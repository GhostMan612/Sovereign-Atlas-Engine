// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class EncryptedChatOutboxStorage(
    private val readCiphertext: () -> String?,
    private val writeCiphertext: (String) -> Boolean,
    private val key: (Boolean) -> SecretKey,
) : ChatOutboxStorage {
    override fun read(): String? {
        val encoded = readCiphertext() ?: return null
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size >= 29 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(true), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(AAD)
        return String(cipher.doFinal(bytes, 13, bytes.size - 13), Charsets.UTF_8)
    }

    override fun write(value: String): Boolean {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(readCiphertext() != null))
        cipher.updateAAD(AAD)
        val nonce = cipher.iv
        require(nonce.size == 12)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return writeCiphertext(Base64.getEncoder().encodeToString(byteArrayOf(1) + nonce + encrypted))
    }

    companion object {
        private val AAD = "sovereign-atlas-chat-outbox-v1".toByteArray(Charsets.UTF_8)
    }
}

internal object AndroidChatOutbox {
    private const val PREFS = "atlas_chat_outbox"
    private const val ALIAS = "atlas_chat_outbox_v1"
    @Volatile private var instance: ChatOutbox? = null

    fun get(context: Context): ChatOutbox = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(context: Context): ChatOutbox {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storage = EncryptedChatOutboxStorage(
            readCiphertext = { prefs.getString("data", null) },
            writeCiphertext = { prefs.edit().putString("data", it).commit() },
            key = { existing ->
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val current = store.getKey(ALIAS, null) as? SecretKey
                if (current != null) current else {
                    check(!existing) { "Chat outbox encryption key is unavailable" }
                    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    generator.init(
                        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                    generator.generateKey()
                }
            },
        )
        return ChatOutbox(storage)
    }
}
