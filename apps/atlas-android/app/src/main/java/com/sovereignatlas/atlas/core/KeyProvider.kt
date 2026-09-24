// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import kotlinx.coroutines.flow.StateFlow

interface KeyProvider {
    val cartoKey: StateFlow<String?>
    suspend fun setCartoKey(key: String)
    suspend fun clearCartoKey()
}
