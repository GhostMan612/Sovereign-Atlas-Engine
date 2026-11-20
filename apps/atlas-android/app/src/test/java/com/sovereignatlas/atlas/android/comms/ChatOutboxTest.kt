// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.android.settings.CloudPolicyCoordinator
import com.sovereignatlas.atlas.android.settings.NetworkProfile
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import com.sovereignatlas.atlas.geo.cot.SyncOperation
import com.sovereignatlas.atlas.geo.cot.SyncProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator

class ChatOutboxTest {
    private val message = ChatMessage("id", "uid", "sender", "room", "room", "hello", 1L, true)

    private class Storage : ChatOutboxStorage {
        var value: String? = null
        var writable = true
        override fun read() = value
        override fun write(value: String): Boolean {
            if (!writable) return false
            this.value = value
            return true
        }
    }

    @Test fun cancellationAndRecreationRetainIdentityUntilConfirmed() = runTest {
        val storage = Storage()
        val policy = CloudPolicyCoordinator(NetworkProfile.HYBRID_BRIDGE)
        val writes = mutableListOf<ChatMessage>()
        val deferred = CompletableDeferred<Result<Unit>>()
        val provider = object : SyncProvider {
            override val isReady = true
            override fun beginAuthentication(): SyncOperation = { Result.success(Unit) }
            override fun beginChatMessage(message: ChatMessage): SyncOperation {
                writes.add(message)
                return { deferred.await() }
            }
        }
        val first = async { deliverChat(NetworkProfile.HYBRID_BRIDGE, message, policy, { provider }, policy.snapshot().authorization, ChatOutbox(storage)) { true } }
        runCurrent()
        assertEquals(listOf(message), writes)
        first.cancelAndJoin()
        val restored = ChatOutbox(storage)
        assertEquals(listOf(message), restored.pending.value)
        deferred.complete(Result.success(Unit))
        val result = deliverChat(NetworkProfile.HYBRID_BRIDGE, restored.pending.value.single(), policy, { provider }, policy.snapshot().authorization, restored) { false }
        assertEquals(CloudDeliveryStatus.CONFIRMED, result.cloudDelivery!!.status)
        assertEquals(listOf(message, message), writes)
        assertEquals(listOf(message), ChatOutbox(storage).pending.value)
        restored.confirm(message.messageId)
        assertTrue(ChatOutbox(storage).pending.value.isEmpty())
    }

    @Test fun failedPersistencePreventsWriteInitiation() = runTest {
        val storage = Storage().apply { writable = false }
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        var writes = 0
        val provider = object : SyncProvider {
            override val isReady = true
            override fun beginAuthentication(): SyncOperation = { Result.success(Unit) }
            override fun beginChatMessage(message: ChatMessage): SyncOperation {
                writes++
                return { Result.success(Unit) }
            }
        }
        val result = deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { provider }, policy.snapshot().authorization, ChatOutbox(storage)) { false }
        assertEquals(CloudDeliveryStatus.FAILED, result.cloudDelivery!!.status)
        assertEquals(0, writes)
    }

    @Test fun corruptOutboxIsNotOverwritten() {
        val storage = Storage().apply { value = "corrupt" }
        val outbox = ChatOutbox(storage)
        assertFalse(outbox.available)
        assertTrue(runCatching { outbox.retain(message) }.isFailure)
        assertEquals("corrupt", storage.value)
    }

    @Test fun encryptedStorageSurvivesRecreationAndRejectsTampering() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val disk = Storage()
        fun encrypted() = EncryptedChatOutboxStorage({ disk.value }, { disk.write(it) }, { key })
        ChatOutbox(encrypted()).retain(message)
        assertFalse(disk.value!!.contains(message.text))
        assertEquals(listOf(message), ChatOutbox(encrypted()).pending.value)
        val bytes = Base64.getDecoder().decode(disk.value)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        val tampered = Base64.getEncoder().encodeToString(bytes)
        disk.value = tampered
        val restored = ChatOutbox(encrypted())
        assertFalse(restored.available)
        assertTrue(runCatching { restored.retain(message) }.isFailure)
        assertEquals(tampered, disk.value)
    }

    @Test fun missingEncryptionKeyCannotReplaceExistingOutbox() {
        val disk = Storage().apply { value = Base64.getEncoder().encodeToString(ByteArray(29).apply { this[0] = 1 }) }
        val storage = EncryptedChatOutboxStorage({ disk.value }, { disk.write(it) }, { error("key unavailable") })
        val restored = ChatOutbox(storage)
        assertFalse(restored.available)
        val original = disk.value
        assertTrue(runCatching { restored.retain(message) }.isFailure)
        assertEquals(original, disk.value)
    }
}
