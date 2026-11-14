// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import android.util.Log
import com.sovereignatlas.atlas.android.settings.NetworkProfile
import com.sovereignatlas.atlas.android.settings.SettingsRepository
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
import com.sovereignatlas.atlas.tactical.mesh.PortNum
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.tactical.mesh.RadioHardwareInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MeshTransportSupervisor(
    val radioClient: MeshRadioClient,
    val networkProfileProvider: () -> NetworkProfile,
    private val cotRouter: CotMessageRouter,
    val fragmentStore: FragmentStore,
    private val localUid: String,
    private val scope: CoroutineScope,
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
    )

    val connectionState: StateFlow<RadioConnectionState> = radioClient.connectionState
    val discoveredNodes: StateFlow<List<MeshNodeInfo>> = radioClient.discoveredNodes
    val activeRadioInfo: StateFlow<RadioHardwareInfo?> = radioClient.activeRadioInfo

    private var packetCollectorJob: Job? = null
    private var maintenanceJob: Job? = null

    init {
        startSupervisor()
    }

    fun canTransmit(): Boolean {
        val profile = networkProfileProvider()
        return profile == NetworkProfile.MESH_ONLY || profile == NetworkProfile.HYBRID_BRIDGE
    }

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
                if (canTransmit() && radioClient.connectionState.value == RadioConnectionState.CONNECTED) {
                    val reports = fragmentStore.tick()
                    // FRA1 can encode many reports, but the wire MTU cannot.
                    // Send one bounded report per packet instead of an oversized batch.
                    for (report in reports) {
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
                            )
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
    ): Boolean {
        if (!canTransmit()) {
            throw IllegalStateException("Marker transmission blocked: EMCON / Cloud-Only profile active")
        }
        if (radioClient.connectionState.value != RadioConnectionState.CONNECTED &&
            radioClient.connectionState.value != RadioConnectionState.STREAMING
        ) {
            return false
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
            return radioClient.transmitPacket(
                MeshPacket(
                    from = myNode,
                    to = 0xFFFFFFFFL,
                    portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                    payload = protoBytes,
                )
            )
        }

        // The generated UID ends with a UUID. Its unique suffix is enough
        // for a short transfer identifier even if localUid is very long.
        val transferId = "cot-${markerUid.takeLast(36)}"
        val dataLimit = minOf(LORA_CHUNK_PAYLOAD_SIZE, ChunkPacketCodec.maxDataBytes(transferId))
        if (dataLimit <= 0) return false
        val chunks = splitIntoChunks(protoBytes, dataLimit)
        if (chunks.size !in 1..ChunkPacketCodec.MAX_CHUNKS) return false
        val wireFrames = chunks.mapIndexed { index, data ->
            ChunkPacketCodec.encode(transferId, index, chunks.size, data) ?: return false
        }
        // Retain exactly what was transmitted, not unframed payloads.
        fragmentStore.registerOutbound(
            transferId = transferId,
            totalChunks = wireFrames.size,
            tier = FragmentTier.PUBLIC,
            counterpartyId = "broadcast",
            expiresAt = System.currentTimeMillis() + 600_000L,
            frames = wireFrames,
        )

        var allSent = true
        for (frame in wireFrames) {
            val sent = radioClient.transmitPacket(
                MeshPacket(
                    from = myNode,
                    to = 0xFFFFFFFFL,
                    portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                    payload = frame,
                )
            )
            if (!sent) allSent = false
        }
        return allSent
    }

    suspend fun sendTextMessage(text: String, targetNodeNum: Long = 0xFFFFFFFFL): Boolean {
        if (!canTransmit()) {
            throw IllegalStateException("Text message blocked: EMCON / Cloud-Only profile active")
        }
        val myNode = activeRadioInfo.value?.nodeNum ?: 0L
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > LORA_MTU_BYTES) return false
        return radioClient.transmitPacket(
            MeshPacket(
                from = myNode,
                to = targetNodeNum,
                portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP,
                payload = bytes,
            )
        )
    }

    suspend fun pingNode(targetNodeNum: Long): Boolean {
        if (!canTransmit()) return false
        return radioClient.pingNode(targetNodeNum)
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
                        val repairs = fragmentStore.processAckReport(report)
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
                                    )
                                )
                            }
                        }
                    }
                    return
                }

                if (ChunkPacketCodec.hasMagic(payload)) {
                    val chunk = ChunkPacketCodec.decode(payload) ?: return
                    val outcome = fragmentStore.registerInbound(
                        transferId = chunk.transferId,
                        chunkIndex = chunk.chunkIndex,
                        totalChunks = chunk.totalChunks,
                        tier = FragmentTier.PUBLIC,
                        counterpartyId = packet.from.toString(),
                        expiresAt = System.currentTimeMillis() + 600_000L,
                        frame = chunk.data,
                    )
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
        const val LORA_MTU_BYTES = 237
        const val LORA_CHUNK_PAYLOAD_SIZE = 180
    }
}
