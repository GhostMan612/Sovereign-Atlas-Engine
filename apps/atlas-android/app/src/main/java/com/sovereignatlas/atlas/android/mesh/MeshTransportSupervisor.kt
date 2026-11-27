// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import android.util.Log
import com.sovereignatlas.atlas.android.settings.NetworkProfile
import com.sovereignatlas.atlas.android.settings.SettingsRepository
import com.sovereignatlas.atlas.android.settings.CloudPolicyCoordinator
import com.sovereignatlas.atlas.android.settings.CloudPolicySnapshot
import com.sovereignatlas.atlas.geo.GeoPoint
import com.sovereignatlas.atlas.geo.cot.CotMessageRouter
import com.sovereignatlas.atlas.geo.cot.CotProtobufGenerator
import com.sovereignatlas.atlas.tactical.dtn.FragmentStore
import com.sovereignatlas.atlas.tactical.dtn.FragmentTier
import com.sovereignatlas.atlas.tactical.dtn.RegisterOutcome
import com.sovereignatlas.atlas.tactical.mesh.ChunkPacketCodec
import com.sovereignatlas.atlas.tactical.mesh.FragmentAckCodec
import com.sovereignatlas.atlas.tactical.mesh.MeshNodeInfo
import com.sovereignatlas.atlas.tactical.mesh.MeshPacket
import com.sovereignatlas.atlas.tactical.mesh.MeshRadioClient
import com.sovereignatlas.atlas.tactical.mesh.TransmissionPermit
import com.sovereignatlas.atlas.tactical.mesh.TransmissionOutcome
import com.sovereignatlas.atlas.tactical.mesh.PacketDeliveryUpdate
import com.sovereignatlas.atlas.tactical.mesh.PortNum
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.tactical.mesh.RadioHardwareInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class MeshTransportSupervisor(
    val radioClient: MeshRadioClient,
    val networkProfileProvider: () -> NetworkProfile,
    private val cotRouter: CotMessageRouter,
    val fragmentStore: FragmentStore,
    private val localUid: String,
    private val scope: CoroutineScope,
    private val transmissionPolicy: CloudPolicyCoordinator? = null,
) {
    constructor(
        radioClient: MeshRadioClient,
        settingsRepository: SettingsRepository,
        cotRouter: CotMessageRouter,
        fragmentStore: FragmentStore,
        localUid: String,
        scope: CoroutineScope,
    ) : this(
        radioClient = radioClient,
        networkProfileProvider = { settingsRepository.networkProfile.value },
        cotRouter = cotRouter,
        fragmentStore = fragmentStore,
        localUid = localUid,
        scope = scope,
        transmissionPolicy = settingsRepository.cloudPolicy,
    )

    val connectionState: StateFlow<RadioConnectionState> = radioClient.connectionState
    val isReady: StateFlow<Boolean> = radioClient.isReady
    val discoveredNodes: StateFlow<List<MeshNodeInfo>> = radioClient.discoveredNodes
    val activeRadioInfo: StateFlow<RadioHardwareInfo?> = radioClient.activeRadioInfo
    val deliveryUpdates: SharedFlow<PacketDeliveryUpdate> = radioClient.deliveryUpdates
    val deliveryStates: StateFlow<Map<Long, PacketDeliveryUpdate>> = radioClient.deliveryStates

    private var packetCollectorJob: Job? = null
    private var maintenanceJob: Job? = null
    private data class TransferPermit(val permit: TransmissionPermit, val expiresAt: Long)
    private val outboundPermits = ConcurrentHashMap<String, TransferPermit>()
    private val inboundPermits = ConcurrentHashMap<String, TransferPermit>()

    private fun rememberPermit(map: ConcurrentHashMap<String, TransferPermit>, transferId: String, snapshot: CloudPolicySnapshot?, expiresAt: Long) {
        val policy = transmissionPolicy ?: return
        if (snapshot == null) return
        val now = System.currentTimeMillis()
        map.entries.removeIf { it.value.expiresAt <= now }
        if (map.size >= MAX_TRANSFER_PERMITS) return
        map.putIfAbsent(transferId, TransferPermit(policy.meshPermit(snapshot), expiresAt))
    }

    private fun transferPermit(map: ConcurrentHashMap<String, TransferPermit>, transferId: String): TransmissionPermit? {
        val entry = map[transferId] ?: return null
        if (entry.expiresAt <= System.currentTimeMillis()) {
            map.remove(transferId, entry)
            return null
        }
        return entry.permit
    }

    init {
        startSupervisor()
    }

    fun canTransmit(): Boolean {
        val profile = networkProfileProvider()
        return profile == NetworkProfile.MESH_ONLY || profile == NetworkProfile.HYBRID_BRIDGE
    }

    private fun meshPermit(snapshot: CloudPolicySnapshot? = transmissionPolicy?.snapshot()): TransmissionPermit =
        if (transmissionPolicy != null && snapshot != null) transmissionPolicy.meshPermit(snapshot)
        else object : TransmissionPermit {
            override fun <T : Any> submit(action: () -> T): T? = if (canTransmit()) action() else null
        }

    suspend fun sendPacket(packet: MeshPacket, snapshot: CloudPolicySnapshot? = transmissionPolicy?.snapshot()): Boolean =
        radioClient.transmitPacket(packet, meshPermit(snapshot))

    suspend fun sendPacketOutcome(packet: MeshPacket, snapshot: CloudPolicySnapshot? = transmissionPolicy?.snapshot()): TransmissionOutcome =
        radioClient.transmitPacketOutcome(packet, meshPermit(snapshot))

    fun startSupervisor() {
        if (packetCollectorJob != null) return

        packetCollectorJob = scope.launch {
            radioClient.inboundPackets.collect { packet ->
                handleInboundPacket(packet)
            }
        }

        maintenanceJob = scope.launch {
            while (isActive) {
                delay(5_000L)
                if (canTransmit() && radioClient.isReady.value) {
                    val reports = fragmentStore.tick()
                    // FRA1 can encode many reports, but the wire MTU cannot.
                    // Send one bounded report per packet instead of an oversized batch.
                    for (report in reports) {
                        val permit = transferPermit(inboundPermits, report.transferId) ?: continue
                        val encoded = runCatching { FragmentAckCodec.encode(listOf(report)) }.getOrNull()
                            ?: continue
                        if (encoded.size > LORA_MTU_BYTES) continue
                        val myNode = activeRadioInfo.value?.nodeNum ?: 0L
                        radioClient.transmitPacket(
                            MeshPacket(
                                from = myNode,
                                to = 0xFFFFFFFFL,
                                portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                                payload = encoded,
                            ), permit
                        )
                    }
                }
                fragmentStore.sweep()
            }
        }
    }

    fun stopSupervisor() {
        packetCollectorJob?.cancel()
        packetCollectorJob = null
        maintenanceJob?.cancel()
        maintenanceJob = null
    }

    suspend fun sendCotMarker(
        type: String,
        callsign: String,
        lat: Double,
        lon: Double,
        snapshot: CloudPolicySnapshot? = transmissionPolicy?.snapshot(),
    ): Boolean = sendCotMarkerOutcome(type, callsign, lat, lon, snapshot) == TransmissionOutcome.SUBMITTED

    suspend fun sendCotMarkerOutcome(
        type: String,
        callsign: String,
        lat: Double,
        lon: Double,
        snapshot: CloudPolicySnapshot? = transmissionPolicy?.snapshot(),
    ): TransmissionOutcome {
        val permit = meshPermit(snapshot)
        if (!canTransmit()) {
            throw IllegalStateException("Marker transmission blocked: EMCON / Cloud-Only profile active")
        }
        if (!radioClient.isReady.value) {
            return TransmissionOutcome.NOT_SUBMITTED
        }

        val geoPoint = GeoPoint(lat, lon, null, null, null, System.currentTimeMillis())
        val markerUid = CotProtobufGenerator.buildMarkerUid(localUid)
        val protoBytes = CotProtobufGenerator.generateMarkerProto(
            localUid = localUid,
            type = type,
            callsign = callsign,
            geoPoint = geoPoint,
            markerUid = markerUid,
        )

        val myNode = activeRadioInfo.value?.nodeNum ?: 0L

        if (protoBytes.size <= LORA_MTU_BYTES) {
            return radioClient.transmitPacketOutcome(
                MeshPacket(
                    from = myNode,
                    to = 0xFFFFFFFFL,
                    portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                    payload = protoBytes,
                ), permit
            )
        }

        // The generated UID ends with a UUID. Its unique suffix is enough
        // for a short transfer identifier even if localUid is very long.
        val transferId = "cot-${markerUid.takeLast(36)}"
        val dataLimit = minOf(LORA_CHUNK_PAYLOAD_SIZE, ChunkPacketCodec.maxDataBytes(transferId))
        if (dataLimit <= 0) return TransmissionOutcome.NOT_SUBMITTED
        val chunks = splitIntoChunks(protoBytes, dataLimit)
        if (chunks.size !in 1..ChunkPacketCodec.MAX_CHUNKS) return TransmissionOutcome.NOT_SUBMITTED
        val wireFrames = chunks.mapIndexed { index, data ->
            ChunkPacketCodec.encode(transferId, index, chunks.size, data) ?: return TransmissionOutcome.NOT_SUBMITTED
        }
        // Retain exactly what was transmitted, not unframed payloads.
        val expiresAt = System.currentTimeMillis() + 600_000L
        fragmentStore.registerOutbound(
            transferId = transferId,
            totalChunks = wireFrames.size,
            tier = FragmentTier.PUBLIC,
            counterpartyId = "broadcast",
            expiresAt = expiresAt,
            frames = wireFrames,
        )
        rememberPermit(outboundPermits, transferId, snapshot, expiresAt)

        var result = TransmissionOutcome.NOT_SUBMITTED
        for (frame in wireFrames) {
            val sent = radioClient.transmitPacketOutcome(
                MeshPacket(
                    from = myNode,
                    to = 0xFFFFFFFFL,
                    portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                    payload = frame,
                ), permit
            )
            if (sent != TransmissionOutcome.SUBMITTED) {
                return if (sent == TransmissionOutcome.UNCONFIRMED || result == TransmissionOutcome.SUBMITTED) TransmissionOutcome.UNCONFIRMED else sent
            }
            result = TransmissionOutcome.SUBMITTED
        }
        return result
    }

    suspend fun sendTextMessage(text: String, targetNodeNum: Long = 0xFFFFFFFFL): Boolean =
        sendTextMessageOutcome(text, targetNodeNum) == TransmissionOutcome.SUBMITTED

    suspend fun sendTextMessageOutcome(text: String, targetNodeNum: Long = 0xFFFFFFFFL): TransmissionOutcome {
        val permit = meshPermit()
        if (!canTransmit()) {
            throw IllegalStateException("Text message blocked: EMCON / Cloud-Only profile active")
        }
        if (!radioClient.isReady.value) {
            return TransmissionOutcome.NOT_SUBMITTED
        }
        val myNode = activeRadioInfo.value?.nodeNum ?: 0L
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > LORA_MTU_BYTES) return TransmissionOutcome.NOT_SUBMITTED
        return radioClient.transmitPacketOutcome(
            MeshPacket(
                from = myNode,
                to = targetNodeNum,
                portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP,
                payload = bytes,
            ), permit
        )
    }

    suspend fun pingNode(targetNodeNum: Long, snapshot: CloudPolicySnapshot? = transmissionPolicy?.snapshot()): Boolean =
        pingNodeOutcome(targetNodeNum, snapshot) == TransmissionOutcome.SUBMITTED

    suspend fun pingNodeOutcome(targetNodeNum: Long, snapshot: CloudPolicySnapshot? = transmissionPolicy?.snapshot()): TransmissionOutcome {
        val permit = meshPermit(snapshot)
        if (!canTransmit() || !radioClient.isReady.value) return TransmissionOutcome.NOT_SUBMITTED
        return radioClient.pingNodeOutcome(targetNodeNum, permit)
    }

    private suspend fun handleInboundPacket(packet: MeshPacket) {
        val payload = packet.payload
        if (payload.isEmpty()) return

        when (packet.portNum) {
            PortNum.PORTNUM_ATAK_PLUGIN, PortNum.PORTNUM_PRIVATE_APP -> {
                if (payload.size >= 4 &&
                    payload[0] == 'F'.code.toByte() &&
                    payload[1] == 'R'.code.toByte() &&
                    payload[2] == 'A'.code.toByte() &&
                    payload[3] == '1'.code.toByte()
                ) {
                    // Bound control packets without breaking legacy raw CoT input:
                    // raw XML and protobuf CoT can originate outside CHK1 transport.
                    if (payload.size > LORA_MTU_BYTES) return
                    val reports = FragmentAckCodec.decode(payload) ?: return
                    for (report in reports) {
                        val permit = transferPermit(outboundPermits, report.transferId) ?: continue
                        val repairs = permit.submit { fragmentStore.processAckReport(report) } ?: continue
                        if (canTransmit()) {
                            val myNode = activeRadioInfo.value?.nodeNum ?: 0L
                            for (repairFrame in repairs) {
                                if (repairFrame.size > LORA_MTU_BYTES ||
                                    ChunkPacketCodec.decode(repairFrame) == null
                                ) continue
                                radioClient.transmitPacket(
                                    MeshPacket(
                                        from = myNode,
                                        to = packet.from,
                                        portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                                        payload = repairFrame,
                                    ), permit
                                )
                            }
                        }
                    }
                    return
                }

                if (ChunkPacketCodec.hasMagic(payload)) {
                    val chunk = ChunkPacketCodec.decode(payload) ?: return
                    val snapshot = transmissionPolicy?.snapshot()
                    val expiresAt = System.currentTimeMillis() + 600_000L
                    val (outcome, created) = fragmentStore.registerInboundWithCreation(
                        transferId = chunk.transferId,
                        chunkIndex = chunk.chunkIndex,
                        totalChunks = chunk.totalChunks,
                        tier = FragmentTier.PUBLIC,
                        counterpartyId = packet.from.toString(),
                        expiresAt = expiresAt,
                        frame = chunk.data,
                    )
                    if (created) rememberPermit(inboundPermits, chunk.transferId, snapshot, expiresAt)
                    if (outcome == RegisterOutcome.STORED && fragmentStore.isInboundComplete(chunk.transferId)) {
                        val fullPayload = fragmentStore.assembleInboundPayload(chunk.transferId)
                        if (fullPayload != null) {
                            fragmentStore.markDelivered(chunk.transferId)
                            cotRouter.route(fullPayload)
                        }
                    }
                    return
                }

                cotRouter.route(payload)
            }
            PortNum.PORTNUM_TEXT_MESSAGE_APP -> {
                val text = String(payload, Charsets.UTF_8)
                Log.i("MeshTransportSupervisor", "Received mesh text from ${packet.from}: $text")
            }
        }
    }

    private fun splitIntoChunks(data: ByteArray, chunkSize: Int): List<ByteArray> {
        val chunks = ArrayList<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            val len = minOf(chunkSize, data.size - offset)
            chunks.add(data.copyOfRange(offset, offset + len))
            offset += len
        }
        return chunks
    }

    companion object {
        private const val MAX_TRANSFER_PERMITS = 512
        const val LORA_MTU_BYTES = 237
        const val LORA_CHUNK_PAYLOAD_SIZE = 180
    }
}
