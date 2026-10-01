// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.config.TinkConfig
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

final class AndroidKeyProviderTest {
    // ---- self-heal ------------------------------------------------------------
    // The wipe is destructive and therefore must be provable, not assumed. These
    // drive the real recovery path by injecting a builder that throws the exact
    // exception the Android Keystore raises on an unusable alias.

    /** The exception `AndroidKeysetManager` raises for an unloadable alias. */
    private class FakeKeyStoreFailure(message: String) : java.security.InvalidKeyException(message)

    /**
     * `android.util.Log` is a stub that throws under plain JVM unit tests, and the
     * self-heal logs on both paths. Routing through an overridable reporter keeps
     * the control flow provable here and still reaches `Log.e` in production.
     */
    private object RecordingReporter : KeysetHealReporter {
        // Cleared on install, not on read: tests run in one JVM and a leftover
        // error from an earlier case would make an unrelated assertion pass.
        val errors = mutableListOf<String>()
        override fun onError(message: String, error: Throwable?) {
            errors += message
        }

        fun install() {
            errors.clear()
            keysetHealReporter = this
        }
    }

    private fun <T> withReporter(body: () -> T): T {
        // KeysetHandle.generateNew needs the registry that TinkConfig installs; the
        // older tests get this inside testAead(), and these build their own handles.
        TinkConfig.register()
        val previous = keysetHealReporter
        RecordingReporter.install()
        try {
            return body()
        } finally {
            keysetHealReporter = previous
        }
    }

