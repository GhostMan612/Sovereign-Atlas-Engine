// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.sovereignatlas.atlas.core.KeyProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal const val API_KEYS_FILE = "api_keys.json"

/** Tink master-key alias. Deleted wholesale when it fails to load. */
internal const val MASTER_KEY_ALIAS = "master_key"
internal const val KEYSET_PREFS_NAME = "api_keys_keyset_prefs"

/**
 * What a self-heal wiped, reported so the caller can log it rather than guess.
 *
 * Every field is a fact about what was destroyed, not a plan. An operator reading
 * this learns that a key they had was discarded; they are not told it was fine.
 */
internal data class KeysetWipeReport(
    val keysetPrefsCleared: Boolean,
    val masterKeyAliasPresent: Boolean,
    val cause: Throwable,
)

/**
 * Where the self-heal reports what it destroyed.
 *
 * An interface rather than a direct `Log.e` because `android.util.Log` is a stub
 * that throws under plain JVM unit tests, and the path that matters most here — the
 * one that destroys a key — must be assertable off-device. Production installs the
 * `Log`-backed implementation below.
 */
internal interface KeysetHealReporter {
    fun onError(message: String, error: Throwable?)
}

private object LogKeysetHealReporter : KeysetHealReporter {
    override fun onError(message: String, error: Throwable?) {
        Log.e("AndroidKeyProvider", message, error)
    }
}

/** Swappable so tests can observe. Not touched from outside this file. */
internal var keysetHealReporter: KeysetHealReporter = LogKeysetHealReporter

// TOP LEVEL SINGLETON FACTORY to ensure only one DataStore instance exists
// per file, preventing IllegalStateException from duplicate delegates.
@Volatile
private var dataStoreInstance: DataStore<Preferences>? = null
private val lock = Any()

private fun getEncryptedDataStore(context: Context): DataStore<Preferences> {
    return dataStoreInstance ?: synchronized(lock) {
        dataStoreInstance ?: buildEncryptedDataStore(context).also { dataStoreInstance = it }
    }
}

/**
 * Loads the master key, wiping and regenerating it if the Keystore cannot read it.
 *
 * WHY THIS EXISTS. `AndroidKeysetManager.Builder().build()` throws
 * `InvalidKeyException: Keystore cannot load the key with ID: master_key` when the
 * alias exists but is unusable — after a restore, a signature change, or a partial
 * wipe. It used to propagate straight out of `AtlasApplication.onCreate`, which is
 * an unbootable app: no activity, no recovery, no UI to clear anything from.
 *
 * WHY THE WIPE IS DESTRUCTIVE, AND WHY THAT IS THE POINT. Regenerating the master
 * key makes previously-encrypted data permanently undecryptable. That is not a
 * side effect to avoid; it is the only way out of an unusable key, and pretending
 * otherwise leaves the app permanently bricked. What this protects is the
 * *silence*: the wipe is reported, and the caller logs it loudly.
 *
 * WHAT IS LOST HERE. Only [KEYSET_PREFS_NAME] and the [MASTER_KEY_ALIAS] entry —
 * the wrapping keyset and its own key. NOT the encrypted DataStore file: it is
 * left on disk, where `ReplaceFileCorruptionHandler` already recovers it to empty
 * preferences. Deleting user data to fix a boot loop is a different decision, and
 * it belongs to the operator, not to a constructor.
 *
 * If the rebuild ALSO fails, the original throwable is rethrown. A second failure
 * means the device keystore itself is broken, and swallowing that would leave the
 * app limping on with no encryption at all.
 */
internal fun buildEncryptedDataStore(context: Context): DataStore<Preferences> =
    buildEncryptedDataStore(context) { appContext ->
        AndroidKeysetManager.Builder()
            .withSharedPref(appContext, "keyset", KEYSET_PREFS_NAME)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri("android-keystore://$MASTER_KEY_ALIAS")
            .build()
            .keysetHandle
    }

/**
 * Seam for testing. [keysetBuilder] receives the application context and performs
 * the Tink keystore access that fails; injecting it lets a JVM test throw the real
 * exception type and assert the wipe.
 */
internal fun buildEncryptedDataStore(
    context: Context,
    keysetBuilder: (Context) -> KeysetHandle,
): DataStore<Preferences> {
    // Full registration (not AeadConfig alone): new-style key templates need the
    // serialization-registry creators TinkConfig provides.
    TinkConfig.register()
    val appContext = context.applicationContext
    val handle = keysetHandleOrSelfHeal(appContext, { keysetBuilder(appContext) })
    val aead = handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    return buildApiKeyStore(aead, context.dataStoreFile(API_KEYS_FILE))
}

