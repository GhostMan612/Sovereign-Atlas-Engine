// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.android.settings.NetworkProfile
import com.sovereignatlas.atlas.android.settings.CloudPolicyCoordinator
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import com.sovereignatlas.atlas.geo.cot.SyncProvider
import com.sovereignatlas.atlas.geo.cot.SyncOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

object OfflineSyncProvider : SyncProvider {
    override val isReady: Boolean = false
    override fun beginAuthentication(): SyncOperation = { Result.failure(IllegalStateException("Cloud unavailable")) }
    override fun beginChatMessage(message: ChatMessage): SyncOperation = { Result.failure(IllegalStateException("Cloud unavailable")) }
}

enum class CloudDeliveryStatus { BLOCKED, CANCELLED_BEFORE_SEND, SUBMITTED, CONFIRMED, FAILED }

data class CloudDelivery(val status: CloudDeliveryStatus, val error: Throwable? = null)

data class ChatSendResult(
    val message: ChatMessage,
    val multicastTransmitted: Boolean,
    val cloudDelivery: CloudDelivery?,
) {
    val cloudResult: Result<Unit>? get() = cloudDelivery?.let {
        if (it.status == CloudDeliveryStatus.CONFIRMED) Result.success(Unit)
        else Result.failure(it.error ?: IllegalStateException("Cloud ${it.status.name.lowercase()}"))
    }
}

internal suspend fun deliverChat(
    profile: NetworkProfile,
    message: ChatMessage,
    policy: CloudPolicyCoordinator,
    provider: () -> SyncProvider,
    authorization: Long? = policy.authorization(profile),
    sendMesh: () -> Boolean,
): ChatSendResult {
    require(profile != NetworkProfile.RADIO_SILENCE) { "Radio Silence (EMCON) is Active" }
    val cloudRequested = profile == NetworkProfile.CLOUD_ONLY || profile == NetworkProfile.HYBRID_BRIDGE
    val token = if (cloudRequested) authorization else null
    val meshSent = if (profile == NetworkProfile.MESH_ONLY || profile == NetworkProfile.HYBRID_BRIDGE) sendMesh() else false
    var submitted = false
    val cloudResult = if (cloudRequested && token == null) CloudDelivery(CloudDeliveryStatus.BLOCKED)
    else if (cloudRequested && token != null) {
        try {
            withTimeoutOrNull(15_000) {
                val started = policy.submit(token) {
                    val cloud = provider()
                    cloud to cloud.beginAuthentication()
                } ?: return@withTimeoutOrNull CloudDelivery(CloudDeliveryStatus.CANCELLED_BEFORE_SEND)
                val auth = started.second()
                if (auth.isFailure) return@withTimeoutOrNull CloudDelivery(CloudDeliveryStatus.FAILED, auth.exceptionOrNull())
                val write = policy.submit(token) {
                    submitted = true
                    started.first.beginChatMessage(message)
                } ?: return@withTimeoutOrNull CloudDelivery(CloudDeliveryStatus.CANCELLED_BEFORE_SEND)
                val result = write()
                if (result.isSuccess) CloudDelivery(CloudDeliveryStatus.CONFIRMED)
                else CloudDelivery(CloudDeliveryStatus.FAILED, result.exceptionOrNull())
            } ?: CloudDelivery(if (submitted) CloudDeliveryStatus.SUBMITTED else CloudDeliveryStatus.CANCELLED_BEFORE_SEND)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CloudDelivery(if (submitted) CloudDeliveryStatus.SUBMITTED else CloudDeliveryStatus.FAILED, error)
        }
    } else null
    return ChatSendResult(message, meshSent, cloudResult)
}
