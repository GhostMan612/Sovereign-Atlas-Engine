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
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val acknowledgementTimeoutMs: Long = 120_000L,
) : MeshRadioClient {

    private val nextPacketId = AtomicLong(1L)
    private val lock = Any()
    private data class PendingDelivery(val packet: MeshPacket, val queuedAt: Long)
    private val pending = LinkedHashMap<Long, PendingDelivery>()

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
        synchronized(lock) { finishPending("Transport disconnected") }
        _isReady.value = false
        _activeRadioInfo.value = null
        _connectionState.value = RadioConnectionState.DISCONNECTED
    }

    override suspend fun transmitPacket(packet: MeshPacket): Boolean = transmitNow(packet)

    override suspend fun transmitPacket(packet: MeshPacket, permit: TransmissionPermit): Boolean =
        permit.submit { transmitNow(packet) } ?: false

    override suspend fun transmitPacketOutcome(packet: MeshPacket, permit: TransmissionPermit): TransmissionOutcome =
        if (transmitPacket(packet, permit)) TransmissionOutcome.SUBMITTED else TransmissionOutcome.NOT_SUBMITTED

    override suspend fun pingNodeOutcome(targetNodeNum: Long, permit: TransmissionPermit): TransmissionOutcome =
        if (pingNode(targetNodeNum, permit)) TransmissionOutcome.SUBMITTED else TransmissionOutcome.NOT_SUBMITTED

    private fun transmitNow(packet: MeshPacket): Boolean = synchronized(lock) {
        if (!_isReady.value) return@synchronized false
        val state = _connectionState.value
        if (state != RadioConnectionState.CONNECTED && state != RadioConnectionState.STREAMING) {
            return@synchronized false
        }
        val packetId = (if (packet.id != 0L) packet.id else nextPacketId.getAndIncrement()) and 0xFFFFFFFFL
        if (packetId == 0L || _deliveryStates.value.containsKey(packetId)) return@synchronized false
        val local = if (packet.from != 0L) packet.from and 0xFFFFFFFFL else _activeRadioInfo.value?.nodeNum ?: 0L
        val resolved = packet.copy(id = packetId, from = local, to = packet.to and 0xFFFFFFFFL)
        transmittedPackets.add(resolved)
        emitDeliveryUpdate(PacketDeliveryUpdate(packetId, PacketDeliveryStage.QUEUED))
        emitDeliveryUpdate(PacketDeliveryUpdate(packetId, PacketDeliveryStage.RADIO_ACCEPTED))
        if (resolved.wantAck && resolved.to != 0L && resolved.to != 0xFFFFFFFFL) {
            pending[packetId] = PendingDelivery(resolved, clockMs())
        }
        true
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

    override suspend fun pingNode(targetNodeNum: Long, permit: TransmissionPermit): Boolean = transmitPacket(
        MeshPacket(
            from = _activeRadioInfo.value?.nodeNum ?: 0L,
            to = targetNodeNum,
            portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP,
            payload = "PING".encodeToByteArray(),
            id = nextPacketId.getAndIncrement(),
            wantAck = true,
        ), permit,
    )

    fun simulateInboundPacket(packet: MeshPacket) {
        synchronized(lock) {
            expireDeliveryTimeoutsLocked()
            val expected = pending[packet.requestId]?.packet
            val error = packet.routingError
            if (packet.portNum == PortNum.PORTNUM_ROUTING_APP && packet.requestId != 0L && expected != null &&
                packet.from == expected.to && packet.to == expected.from && packet.channel == expected.channel && error != null
            ) {
                pending.remove(packet.requestId)
                val stage = if (error == 0) PacketDeliveryStage.PEER_DELIVERED else PacketDeliveryStage.FAILED
                emitDeliveryUpdate(PacketDeliveryUpdate(packet.requestId, stage, errorCode = error, timestampMs = clockMs()))
            }
        }
        _inboundPackets.tryEmit(packet)
    }

    fun expireDeliveryTimeouts() {
        synchronized(lock) { expireDeliveryTimeoutsLocked() }
    }

    private fun expireDeliveryTimeoutsLocked() {
        val now = clockMs()
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val (id, delivery) = iterator.next()
            if (now < delivery.queuedAt || now - delivery.queuedAt >= acknowledgementTimeoutMs) {
                iterator.remove()
                emitDeliveryUpdate(PacketDeliveryUpdate(id, PacketDeliveryStage.UNCONFIRMED, errorMessage = "Delivery acknowledgment timed out", timestampMs = now))
            }
        }
    }

    private fun finishPending(reason: String) {
        val now = clockMs()
        for (id in pending.keys) {
            emitDeliveryUpdate(PacketDeliveryUpdate(id, PacketDeliveryStage.UNCONFIRMED, errorMessage = reason, timestampMs = now))
        }
        pending.clear()
    }

    fun simulateDeliveryUpdate(update: PacketDeliveryUpdate) {
        synchronized(lock) {
            if (update.stage == PacketDeliveryStage.PEER_DELIVERED || update.stage == PacketDeliveryStage.FAILED) pending.remove(update.packetId)
            emitDeliveryUpdate(update)
        }
    }

    fun setNodes(nodes: List<MeshNodeInfo>) {
        _discoveredNodes.value = nodes
    }

    fun addDiscoveredNode(node: MeshNodeInfo) {
        _discoveredNodes.value = _discoveredNodes.value.filterNot { it.nodeNum == node.nodeNum } + node
    }

    fun setConnectionState(state: RadioConnectionState) {
        if (state == RadioConnectionState.DISCONNECTED || state == RadioConnectionState.ERROR) {
            synchronized(lock) { finishPending("Transport disconnected") }
        }
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
