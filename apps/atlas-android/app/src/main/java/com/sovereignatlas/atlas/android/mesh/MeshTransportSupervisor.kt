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
                    if (reports.isNotEmpty()) {
                        val encoded = FragmentAckCodec.encode(reports)
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

        val chunks = splitIntoChunks(protoBytes, LORA_CHUNK_PAYLOAD_SIZE)
        val transferId = "cot-$markerUid"
        fragmentStore.registerOutbound(
            transferId = transferId,
            totalChunks = chunks.size,
            tier = FragmentTier.PUBLIC,
            counterpartyId = "broadcast",
            expiresAt = System.currentTimeMillis() + 600_000L,
            frames = chunks,
        )

        var allSent = true
        for (i in chunks.indices) {
            val chunkPayload = buildChunkPacketPayload(transferId, i, chunks.size, chunks[i])
            val sent = radioClient.transmitPacket(
                MeshPacket(
                    from = myNode,
                    to = 0xFFFFFFFFL,
                    portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                    payload = chunkPayload,
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
                    val reports = FragmentAckCodec.decode(payload) ?: return
                    for (report in reports) {
                        val repairs = fragmentStore.processAckReport(report)
                        if (canTransmit()) {
                            val myNode = activeRadioInfo.value?.nodeNum ?: 0L
                            for (repairFrame in repairs) {
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

                if (isChunkPacket(payload)) {
                    val chunk = parseChunkPacket(payload) ?: return
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

    private fun isChunkPacket(bytes: ByteArray): Boolean {
        return bytes.size >= 8 &&
            bytes[0] == 'C'.code.toByte() &&
            bytes[1] == 'H'.code.toByte() &&
            bytes[2] == 'K'.code.toByte() &&
            bytes[3] == '1'.code.toByte()
    }

    private fun buildChunkPacketPayload(
        transferId: String,
        chunkIndex: Int,
        totalChunks: Int,
        data: ByteArray,
    ): ByteArray {
        val idBytes = transferId.toByteArray(Charsets.UTF_8)
        val idLen = minOf(idBytes.size, 0xFF)
        val out = ByteArray(8 + idLen + data.size)
        out[0] = 'C'.code.toByte()
        out[1] = 'H'.code.toByte()
        out[2] = 'K'.code.toByte()
        out[3] = '1'.code.toByte()
        out[4] = idLen.toByte()
        out[5] = (chunkIndex and 0xFF).toByte()
        out[6] = (totalChunks and 0xFF).toByte()
        out[7] = 0.toByte()
        System.arraycopy(idBytes, 0, out, 8, idLen)
        System.arraycopy(data, 0, out, 8 + idLen, data.size)
        return out
    }

    private data class ParsedChunk(
        val transferId: String,
        val chunkIndex: Int,
        val totalChunks: Int,
        val data: ByteArray,
    )

    private fun parseChunkPacket(bytes: ByteArray): ParsedChunk? {
        if (!isChunkPacket(bytes)) return null
        val idLen = bytes[4].toInt() and 0xFF
        val chunkIndex = bytes[5].toInt() and 0xFF
        val totalChunks = bytes[6].toInt() and 0xFF
        if (bytes.size < 8 + idLen) return null
        val transferId = String(bytes, 8, idLen, Charsets.UTF_8)
        val data = bytes.copyOfRange(8 + idLen, bytes.size)
        return ParsedChunk(transferId, chunkIndex, totalChunks, data)
    }

    companion object {
        const val LORA_MTU_BYTES = 237
        const val LORA_CHUNK_PAYLOAD_SIZE = 180
    }
}
