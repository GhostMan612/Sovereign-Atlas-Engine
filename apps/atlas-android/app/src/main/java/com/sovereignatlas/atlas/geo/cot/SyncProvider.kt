// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

typealias SyncOperation = suspend () -> Result<Unit>

interface SyncProvider {
    fun beginAuthentication(): SyncOperation
    fun beginChatMessage(message: ChatMessage): SyncOperation
    val isReady: Boolean
}
