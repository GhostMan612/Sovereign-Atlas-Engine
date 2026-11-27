// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.geo.cot.ChatMessage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ChatOutboxStorage {
    fun read(): String?
    fun write(value: String): Boolean
}

class ChatOutbox(private val storage: ChatOutboxStorage) {
    private val lock = Any()
    private val loaded = runCatching { decode(storage.read()) }
    val available: Boolean = loaded.isSuccess
    private val _pending = MutableStateFlow(loaded.getOrDefault(emptyList()))
    val pending: StateFlow<List<ChatMessage>> = _pending.asStateFlow()

    fun retain(message: ChatMessage) = synchronized(lock) {
        val existing = _pending.value.firstOrNull { it.messageId == message.messageId }
        require(existing == null || existing == message)
        if (existing == null) save(_pending.value + message)
    }

    fun confirm(messageId: String) = synchronized(lock) {
        if (_pending.value.any { it.messageId == messageId }) save(_pending.value.filterNot { it.messageId == messageId })
    }

    private fun save(messages: List<ChatMessage>) {
        check(available) { "Chat outbox is unavailable" }
        require(messages.size <= 1000)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(messages.size)
            messages.forEach { msg ->
                out.writeUTF(msg.messageId)
                out.writeUTF(msg.senderUid)
                out.writeUTF(msg.senderCallsign)
                out.writeUTF(msg.chatroom)
                out.writeUTF(msg.remarksTo)
                out.writeUTF(msg.text)
                out.writeLong(msg.timestampMillis)
                out.writeBoolean(msg.isSelf)
            }
        }
        check(storage.write(Base64.getEncoder().encodeToString(bytes.toByteArray()))) { "Chat outbox could not be saved" }
        _pending.value = messages
    }

    private fun decode(value: String?): List<ChatMessage> {
        if (value == null) return emptyList()
        return DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(value))).use { input ->
            require(input.readInt() == 1)
            val count = input.readInt()
            require(count in 0..1000)
            val messages = List(count) {
                ChatMessage(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF(), input.readLong(), input.readBoolean())
            }
            require(input.available() == 0 && messages.map { it.messageId }.toSet().size == count)
            messages
        }
    }
}
