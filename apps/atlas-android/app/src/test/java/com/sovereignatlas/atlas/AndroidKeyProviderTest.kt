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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

final class AndroidKeyProviderTest {
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
        withTimeout(10_000L) {
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
            first.scope.cancel()
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
