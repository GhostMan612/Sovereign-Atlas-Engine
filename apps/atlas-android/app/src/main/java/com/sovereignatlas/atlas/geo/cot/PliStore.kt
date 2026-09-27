// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CotPli(
    val uid: String,
    val type: String,
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val altitude: Double? = null,
)

interface CotParser {
    fun parse(packetData: ByteArray): ParsedCot?
}

sealed interface ParsedCot {
    data class Pli(val pli: CotPli) : ParsedCot
    data class Chat(val message: ChatMessage) : ParsedCot
    data class Marker(val marker: CotMarker) : ParsedCot
}

class PliStore(private val localDeviceUid: String, private val ttlMillis: Long = 15 * 60 * 1000L) {
    private val _activePlis = MutableStateFlow<Map<String, CotPli>>(emptyMap())
    val activePlis: StateFlow<Map<String, CotPli>> = _activePlis.asStateFlow()

    fun update(pli: CotPli) {
        if (pli.uid == localDeviceUid) return
        val now = System.currentTimeMillis()
        _activePlis.value = _activePlis.value
            .filterValues { now - it.timestamp < ttlMillis }
            .plus(pli.uid to pli)
    }

    fun pruneExpired() {
        val now = System.currentTimeMillis()
        val current = _activePlis.value
        val fresh = current.filterValues { now - it.timestamp < ttlMillis }
        if (fresh.size != current.size) _activePlis.value = fresh
    }
}
