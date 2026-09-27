// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

/**
 * Pure-logic contract for pushing durable tactical artifacts to a remote sync backend.
 * Implementations live in the android layer (e.g., FirestoreSyncProvider).
 * Live PLI is intentionally NOT part of this contract — it stays on the mesh.
 */
interface SyncProvider {
    /** Authenticate (or anonymously provision) the operator. Idempotent. */
    suspend fun ensureAuthenticated(): Result<Unit>

    /** Push a chat message to the durable log. Best-effort; does not throw on transient failure. */
    suspend fun pushChatMessage(message: ChatMessage): Result<Unit>

    /** True if the last auth attempt succeeded and the client is ready to write. */
    val isReady: Boolean
}
