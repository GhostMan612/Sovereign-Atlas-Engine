// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import android.content.Context
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

private fun buildEncryptedDataStore(context: Context): DataStore<Preferences> {
    // Full registration (not AeadConfig alone): new-style key templates
    // need the serialization-registry creators TinkConfig provides.
    TinkConfig.register()
    val keysetHandle = AndroidKeysetManager.Builder()
        .withSharedPref(context.applicationContext, "keyset", "api_keys_keyset_prefs")
        .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
        .withMasterKeyUri("android-keystore://master_key")
        .build()
        .keysetHandle

    val aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    return buildApiKeyStore(aead, context.dataStoreFile(API_KEYS_FILE))
}

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
