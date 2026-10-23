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

class MockMeshRadioClient(
    initialState: RadioConnectionState = RadioConnectionState.DISCONNECTED,
    initialHardware: RadioHardwareInfo? = RadioHardwareInfo(
        radioModel = "Heltec V3 (Simulated)",
        frequencyBand = "915 MHz US",
        firmwareVersion = "2.3.12",
        nodeNum = 0x1A2B3C4DL,
        isUsb = true,
    ),
) : MeshRadioClient {

    private val _connectionState = MutableStateFlow(initialState)
    override val connectionState: StateFlow<RadioConnectionState> = _connectionState.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<List<MeshNodeInfo>>(emptyList())
    override val discoveredNodes: StateFlow<List<MeshNodeInfo>> = _discoveredNodes.asStateFlow()

    private val _inboundPackets = MutableSharedFlow<MeshPacket>(replay = 1, extraBufferCapacity = 64)
    override val inboundPackets: SharedFlow<MeshPacket> = _inboundPackets.asSharedFlow()

    private val _activeRadioInfo = MutableStateFlow(initialHardware)
    override val activeRadioInfo: StateFlow<RadioHardwareInfo?> = _activeRadioInfo.asStateFlow()

    val transmittedPackets = ArrayList<MeshPacket>()
    var failTransmissions = false

    override suspend fun connect(): Boolean {
        _connectionState.value = RadioConnectionState.CONNECTING
        _connectionState.value = RadioConnectionState.CONNECTED
        return true
    }

    override suspend fun disconnect() {
        _connectionState.value = RadioConnectionState.DISCONNECTED
    }

    override suspend fun transmitPacket(packet: MeshPacket): Boolean {
        if (failTransmissions) return false
        if (_connectionState.value != RadioConnectionState.CONNECTED &&
            _connectionState.value != RadioConnectionState.STREAMING
        ) {
            return false
        }
        transmittedPackets.add(packet)
        return true
    }

    override suspend fun pingNode(targetNodeNum: Long): Boolean {
        return transmitPacket(
            MeshPacket(
                from = _activeRadioInfo.value?.nodeNum ?: 0L,
                to = targetNodeNum,
                portNum = PortNum.PORTNUM_TELEMETRY_APP,
                payload = "PING".toByteArray(Charsets.UTF_8),
                wantAck = true,
            )
        )
    }

    fun simulateInboundPacket(packet: MeshPacket) {
        _inboundPackets.tryEmit(packet)
    }

    fun setNodes(nodes: List<MeshNodeInfo>) {
        _discoveredNodes.value = nodes
    }

    fun addDiscoveredNode(node: MeshNodeInfo) {
        val existing = _discoveredNodes.value.filter { it.nodeNum != node.nodeNum }
        _discoveredNodes.value = existing + node
    }

    fun setConnectionState(state: RadioConnectionState) {
        _connectionState.value = state
    }
}
