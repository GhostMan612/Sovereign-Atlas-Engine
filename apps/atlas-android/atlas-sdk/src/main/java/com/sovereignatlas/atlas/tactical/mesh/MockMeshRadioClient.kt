// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class MockMeshRadioClient(
    initialState: RadioConnectionState = RadioConnectionState.DISCONNECTED,
    private val initialHardware: RadioHardwareInfo? = RadioHardwareInfo(
        radioModel = "Mock LoRa Node",
        frequencyBand = "915 MHz US",
        firmwareVersion = "2.8.2-mock",
        nodeNum = 0x11223344L,
        isUsb = true,
    ),
) : MeshRadioClient {

    private val nextPacketId = AtomicLong(1L)

    private val _connectionState = MutableStateFlow(initialState)
    override val connectionState: StateFlow<RadioConnectionState> = _connectionState.asStateFlow()

    private val _isReady = MutableStateFlow(isStateReady(initialState))
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<List<MeshNodeInfo>>(emptyList())
    override val discoveredNodes: StateFlow<List<MeshNodeInfo>> = _discoveredNodes.asStateFlow()

    private val _inboundPackets = MutableSharedFlow<MeshPacket>(replay = 1, extraBufferCapacity = 64)
    override val inboundPackets: SharedFlow<MeshPacket> = _inboundPackets.asSharedFlow()

    private val _activeRadioInfo = MutableStateFlow(if (isStateReady(initialState)) initialHardware else null)
    override val activeRadioInfo: StateFlow<RadioHardwareInfo?> = _activeRadioInfo.asStateFlow()

    private val _deliveryUpdates = MutableSharedFlow<PacketDeliveryUpdate>(replay = 1, extraBufferCapacity = 64)
    override val deliveryUpdates: SharedFlow<PacketDeliveryUpdate> = _deliveryUpdates.asSharedFlow()

    private val _deliveryStates = MutableStateFlow<Map<Long, PacketDeliveryUpdate>>(emptyMap())
    override val deliveryStates: StateFlow<Map<Long, PacketDeliveryUpdate>> = _deliveryStates.asStateFlow()

    val transmittedPackets = ArrayList<MeshPacket>()

    override suspend fun connect(): Boolean {
        _connectionState.value = RadioConnectionState.CONNECTING
        _connectionState.value = RadioConnectionState.HANDSHAKING
        _activeRadioInfo.value = initialHardware
        _connectionState.value = RadioConnectionState.CONNECTED
        _isReady.value = true
        return true
    }

    override suspend fun disconnect() {
        _isReady.value = false
        _activeRadioInfo.value = null
        _connectionState.value = RadioConnectionState.DISCONNECTED
    }

    override suspend fun transmitPacket(packet: MeshPacket): Boolean {
        if (!_isReady.value) return false
        val state = _connectionState.value
        if (state != RadioConnectionState.CONNECTED && state != RadioConnectionState.STREAMING) {
            return false
        }
        val packetId = if (packet.id != 0L) packet.id else nextPacketId.getAndIncrement()
        val resolved = packet.copy(id = packetId)
        transmittedPackets.add(resolved)
        emitDeliveryUpdate(PacketDeliveryUpdate(packetId, PacketDeliveryStage.QUEUED))
        emitDeliveryUpdate(PacketDeliveryUpdate(packetId, PacketDeliveryStage.RADIO_ACCEPTED))
        return true
    }

    override suspend fun pingNode(targetNodeNum: Long): Boolean {
        return transmitPacket(
            MeshPacket(
                from = _activeRadioInfo.value?.nodeNum ?: 0L,
                to = targetNodeNum,
                portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP,
                payload = "PING".encodeToByteArray(),
                id = nextPacketId.getAndIncrement(),
                wantAck = true,
            )
        )
    }

    fun simulateInboundPacket(packet: MeshPacket) {
        if (packet.portNum == PortNum.PORTNUM_ROUTING_APP && packet.requestId != 0L) {
            val error = packet.routingError
            if (error == null || error == 0) {
                emitDeliveryUpdate(PacketDeliveryUpdate(packet.requestId, PacketDeliveryStage.PEER_DELIVERED))
            } else {
                emitDeliveryUpdate(PacketDeliveryUpdate(packet.requestId, PacketDeliveryStage.FAILED, errorCode = error))
            }
        }
        _inboundPackets.tryEmit(packet)
    }

    fun simulateDeliveryUpdate(update: PacketDeliveryUpdate) {
        emitDeliveryUpdate(update)
    }

    fun setNodes(nodes: List<MeshNodeInfo>) {
        _discoveredNodes.value = nodes
    }

    fun addDiscoveredNode(node: MeshNodeInfo) {
        _discoveredNodes.value = _discoveredNodes.value.filterNot { it.nodeNum == node.nodeNum } + node
    }

    fun setConnectionState(state: RadioConnectionState) {
        _connectionState.value = state
        _isReady.value = isStateReady(state)
        if (!_isReady.value) {
            if (state == RadioConnectionState.DISCONNECTED || state == RadioConnectionState.ERROR) {
                _activeRadioInfo.value = null
            }
        } else if (_activeRadioInfo.value == null) {
            _activeRadioInfo.value = initialHardware
        }
    }

    private fun emitDeliveryUpdate(update: PacketDeliveryUpdate) {
        _deliveryStates.value = _deliveryStates.value + (update.packetId to update)
        _deliveryUpdates.tryEmit(update)
    }

    private fun isStateReady(state: RadioConnectionState): Boolean {
        return state == RadioConnectionState.CONNECTED || state == RadioConnectionState.STREAMING
    }
}