/**
 * Runs [keysetBuilder], and on failure wipes the keyset and tries exactly once more.
 *
 * Only one retry. A loop here would spin on a genuinely broken device keystore,
 * deleting and recreating the alias repeatedly on every launch while never
 * succeeding.
 */
internal fun keysetHandleOrSelfHeal(
    appContext: Context,
    keysetBuilder: () -> KeysetHandle,
    onWipe: (KeysetWipeReport) -> Unit = {},
): KeysetHandle = keysetHandleOrSelfHeal(
    builder = keysetBuilder,
    wipe = { failure ->
        val aliasPresent = runCatching {
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.containsAlias(MASTER_KEY_ALIAS)
        }.getOrDefault(false)

        val prefsCleared = runCatching {
            appContext.getSharedPreferences(KEYSET_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }.getOrDefault(false)

        runCatching {
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(MASTER_KEY_ALIAS)
        }

        onWipe(KeysetWipeReport(prefsCleared, aliasPresent, failure))
    },
)

/**
 * The self-heal decision, with the Android Keystore touch isolated in [wipe].
 *
 * Split out so a JVM test can drive the control flow — fail, wipe, retry, and give
 * up — without a device keystore. [keysetHandleOrSelfHeal] supplies the real
 * wipe; tests supply a recorder.
 */
internal fun keysetHandleOrSelfHeal(
    builder: () -> KeysetHandle,
    wipe: (Throwable) -> Unit,
): KeysetHandle = try {
    builder()
} catch (failure: Exception) {
    wipe(failure)

    keysetHealReporter.onError(
        "Master key '$MASTER_KEY_ALIAS' unusable (${failure.javaClass.simpleName}: " +
            "${failure.message}). Destroyed the wrapping keyset; any data encrypted " +
            "under the old key is now unrecoverable and the user must re-enter it.",
        failure,
    )

    try {
        builder()
    } catch (second: Exception) {
        keysetHealReporter.onError(
            "Keyset rebuild failed after wipe. The device keystore is unusable; not " +
                "continuing without encryption.",
            second,
        )
        throw second
    }
}

/**
 * Test shim: reports the wipe instead of touching a device keystore.
 *
 * [onWipe] receives the real failure, so a test can assert the wipe ran AND that it
 * was caused by the exception it injected rather than by something incidental.
 */
internal fun keysetHandleOrSelfHealForTest(
    builder: () -> KeysetHandle,
    onWipe: (KeysetWipeReport) -> Unit,
): KeysetHandle = keysetHandleOrSelfHeal(
    builder = builder,
    wipe = { failure ->
        onWipe(KeysetWipeReport(keysetPrefsCleared = true, masterKeyAliasPresent = true, cause = failure))
    },
)

internal fun buildApiKeyStore(
    aead: Aead,
    file: File,
    storeScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> {
    val serializer = AeadSerializer(
        aead = aead,
        wrappedSerializer = PreferencesFileSerializer,
        associatedData = API_KEYS_FILE.encodeToByteArray(),
    )
    return DataStoreFactory.create(
        serializer = serializer,
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = storeScope,
        produceFile = { file },
    )
}

// Test-only hook: drops the singleton so tests can rebuild over scratch
// files with a JVM (non-Keystore) Aead. Never call from production code.
internal fun resetEncryptedDataStoreForTests() {
    synchronized(lock) {
        dataStoreInstance = null
    }
}

class AndroidKeyProvider internal constructor(
    private val dataStore: DataStore<Preferences>,
    sharingScope: CoroutineScope,
) : KeyProvider {
    constructor(context: Context) : this(
        getEncryptedDataStore(context),
        CoroutineScope(Dispatchers.IO + SupervisorJob()),
    )

    private val cartoKeyPref = stringPreferencesKey("carto_key")

    override val cartoKey: StateFlow<String?> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[cartoKeyPref] }
        .stateIn(
            scope = sharingScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    override suspend fun setCartoKey(key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "CARTO key must not be empty." }
        dataStore.edit { it[cartoKeyPref] = trimmed }
    }

    override suspend fun clearCartoKey() {
        dataStore.edit { it.remove(cartoKeyPref) }
    }
}
