// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun createMockMsg(sender: String, to: String) = ChatMessage(
    messageId = "test",
    senderUid = sender,
    senderCallsign = "X",
    chatroom = "All Chat Rooms",
    remarksTo = to,
    text = "test",
    timestampMillis = 0L,
    isSelf = false,
)

final class MessageStoreTest {
    @Test
    fun directMessageToOtherUidIsFiltered() {
        val store = MessageStore("A")
        store.addMessage(createMockMsg("C", "B"))
        assertEquals(0, store.messages.value.size)
    }

    @Test
    fun broadcastMessageIsAccepted() {
        val store = MessageStore("A")
        store.addMessage(createMockMsg("C", "All Chat Rooms"))
        assertEquals(1, store.messages.value.size)
    }

    @Test
    fun dmToSelfIsAccepted() {
        val store = MessageStore("A")
        store.addMessage(createMockMsg("C", "A"))
        assertEquals(1, store.messages.value.size)
    }

    @Test
    fun ownMessageIsAcceptedAsSelf() {
        val store = MessageStore("A")
        store.addMessage(createMockMsg("A", "All Chat Rooms"))
        assertEquals(1, store.messages.value.size)
        assertTrue(store.messages.value.first().isSelf)
    }

    @Test
    fun ringBufferDropsOldest() {
        val store = MessageStore("A")
        for (i in 1..101) {
            store.addMessage(createMockMsg("C", "All Chat Rooms").copy(messageId = "msg-$i"))
        }
        assertEquals(100, store.messages.value.size)
        assertEquals("msg-2", store.messages.value.first().messageId)
        assertEquals("msg-101", store.messages.value.last().messageId)
    }

    @Test
    fun duplicateMessageIsIgnored() {
        val store = MessageStore("A")
        val msg = createMockMsg("C", "All Chat Rooms").copy(messageId = "dup-1")
        store.addMessage(msg)
        store.addMessage(msg)
        assertEquals(1, store.messages.value.size)
    }
}
