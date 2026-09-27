// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ChatMessage(
    val messageId: String,
    val senderUid: String,
    val senderCallsign: String,
    val chatroom: String,
    val remarksTo: String,
    val text: String,
    val timestampMillis: Long,
    val isSelf: Boolean,
)

class MessageStore(private val localDeviceUid: String) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun addMessage(msg: ChatMessage) {
        val isBroadcast = msg.remarksTo == "All Chat Rooms" ||
            msg.remarksTo.startsWith("All Chat Rooms.")
        val isToMe = msg.remarksTo == localDeviceUid
        val isFromMe = msg.senderUid == localDeviceUid

        if (isBroadcast || isToMe || isFromMe) {
            _messages.update { current ->
                if (current.any { it.messageId == msg.messageId }) return@update current
                val finalMsg = if (isFromMe) msg.copy(isSelf = true) else msg.copy(isSelf = false)
                (current + finalMsg).takeLast(100)
            }
        }
    }
}
