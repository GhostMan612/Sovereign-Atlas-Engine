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

class MeshtasticProtocolSession(
    private val wireAdapter: MeshtasticCodec,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val acknowledgementTimeoutMs: Long = 120_000L,
) {
    constructor() : this(MeshtasticWireAdapter(), { System.currentTimeMillis() }, 120_000L)

    constructor(
        wireAdapter: MeshtasticWireAdapter = MeshtasticWireAdapter(),
        clockMs: () -> Long = { System.currentTimeMillis() },
        acknowledgementTimeoutMs: Long = 120_000L,
    ) : this(MeshtasticLegacyCodec(wireAdapter), clockMs, acknowledgementTimeoutMs)

    data class PreparedTransmission(
        val packet: MeshPacket,
        val toRadioBytes: ByteArray,
    )

    private val lock = Any()
    private data class PendingDelivery(val local: Long, val peer: Long, val channel: Int, val wantsAck: Boolean, val queuedAt: Long)
    private val pendingDeliveries = LinkedHashMap<Long, PendingDelivery>()
    private val packetIdSeq = AtomicLong((clockMs() and 0x3FFFFFFFL).coerceAtLeast(1L))
    private val configNonceSeq = AtomicLong(1000L)

    private var isUsbSession: Boolean = false
    private var activeConfigNonce: Long? = null
    private var myNodeNum: Long? = null
    private var metadata: DecodedFromRadio.Metadata? = null
    private var loraRegion: DecodedFromRadio.LoraRegion? = null
    private val nodesByNum = LinkedHashMap<Long, MeshNodeInfo>()

    private val _connectionState = MutableStateFlow(RadioConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RadioConnectionState> = _connectionState.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _discoveredNodes = MutableStateFlow<List<MeshNodeInfo>>(emptyList())
    val discoveredNodes: StateFlow<List<MeshNodeInfo>> = _discoveredNodes.asStateFlow()

    private val _inboundPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 128)
    val inboundPackets: SharedFlow<MeshPacket> = _inboundPackets.asSharedFlow()

    private val _activeRadioInfo = MutableStateFlow<RadioHardwareInfo?>(null)
    val activeRadioInfo: StateFlow<RadioHardwareInfo?> = _activeRadioInfo.asStateFlow()

    private val _deliveryUpdates = MutableSharedFlow<PacketDeliveryUpdate>(extraBufferCapacity = 128)
    val deliveryUpdates: SharedFlow<PacketDeliveryUpdate> = _deliveryUpdates.asSharedFlow()

    private val _deliveryStates = MutableStateFlow<Map<Long, PacketDeliveryUpdate>>(emptyMap())
    val deliveryStates: StateFlow<Map<Long, PacketDeliveryUpdate>> = _deliveryStates.asStateFlow()

    private val _malformedFrameCount = MutableStateFlow(0L)
    val malformedFrameCount: StateFlow<Long> = _malformedFrameCount.asStateFlow()

    private val _rebootEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val rebootEvents: SharedFlow<Unit> = _rebootEvents.asSharedFlow()

    fun beginConnecting() {
        synchronized(lock) {
            pendingDeliveries.clear()
            activeConfigNonce = null
            myNodeNum = null
            metadata = null
            loraRegion = null
            nodesByNum.clear()
            _discoveredNodes.value = emptyList()
            _activeRadioInfo.value = null
            _isReady.value = false
            _connectionState.value = RadioConnectionState.CONNECTING
        }
    }

    fun beginHandshake(isUsb: Boolean, configNonce: Long = allocateConfigNonce()): ByteArray {
        val wantConfigBytes = wireAdapter.encodeWantConfig(configNonce)
        synchronized(lock) {
            isUsbSession = isUsb
            activeConfigNonce = configNonce
            myNodeNum = null
            metadata = null
            loraRegion = null
            _isReady.value = false
            _activeRadioInfo.value = null
            _connectionState.value = RadioConnectionState.HANDSHAKING
        }
        return wantConfigBytes
    }

    fun onTransportDisconnected(isError: Boolean = false, reason: String? = null) {
        val failedUpdates = ArrayList<PacketDeliveryUpdate>()
        synchronized(lock) {
            val now = clockMs()
            activeConfigNonce = null
            myNodeNum = null
            metadata = null
            loraRegion = null
            nodesByNum.clear()
            _discoveredNodes.value = emptyList()
            _activeRadioInfo.value = null
            _isReady.value = false
            _connectionState.value = if (isError) RadioConnectionState.ERROR else RadioConnectionState.DISCONNECTED
            failInFlightDeliveriesLocked(
                reason = reason ?: if (isError) "Transport error" else "Transport disconnected",
                timestampMs = now,
                out = failedUpdates,
            )
        }
        failedUpdates.forEach { _deliveryUpdates.tryEmit(it) }
    }

    fun prepareOutgoingPacket(
        packet: MeshPacket,
        maxEnvelopeBytes: Int = MeshtasticCodec.MAX_TO_FROM_RADIO_SIZE,
    ): PreparedTransmission? {
        val normalized: MeshPacket
        synchronized(lock) {
            if (!_isReady.value) return null
            val state = _connectionState.value
            if (state != RadioConnectionState.CONNECTED && state != RadioConnectionState.STREAMING) {
                return null
            }
            val resolvedId = if (packet.id != 0L) (packet.id and 0xFFFFFFFFL) else allocatePacketId()
            if (resolvedId == 0L || _deliveryStates.value.containsKey(resolvedId)) return null
            val resolvedFrom = if (packet.from != 0L) (packet.from and 0xFFFFFFFFL) else (myNodeNum ?: 0L)
            normalized = packet.copy(from = resolvedFrom, id = resolvedId)
        }

        val toRadioBytes = try {
            wireAdapter.encodePacket(normalized, maxEnvelopeBytes)
        } catch (e: IllegalArgumentException) {
            recordDeliveryUpdate(
                PacketDeliveryUpdate(
                    packetId = normalized.id,
                    stage = PacketDeliveryStage.FAILED,
                    errorMessage = e.message ?: "Envelope too large",
                    timestampMs = clockMs(),
                )
            )
            return null
        }

        val queued = PacketDeliveryUpdate(
                packetId = normalized.id,
                stage = PacketDeliveryStage.QUEUED,
                timestampMs = clockMs(),
            )
        synchronized(lock) {
            if (!_isReady.value || _deliveryStates.value.containsKey(normalized.id)) return null
            pendingDeliveries[normalized.id] = PendingDelivery(normalized.from, normalized.to and 0xFFFFFFFFL, normalized.channel, normalized.wantAck, queued.timestampMs)
            _deliveryStates.value = _deliveryStates.value + (normalized.id to queued)
        }
        _deliveryUpdates.tryEmit(queued)
        return PreparedTransmission(normalized, toRadioBytes)
    }

    fun markTransportWriteFailed(packetId: Long, reason: String) {
        val id = packetId and 0xFFFFFFFFL
        val update: PacketDeliveryUpdate
        synchronized(lock) {
            if (pendingDeliveries.remove(id) == null) return
            update = PacketDeliveryUpdate(
                packetId = id,
                stage = PacketDeliveryStage.FAILED,
                errorMessage = reason,
                timestampMs = clockMs(),
            )
            _deliveryStates.value = _deliveryStates.value + (id to update)
        }
        _deliveryUpdates.tryEmit(update)
    }

    fun markTransportWriteUnconfirmed(packetId: Long, reason: String) {
        val id = packetId and 0xFFFFFFFFL
        val update: PacketDeliveryUpdate
        synchronized(lock) {
            if (!pendingDeliveries.containsKey(id)) return
            if (_deliveryStates.value[id]?.stage != PacketDeliveryStage.QUEUED) return
            update = PacketDeliveryUpdate(id, PacketDeliveryStage.UNCONFIRMED, errorMessage = reason, timestampMs = clockMs())
            _deliveryStates.value = _deliveryStates.value + (id to update)
        }
        _deliveryUpdates.tryEmit(update)
    }

    fun buildPingPacket(targetNodeNum: Long): MeshPacket {
        val local = synchronized(lock) { myNodeNum ?: 0L }
        return MeshPacket(
            from = local,
            to = targetNodeNum and 0xFFFFFFFFL,
            portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP,
            payload = "PING".encodeToByteArray(),
            id = allocatePacketId(),
            wantAck = true,
        )
    }

    fun encodeDisconnectCommand(): ByteArray = wireAdapter.encodeDisconnect()

    fun onFromRadioBytes(bytes: ByteArray): Boolean {
        val decoded = wireAdapter.decodeFromRadio(bytes)
        if (decoded == null) {
            _malformedFrameCount.value = _malformedFrameCount.value + 1L
            return false
        }

        var packetToEmit: MeshPacket? = null
        var deliveryToEmit: PacketDeliveryUpdate? = null
        val extraDeliveryFailures = ArrayList<PacketDeliveryUpdate>()
        var rebooted = false

        synchronized(lock) {
            expirePendingDeliveriesLocked(clockMs(), extraDeliveryFailures)
            when (decoded) {
                is DecodedFromRadio.MyInfo -> {
                    myNodeNum = decoded.myNodeNum
                    nodesByNum.remove(decoded.myNodeNum)
                    publishNodesLocked()
                    publishHardwareLocked()
                }
                is DecodedFromRadio.Metadata -> {
                    metadata = decoded
                    publishHardwareLocked()
                }
                is DecodedFromRadio.LoraRegion -> {
                    loraRegion = decoded
                    publishHardwareLocked()
                }
                is DecodedFromRadio.DiscoveredNode -> {
                    val incoming = decoded.node
                    if (incoming.nodeNum != myNodeNum) {
                        val existing = nodesByNum[incoming.nodeNum]
                        nodesByNum[incoming.nodeNum] = mergeNodeInfo(existing, incoming)
                        publishNodesLocked()
                    }
                }
                is DecodedFromRadio.QueueAck -> {
                    val existing = _deliveryStates.value[decoded.meshPacketId]
                    val pending = pendingDeliveries[decoded.meshPacketId]
                    val update = when {
                        pending == null || (existing?.stage != PacketDeliveryStage.QUEUED && existing?.stage != PacketDeliveryStage.UNCONFIRMED && existing?.stage != PacketDeliveryStage.RADIO_ACCEPTED) -> null
                        decoded.result != 0 -> PacketDeliveryUpdate(
                            packetId = decoded.meshPacketId,
                            stage = PacketDeliveryStage.FAILED,
                            errorCode = decoded.result,
                            errorMessage = "Radio queue error ${decoded.result}",
                            timestampMs = clockMs(),
                        )
                        existing?.stage == PacketDeliveryStage.QUEUED || existing?.stage == PacketDeliveryStage.UNCONFIRMED -> PacketDeliveryUpdate(
                            packetId = decoded.meshPacketId,
                            stage = PacketDeliveryStage.RADIO_ACCEPTED,
                            timestampMs = clockMs(),
                        )
                        else -> null
                    }
                    if (update != null) {
                        if (update.stage == PacketDeliveryStage.FAILED || !pending!!.wantsAck || pending.peer == 0xFFFFFFFFL || pending.peer == 0L) {
                            pendingDeliveries.remove(update.packetId)
                        }
                        _deliveryStates.value = _deliveryStates.value + (update.packetId to update)
                        deliveryToEmit = update
                    }
                }
                is DecodedFromRadio.ConfigComplete -> {
                    val expected = activeConfigNonce
                    if (expected != null && decoded.configCompleteId == expected && myNodeNum != null) {
                        activeConfigNonce = null
                        _isReady.value = true
                        _connectionState.value = RadioConnectionState.CONNECTED
                    }
                }
                is DecodedFromRadio.Rebooted -> {
                    _isReady.value = false
                    myNodeNum = null
                    _activeRadioInfo.value = null
                    _connectionState.value = RadioConnectionState.HANDSHAKING
                    failInFlightDeliveriesLocked("Radio rebooted", clockMs(), extraDeliveryFailures)
                    rebooted = true
                }
                is DecodedFromRadio.InboundPacket -> {
                    val packet = decoded.packet
                    if (_isReady.value && _connectionState.value == RadioConnectionState.CONNECTED) {
                        _connectionState.value = RadioConnectionState.STREAMING
                    }
                    val pending = pendingDeliveries[packet.requestId]
                    if (packet.portNum == PortNum.PORTNUM_ROUTING_APP && packet.requestId != 0L &&
                        pending != null && pending.wantsAck && pending.peer != 0L && pending.peer != 0xFFFFFFFFL &&
                        packet.from == pending.peer && packet.to == pending.local && packet.channel == pending.channel &&
                        packet.routingError != null
                    ) {
                        val err = packet.routingError
                        val stage = if (err == 0) {
                            PacketDeliveryStage.PEER_DELIVERED
                        } else {
                            PacketDeliveryStage.FAILED
                        }
                        val update = PacketDeliveryUpdate(
                            packetId = packet.requestId,
                            stage = stage,
                            errorCode = err,
                            timestampMs = clockMs(),
                        )
                        _deliveryStates.value = _deliveryStates.value + (update.packetId to update)
                        pendingDeliveries.remove(update.packetId)
                        deliveryToEmit = update
                    }
                    if (packet.from != 0L && packet.from != myNodeNum) {
                        enrichNodeFromPacketLocked(packet)
                    }
                    packetToEmit = packet
                }
                is DecodedFromRadio.Ignored -> Unit
            }
        }

        extraDeliveryFailures.forEach { _deliveryUpdates.tryEmit(it) }
        deliveryToEmit?.let { _deliveryUpdates.tryEmit(it) }
        packetToEmit?.let { _inboundPackets.tryEmit(it) }
        if (rebooted) {
            _rebootEvents.tryEmit(Unit)
        }
        return true
    }

    private fun failInFlightDeliveriesLocked(
        reason: String,
        timestampMs: Long,
        out: MutableList<PacketDeliveryUpdate>,
    ) {
        val nextMap = LinkedHashMap(_deliveryStates.value)
        for (packetId in pendingDeliveries.keys) {
            val existing = _deliveryStates.value[packetId] ?: continue
            if (existing.stage == PacketDeliveryStage.QUEUED || existing.stage == PacketDeliveryStage.UNCONFIRMED ||
                existing.stage == PacketDeliveryStage.RADIO_ACCEPTED
            ) {
                val failed = PacketDeliveryUpdate(
                    packetId = packetId,
                    stage = PacketDeliveryStage.UNCONFIRMED,
                    errorMessage = reason,
                    timestampMs = timestampMs,
                )
                nextMap[packetId] = failed
                out.add(failed)
            }
        }
        pendingDeliveries.clear()
        if (out.isNotEmpty()) {
            _deliveryStates.value = nextMap
        }
    }

    fun expireDeliveryTimeouts() {
        val expired = ArrayList<PacketDeliveryUpdate>()
        synchronized(lock) { expirePendingDeliveriesLocked(clockMs(), expired) }
        expired.forEach { _deliveryUpdates.tryEmit(it) }
    }

    private fun expirePendingDeliveriesLocked(now: Long, out: MutableList<PacketDeliveryUpdate>) {
        val iterator = pendingDeliveries.iterator()
        val next = LinkedHashMap(_deliveryStates.value)
        while (iterator.hasNext()) {
            val (id, pending) = iterator.next()
            if (now < pending.queuedAt || now - pending.queuedAt >= acknowledgementTimeoutMs) {
                iterator.remove()
                val update = PacketDeliveryUpdate(id, PacketDeliveryStage.UNCONFIRMED, errorMessage = "Delivery acknowledgment timed out", timestampMs = now)
                next[id] = update
                out.add(update)
            }
        }
        if (out.isNotEmpty()) _deliveryStates.value = next
    }

    private fun enrichNodeFromPacketLocked(packet: MeshPacket) {
        val current = nodesByNum[packet.from] ?: MeshNodeInfo(
            nodeNum = packet.from,
            user = "Node !%08x".format(packet.from),
            callsign = "!%04x".format(packet.from and 0xFFFFL),
        )
        val hopCount = if (packet.hopStart != null && packet.hopStart >= packet.hopLimit) {
            packet.hopStart - packet.hopLimit
        } else {
            current.hopsAway
        }
        var updated = current.copy(
            snr = packet.rxSnr ?: current.snr,
            rssi = packet.rxRssi ?: current.rssi,
            lastHeardTimestampMs = clockMs(),
            hopsAway = hopCount,
        )
        when (packet.portNum) {
            PortNum.PORTNUM_NODEINFO_APP -> {
                wireAdapter.decodeUserPayload(packet.payload)?.let { user ->
                    updated = updated.copy(
                        user = user.longName ?: updated.user,
                        callsign = user.shortName ?: updated.callsign,
                    )
                }
            }
            PortNum.PORTNUM_POSITION_APP -> {
                wireAdapter.decodePositionPayload(packet.payload)?.let { pos ->
                    updated = updated.copy(
                        latitude = pos.latitude,
                        longitude = pos.longitude,
                        altitudeMeters = pos.altitudeMeters ?: updated.altitudeMeters,
                    )
                }
            }
            PortNum.PORTNUM_TELEMETRY_APP -> {
                wireAdapter.decodeTelemetryPayload(packet.payload)?.let { telemetry ->
                    updated = updated.copy(batteryLevel = telemetry.batteryLevel)
                }
            }
        }
        nodesByNum[packet.from] = updated
        publishNodesLocked()
    }

    private fun mergeNodeInfo(existing: MeshNodeInfo?, incoming: MeshNodeInfo): MeshNodeInfo {
        if (existing == null) return incoming
        return incoming.copy(
            snr = incoming.snr ?: existing.snr,
            rssi = incoming.rssi ?: existing.rssi,
            batteryLevel = incoming.batteryLevel ?: existing.batteryLevel,
            lastHeardTimestampMs = maxOf(incoming.lastHeardTimestampMs, existing.lastHeardTimestampMs),
            hopsAway = incoming.hopsAway ?: existing.hopsAway,
            latitude = incoming.latitude ?: existing.latitude,
            longitude = incoming.longitude ?: existing.longitude,
            altitudeMeters = incoming.altitudeMeters ?: existing.altitudeMeters,
        )
    }

    private fun publishHardwareLocked() {
        val nodeNum = myNodeNum ?: return
        _activeRadioInfo.value = RadioHardwareInfo(
            radioModel = metadata?.hardwareModelName,
            frequencyBand = loraRegion?.frequencyBand,
            firmwareVersion = metadata?.firmwareVersion,
            nodeNum = nodeNum,
            isUsb = isUsbSession,
            hardwareModelCode = metadata?.hardwareModelCode,
            regionCode = loraRegion?.regionCode,
        )
    }

    private fun publishNodesLocked() {
        _discoveredNodes.value = nodesByNum.values.toList()
    }

    private fun recordDeliveryUpdate(update: PacketDeliveryUpdate) {
        synchronized(lock) {
            _deliveryStates.value = _deliveryStates.value + (update.packetId to update)
        }
        _deliveryUpdates.tryEmit(update)
    }

    private fun allocatePacketId(): Long {
        while (true) {
            val next = packetIdSeq.getAndIncrement() and 0xFFFFFFFFL
            if (next != 0L) return next
        }
    }

    private fun allocateConfigNonce(): Long {
        while (true) {
            val next = configNonceSeq.getAndIncrement() and 0x7FFFFFFFL
            if (next != 0L) return next
        }
    }
}
