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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChatDeliveryTest {
    private val message = ChatMessage("id", "uid", "sender", "room", "room", "hello", 1L, true)

    private suspend fun deliverChat(
        profile: NetworkProfile,
        message: ChatMessage,
        policy: CloudPolicyCoordinator,
        provider: () -> SyncProvider,
        authorization: Long? = policy.authorization(profile),
        sendMesh: () -> Boolean,
    ): ChatSendResult {
        val outbox = ChatOutbox(object : ChatOutboxStorage {
            override fun read(): String? = null
            override fun write(value: String) = true
        })
        return com.sovereignatlas.atlas.android.comms.deliverChat(profile, message, policy, provider, authorization, outbox, sendMesh)
    }

    private class FakeCloud : SyncProvider {
        val auth = CompletableDeferred<Result<Unit>>()
        val write = CompletableDeferred<Result<Unit>>()
        var authStarts = 0
        var writeStarts = 0
        override val isReady get() = auth.isCompleted
        override fun beginAuthentication(): SyncOperation {
            authStarts++
            return { auth.await() }
        }
        override fun beginChatMessage(message: ChatMessage): SyncOperation {
            writeStarts++
            return { write.await() }
        }
    }

    @Test fun offlineDoesNotConstructCloud() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.MESH_ONLY)
        val result = deliverChat(NetworkProfile.MESH_ONLY, message, policy, { error("cloud constructed") }) { true }
        assertTrue(result.multicastTransmitted)
        assertNull(result.cloudDelivery)
    }

    @Test fun unavailableCloudFailsHonestly() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val result = deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { OfflineSyncProvider }) { error("mesh called") }
        assertEquals(CloudDeliveryStatus.FAILED, result.cloudDelivery!!.status)
        assertTrue(result.cloudResult!!.isFailure)
    }

    @Test fun hybridKeepsMulticastWhenCloudFails() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.HYBRID_BRIDGE)
        val result = deliverChat(NetworkProfile.HYBRID_BRIDGE, message, policy, { OfflineSyncProvider }) { true }
        assertTrue(result.multicastTransmitted)
        assertEquals(CloudDeliveryStatus.FAILED, result.cloudDelivery!!.status)
    }

    @Test fun transitionDuringAuthenticationCancelsBeforeWrite() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.HYBRID_BRIDGE)
        val cloud = FakeCloud()
        val send = async { deliverChat(NetworkProfile.HYBRID_BRIDGE, message, policy, { cloud }) { true } }
        runCurrent()
        assertEquals(1, cloud.authStarts)
        policy.transition(NetworkProfile.RADIO_SILENCE)
        cloud.auth.complete(Result.success(Unit))
        assertEquals(CloudDeliveryStatus.CANCELLED_BEFORE_SEND, send.await().cloudDelivery!!.status)
        assertEquals(0, cloud.writeStarts)
    }

    @Test fun pendingSendsAllLoseAuthorizationOnSilence() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val cloud = FakeCloud()
        val sends = List(4) { async { deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { cloud }) { false } } }
        runCurrent()
        assertEquals(4, cloud.authStarts)
        policy.transition(NetworkProfile.RADIO_SILENCE)
        cloud.auth.complete(Result.success(Unit))
        sends.forEach { assertEquals(CloudDeliveryStatus.CANCELLED_BEFORE_SEND, it.await().cloudDelivery!!.status) }
        assertEquals(0, cloud.writeStarts)
    }

    @Test fun queuedSendsCannotStartAfterSilence() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val cloud = FakeCloud()
        val sends = List(3) { async { deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { cloud }) { false } } }
        policy.transition(NetworkProfile.RADIO_SILENCE)
        sends.forEach { assertEquals(CloudDeliveryStatus.BLOCKED, it.await().cloudDelivery!!.status) }
        assertEquals(0, cloud.authStarts)
    }

    @Test fun transitionImmediatelyBeforeWritePreventsSubmission() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        var writes = 0
        val cloud = object : SyncProvider {
            override val isReady = true
            override fun beginAuthentication(): SyncOperation = {
                policy.transition(NetworkProfile.RADIO_SILENCE)
                Result.success(Unit)
            }
            override fun beginChatMessage(message: ChatMessage): SyncOperation {
                writes++
                return { Result.success(Unit) }
            }
        }
        val result = deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { cloud }) { false }
        assertEquals(CloudDeliveryStatus.CANCELLED_BEFORE_SEND, result.cloudDelivery!!.status)
        assertEquals(0, writes)
    }

    @Test fun generationCannotReviveAfterRepeatedTransitions() {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val old = policy.authorization(NetworkProfile.CLOUD_ONLY)!!
        policy.transition(NetworkProfile.RADIO_SILENCE)
        policy.transition(NetworkProfile.CLOUD_ONLY)
        policy.transition(NetworkProfile.MESH_ONLY)
        policy.transition(NetworkProfile.CLOUD_ONLY)
        assertNull(policy.submit(old) { "submitted" })
        assertEquals("new", policy.submit(policy.authorization(NetworkProfile.CLOUD_ONLY)!!) { "new" })
    }

    @Test fun capturedRequestCannotReviveAfterSilenceAndReenable() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val captured = policy.snapshot()
        policy.transition(NetworkProfile.RADIO_SILENCE)
        policy.transition(NetworkProfile.CLOUD_ONLY)
        val result = deliverChat(captured.profile, message, policy, { error("stale request initiated authentication") }, captured.authorization) { false }
        assertEquals(CloudDeliveryStatus.CANCELLED_BEFORE_SEND, result.cloudDelivery!!.status)
    }

    @Test fun transitionIsSerializedWithSubmission() {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val token = policy.authorization(NetworkProfile.CLOUD_ONLY)!!
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val transitionStarted = CountDownLatch(1)
        val transitionDone = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val submission = executor.submit {
                policy.submit(token) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    "submitted"
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val transition = executor.submit {
                transitionStarted.countDown()
                policy.transition(NetworkProfile.RADIO_SILENCE)
                transitionDone.countDown()
            }
            assertTrue(transitionStarted.await(5, TimeUnit.SECONDS))
            assertEquals(1L, transitionDone.count)
            release.countDown()
            submission.get(5, TimeUnit.SECONDS)
            transition.get(5, TimeUnit.SECONDS)
            assertNull(policy.submit(token) { "late" })
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun staleProfileCannotStartAuthentication() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.RADIO_SILENCE)
        val result = deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { error("started") }) { false }
        assertEquals(CloudDeliveryStatus.BLOCKED, result.cloudDelivery!!.status)
    }

    @Test fun staleMeshPermitCannotSendAfterSilenceAndReenable() {
        val policy = CloudPolicyCoordinator(NetworkProfile.HYBRID_BRIDGE)
        val permit = policy.meshPermit(policy.snapshot())
        policy.transition(NetworkProfile.RADIO_SILENCE)
        policy.transition(NetworkProfile.HYBRID_BRIDGE)
        var calls = 0
        assertNull(permit.submit { calls++; true })
        assertEquals(0, calls)
    }

    @Test fun writeInitiationExceptionIsUnconfirmed() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val cloud = object : SyncProvider {
            override val isReady = true
            override fun beginAuthentication(): SyncOperation = { Result.success(Unit) }
            override fun beginChatMessage(message: ChatMessage): SyncOperation = error("before write")
        }
        val result = deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { cloud }) { false }
        assertEquals(CloudDeliveryStatus.UNCONFIRMED, result.cloudDelivery!!.status)
    }

    @Test fun retryUsesTheSameMessageIdentity() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val writes = mutableListOf<ChatMessage>()
        val cloud = object : SyncProvider {
            override val isReady = true
            override fun beginAuthentication(): SyncOperation = { Result.success(Unit) }
            override fun beginChatMessage(message: ChatMessage): SyncOperation {
                writes.add(message)
                return { Result.failure(IllegalStateException("unconfirmed")) }
            }
        }
        val first = deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { cloud }) { false }
        assertEquals(CloudDeliveryStatus.SUBMITTED, first.cloudDelivery!!.status)
        deliverChat(NetworkProfile.CLOUD_ONLY, first.message, policy, { cloud }) { false }
        assertEquals(listOf(message, message), writes)
    }

    @Test fun alreadySubmittedWriteRemainsSubmittedOnTimeout() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val cloud = FakeCloud()
        cloud.auth.complete(Result.success(Unit))
        val send = async { deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { cloud }) { false } }
        runCurrent()
        assertEquals(1, cloud.writeStarts)
        policy.transition(NetworkProfile.RADIO_SILENCE)
        assertEquals(CloudDeliveryStatus.SUBMITTED, send.await().cloudDelivery!!.status)
    }

    @Test fun confirmationAfterTransitionIsNotReportedCancelled() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val cloud = FakeCloud()
        cloud.auth.complete(Result.success(Unit))
        val send = async { deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { cloud }) { false } }
        runCurrent()
        policy.transition(NetworkProfile.RADIO_SILENCE)
        cloud.write.complete(Result.success(Unit))
        assertEquals(CloudDeliveryStatus.CONFIRMED, send.await().cloudDelivery!!.status)
    }

    @Test fun failureAfterSubmissionIsReportedAsFailureRatherThanCancellation() = runTest {
        val policy = CloudPolicyCoordinator(NetworkProfile.CLOUD_ONLY)
        val cloud = FakeCloud()
        cloud.auth.complete(Result.success(Unit))
        val send = async { deliverChat(NetworkProfile.CLOUD_ONLY, message, policy, { cloud }) { false } }
        runCurrent()
        policy.transition(NetworkProfile.RADIO_SILENCE)
        cloud.write.complete(Result.failure(IllegalStateException("write failed")))
        val result = send.await().cloudDelivery!!
        assertEquals(CloudDeliveryStatus.SUBMITTED, result.status)
        assertEquals("write failed", result.error!!.message)
    }
}
