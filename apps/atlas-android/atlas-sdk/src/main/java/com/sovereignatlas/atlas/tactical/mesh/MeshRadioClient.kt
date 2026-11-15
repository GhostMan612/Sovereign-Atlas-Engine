// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MeshRadioClient {
    val connectionState: StateFlow<RadioConnectionState>
    val isReady: StateFlow<Boolean>
    val discoveredNodes: StateFlow<List<MeshNodeInfo>>
    val inboundPackets: SharedFlow<MeshPacket>
    val activeRadioInfo: StateFlow<RadioHardwareInfo?>
    val deliveryUpdates: SharedFlow<PacketDeliveryUpdate>
    val deliveryStates: StateFlow<Map<Long, PacketDeliveryUpdate>>

    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun transmitPacket(packet: MeshPacket): Boolean
    suspend fun pingNode(targetNodeNum: Long): Boolean
}