    /**
     * Runs the real self-heal against a builder that fails [failures] times, then
     * succeeds, and returns the keyset it produced.
     *
     * The Keystore calls inside the wipe are wrapped in runCatching, so on a JVM
     * with no AndroidKeyStore they degrade to "alias absent" rather than throwing.
     * That is the behaviour under test here: the WIPE must run and the rebuild must
     * be attempted exactly once.
     */
    private fun selfHealsAfter(
        failures: Int,
        onWipe: () -> Unit = {},
    ): KeysetHandle = withReporter {
        var attempts = 0
        val handle = KeysetHandle.generateNew(
            KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM),
        )
        val result = keysetHandleOrSelfHealForTest(
            builder = {
                attempts += 1
                if (attempts <= failures) throw FakeKeyStoreFailure("cannot load master_key")
                handle
            },
            onWipe = { onWipe() },
        )
        assertEquals("builder must be retried exactly once after a wipe", failures + 1, attempts)
        assertTrue(
            "the destructive wipe must be reported, not silent",
            RecordingReporter.errors.any { it.contains(MASTER_KEY_ALIAS) },
        )
        result
    }

    @Test
    fun anUnloadableMasterKeyIsWipedAndRegenerated() {
        var wiped = false
        val handle = selfHealsAfter(failures = 1) { wiped = true }

        assertTrue("the wipe must run", wiped)
        assertNotNull("a fresh keyset must be returned", handle)
    }

    @Test
    fun aHealthyKeyIsUsedWithoutAnyWipe() = withReporter {
        var wiped = false
        var attempts = 0
        val handle = KeysetHandle.generateNew(
            KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM),
        )

        val result = keysetHandleOrSelfHealForTest(
            builder = { attempts += 1; handle },
            onWipe = { wiped = true },
        )

        assertEquals("a healthy key must not be retried", 1, attempts)
        assertFalse("a healthy key must never trigger a destructive wipe", wiped)
        assertTrue("a healthy key must log nothing", RecordingReporter.errors.isEmpty())
        assertEquals(handle, result)
    }

    @Test
    fun aKeystoreThatFailsTwiceIsNotSwallowed() = withReporter {
        // A second failure means the device keystore itself is broken. Continuing
        // silently would leave the app running with no encryption at all, which is
        // worse than failing to start.
        var attempts = 0

        val thrown = try {
            keysetHandleOrSelfHealForTest(
                builder = { attempts += 1; throw FakeKeyStoreFailure("always broken") },
                onWipe = {},
            )
            null
        } catch (error: Exception) {
            error
        }

        assertNotNull("a double failure must propagate", thrown)
        assertTrue(thrown is FakeKeyStoreFailure)
        // One original attempt plus exactly one rebuild; no retry loop.
        assertEquals("must not retry indefinitely", 2, attempts)
        assertTrue(
            "giving up must be reported too",
            RecordingReporter.errors.any { it.contains("rebuild failed") },
        )
    }

    @Test
    fun selfHealDoesNotDeleteTheEncryptedDataStoreFile() = withReporter {
        // The wipe destroys the KEY, not the user's file. The corruption handler
        // already recovers a now-undecryptable file to empty preferences, and
        // deleting user data to fix a boot loop is the operator's call, not a
        // constructor's.
        val root = Files.createTempDirectory("atlas-wipe").toFile()
        val file = File(File(root, "datastore"), "api_keys.json")
        file.parentFile?.mkdirs()
        file.writeText("pretend this is ciphertext")

        // Fails on the original attempt and again on the rebuild, so the throw is
        // expected; what matters is that the encrypted file survived the wipe.
        runCatching {
            keysetHandleOrSelfHealForTest(
                builder = { throw FakeKeyStoreFailure("bad key") },
                onWipe = {},
            )
        }

        assertTrue("the wipe must not touch the encrypted data file", file.exists())
        assertEquals(
            "the wipe must not rewrite or truncate the data file",
            "pretend this is ciphertext",
            file.readText(),
        )
    }

    private fun testAead(): Aead {
        TinkConfig.register()
        val handle = KeysetHandle.generateNew(
            KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM),
        )
        return handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun storeFile(root: File): File {
        return File(File(root, "datastore"), "api_keys.json")
    }

    private data class Harness(
        val provider: AndroidKeyProvider,
        val scope: CoroutineScope,
    )

    private fun harnessOver(root: File, aead: Aead): Harness {
        // One scope owns both the store and the sharing collector; cancelling
        // it releases the file so a later instance may open it (singleton law).
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val provider = AndroidKeyProvider(buildApiKeyStore(aead, storeFile(root), scope), scope)
        return Harness(provider, scope)
    }

    private suspend fun awaitValue(provider: AndroidKeyProvider, expected: String?) {
        withTimeout(60_000L) {
            provider.cartoKey.first { it == expected }
        }
    }

    @Test
    fun roundTripSetAndClear() {
        val harness = harnessOver(Files.createTempDirectory("atlas-keys").toFile(), testAead())
        try {
            runBlocking {
                val provider = harness.provider
                provider.setCartoKey("test-key")
                awaitValue(provider, "test-key")
                assertEquals("test-key", provider.cartoKey.value)
                provider.clearCartoKey()
                awaitValue(provider, null)
                assertNull(provider.cartoKey.value)
            }
        } finally {
            harness.scope.cancel()
        }
    }

    @Test
    fun keySurvivesNewInstance() {
        val root = Files.createTempDirectory("atlas-keys").toFile()
        val aead = testAead()
        val first = harnessOver(root, aead)
        try {
            runBlocking {
                first.provider.setCartoKey("persist-key")
                awaitValue(first.provider, "persist-key")
            }
        } finally {
            // cancel() is only a request; the DataStore actor and the stateIn
            // collector may still be touching the file when the next instance
            // boots. Joining the scope's Job makes teardown actually complete
            // before the second harness is constructed.
            first.scope.cancel()
            runBlocking { first.scope.coroutineContext[Job]?.join() }
        }
        resetEncryptedDataStoreForTests()
        val second = harnessOver(root, aead)
        try {
            runBlocking {
                awaitValue(second.provider, "persist-key")
                assertEquals("persist-key", second.provider.cartoKey.value)
            }
        } finally {
            second.scope.cancel()
        }
    }

    @Test
    fun storedFileDoesNotContainPlaintext() {
        val root = Files.createTempDirectory("atlas-keys").toFile()
        val harness = harnessOver(root, testAead())
        try {
            runBlocking {
                val provider = harness.provider
                val secret = "carto-secret-xyz-123"
                provider.setCartoKey(secret)
                awaitValue(provider, secret)
                val raw = storeFile(root).readBytes().toString(Charsets.ISO_8859_1)
                assertFalse(raw.contains(secret))
            }
        } finally {
            harness.scope.cancel()
        }
    }

    @Test
    fun tamperedFileRecoversToEmpty() {
        val root = Files.createTempDirectory("atlas-keys").toFile()
        val aead = testAead()
        val harness = harnessOver(root, aead)
        try {
            runBlocking {
                harness.provider.setCartoKey("tamper-key")
                awaitValue(harness.provider, "tamper-key")
            }
        } finally {
            harness.scope.cancel()
        }
        storeFile(root).writeBytes(byteArrayOf(7, 7, 7, 7, 7, 7, 7, 7))
        resetEncryptedDataStoreForTests()
        val recovered = harnessOver(root, aead)
        try {
            runBlocking {
                awaitValue(recovered.provider, null)
                assertNull(recovered.provider.cartoKey.value)
            }
        } finally {
            recovered.scope.cancel()
        }
    }
}
