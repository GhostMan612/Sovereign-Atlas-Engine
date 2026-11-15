// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import okio.ByteString
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.MeshPacket as ProtoMeshPacket
import org.meshtastic.proto.PortNum as ProtoPortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.Routing as ProtoRouting
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User
import java.io.IOException

sealed class DecodedFromRadio {
    data class MyInfo(
        val myNodeNum: Long,
        val rebootCount: Long,
        val nodedbCount: Int,
        val pioEnv: String?,
    ) : DecodedFromRadio()

    data class Metadata(
        val firmwareVersion: String?,
        val hardwareModelCode: Int?,
        val hardwareModelName: String?,
    ) : DecodedFromRadio()

    data class LoraRegion(
        val regionCode: Int,
        val frequencyBand: String?,
    ) : DecodedFromRadio()

    data class DiscoveredNode(
        val node: MeshNodeInfo,
    ) : DecodedFromRadio()

    data class InboundPacket(
        val packet: MeshPacket,
    ) : DecodedFromRadio()

    data class QueueAck(
        val meshPacketId: Long,
        val result: Int,
        val free: Int,
        val maxlen: Int,
    ) : DecodedFromRadio()

    data class ConfigComplete(
        val configCompleteId: Long,
    ) : DecodedFromRadio()

    data object Rebooted : DecodedFromRadio()
    data object Ignored : DecodedFromRadio()
}

class MeshtasticWireAdapter {

    fun encodeWantConfig(configId: Long): ByteArray {
        require(configId in 1L..0xFFFFFFFFL) {
            "want_config_id must be a non-zero unsigned 32-bit integer"
        }
        val toRadio = ToRadio(want_config_id = configId.toInt())
        return encodeToRadioStrict(toRadio, MAX_TO_FROM_RADIO_SIZE)
    }

    fun encodeDisconnect(): ByteArray {
        return encodeToRadioStrict(ToRadio(disconnect = true), MAX_TO_FROM_RADIO_SIZE)
    }

    fun encodeHeartbeat(nonce: Long = 0L): ByteArray {
        return encodeToRadioStrict(
            ToRadio(heartbeat = Heartbeat(nonce = nonce.toInt())),
            MAX_TO_FROM_RADIO_SIZE,
        )
    }

    fun encodePacket(
        packet: MeshPacket,
        maxEnvelopeBytes: Int = MAX_TO_FROM_RADIO_SIZE,
    ): ByteArray {
        require(maxEnvelopeBytes in 1..MAX_TO_FROM_RADIO_SIZE) {
            "maxEnvelopeBytes must be in 1..$MAX_TO_FROM_RADIO_SIZE"
        }
        val protoPortNum = ProtoPortNum.fromValue(packet.portNum)
            ?: throw IllegalArgumentException("Unsupported Meshtastic PortNum: ${packet.portNum}")
        val data = Data(
            portnum = protoPortNum,
            payload = ByteString.of(*packet.payload),
            want_response = packet.wantResponse,
            request_id = (packet.requestId and 0xFFFFFFFFL).toInt(),
            reply_id = (packet.replyId and 0xFFFFFFFFL).toInt(),
        )
        val priority = when {
            packet.portNum == PortNum.PORTNUM_ROUTING_APP -> ProtoMeshPacket.Priority.ACK
            packet.wantAck -> ProtoMeshPacket.Priority.RELIABLE
            else -> ProtoMeshPacket.Priority.UNSET
        }
        val protoPacket = ProtoMeshPacket(
            from = (packet.from and 0xFFFFFFFFL).toInt(),
            to = (packet.to and 0xFFFFFFFFL).toInt(),
            channel = packet.channel,
            decoded = data,
            id = (packet.id and 0xFFFFFFFFL).toInt(),
            hop_limit = packet.hopLimit.coerceIn(0, 7),
            want_ack = packet.wantAck,
            priority = priority,
        )
        return encodeToRadioStrict(ToRadio(packet = protoPacket), maxEnvelopeBytes)
    }

    fun decodeFromRadio(bytes: ByteArray): DecodedFromRadio? {
        if (bytes.isEmpty() || bytes.size > MAX_TO_FROM_RADIO_SIZE) return null
        val fromRadio = try {
            FromRadio.ADAPTER.decode(bytes)
        } catch (_: IOException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }

        fromRadio.my_info?.let { info ->
            return DecodedFromRadio.MyInfo(
                myNodeNum = info.my_node_num.toLong() and 0xFFFFFFFFL,
                rebootCount = info.reboot_count.toLong() and 0xFFFFFFFFL,
                nodedbCount = info.nodedb_count,
                pioEnv = info.pio_env.takeIf { it.isNotBlank() },
            )
        }

        fromRadio.metadata?.let { meta ->
            val hwModel = meta.hw_model
            val hwCode = hwModel.value.takeIf { hwModel != HardwareModel.UNSET }
            val hwName = formatHardwareModel(hwModel)
            return DecodedFromRadio.Metadata(
                firmwareVersion = meta.firmware_version.takeIf { it.isNotBlank() },
                hardwareModelCode = hwCode,
                hardwareModelName = hwName,
            )
        }

        fromRadio.config?.lora?.let { lora ->
            val region = lora.region
            return DecodedFromRadio.LoraRegion(
                regionCode = region.value,
                frequencyBand = formatRegionBand(region),
            )
        }

        fromRadio.node_info?.let { nodeInfo ->
            val nodeNum = nodeInfo.num.toLong() and 0xFFFFFFFFL
            if (nodeNum == 0L) return DecodedFromRadio.Ignored
            val pos = nodeInfo.position
            val hasValidCoords = hasValidCoordinates(pos)
            val shortName = nodeInfo.user?.short_name?.takeIf { it.isNotBlank() }
                ?: "!%04x".format(nodeNum and 0xFFFFL)
            val longName = nodeInfo.user?.long_name?.takeIf { it.isNotBlank() }
                ?: "Node !%08x".format(nodeNum)
            val lastHeardMs = if (nodeInfo.last_heard != 0) {
                (nodeInfo.last_heard.toLong() and 0xFFFFFFFFL) * 1000L
            } else {
                0L
            }
            val measuredSnr = if (nodeInfo.snr != 0f || (nodeInfo.last_heard != 0 && !nodeInfo.via_mqtt)) {
                nodeInfo.snr
            } else {
                null
            }
            return DecodedFromRadio.DiscoveredNode(
                MeshNodeInfo(
                    nodeNum = nodeNum,
                    user = longName,
                    callsign = shortName,
                    snr = measuredSnr,
                    rssi = null,
                    batteryLevel = nodeInfo.device_metrics?.battery_level,
                    lastHeardTimestampMs = lastHeardMs,
                    hopsAway = nodeInfo.hops_away,
                    latitude = if (hasValidCoords) pos!!.latitude_i!! * 1e-7 else null,
                    longitude = if (hasValidCoords) pos!!.longitude_i!! * 1e-7 else null,
                    altitudeMeters = if (hasValidCoords) pos!!.altitude else null,
                )
            )
        }

        fromRadio.queueStatus?.let { q ->
            return DecodedFromRadio.QueueAck(
                meshPacketId = q.mesh_packet_id.toLong() and 0xFFFFFFFFL,
                result = q.res,
                free = q.free,
                maxlen = q.maxlen,
            )
        }

        fromRadio.config_complete_id?.let { completedId ->
            return DecodedFromRadio.ConfigComplete(
                configCompleteId = completedId.toLong() and 0xFFFFFFFFL
            )
        }

        if (fromRadio.rebooted == true) {
            return DecodedFromRadio.Rebooted
        }

        fromRadio.packet?.let { protoPacket ->
            val decodedData = protoPacket.decoded ?: return DecodedFromRadio.Ignored
            val payloadBytes = decodedData.payload.toByteArray()
            val portNum = decodedData.portnum.value
            val routingError: Int? = if (decodedData.portnum == ProtoPortNum.ROUTING_APP) {
                if (payloadBytes.isEmpty()) {
                    null
                } else {
                    val routing = try {
                        ProtoRouting.ADAPTER.decode(payloadBytes)
                    } catch (_: IOException) {
                        return null
                    }
                    when {
                        routing.error_reason != null -> routing.error_reason.value
                        else -> null
                    }
                }
            } else {
                null
            }
            val measuredSnr = if (protoPacket.rx_snr != 0f || protoPacket.rx_rssi != null) {
                protoPacket.rx_snr
            } else {
                null
            }
            val hopStart = protoPacket.hop_start.takeIf { it > 0 }
            val packet = MeshPacket(
                from = protoPacket.from.toLong() and 0xFFFFFFFFL,
                to = protoPacket.to.toLong() and 0xFFFFFFFFL,
                portNum = portNum,
                payload = payloadBytes,
                id = protoPacket.id.toLong() and 0xFFFFFFFFL,
                wantAck = protoPacket.want_ack,
                hopLimit = protoPacket.hop_limit,
                channel = protoPacket.channel,
                wantResponse = decodedData.want_response,
                requestId = decodedData.request_id.toLong() and 0xFFFFFFFFL,
                replyId = decodedData.reply_id.toLong() and 0xFFFFFFFFL,
                routingError = routingError,
                rxSnr = measuredSnr,
                rxRssi = protoPacket.rx_rssi,
                hopStart = hopStart,
            )
            return DecodedFromRadio.InboundPacket(packet)
        }

        return DecodedFromRadio.Ignored
    }

    fun decodeUserPayload(payload: ByteArray): DecodedUser? {
        if (payload.isEmpty()) return null
        val user = try {
            User.ADAPTER.decode(payload)
        } catch (_: IOException) {
            return null
        }
        val shortName = user.short_name.takeIf { it.isNotBlank() }
        val longName = user.long_name.takeIf { it.isNotBlank() }
        if (shortName == null && longName == null) return null
        return DecodedUser(shortName = shortName, longName = longName)
    }

    fun decodePositionPayload(payload: ByteArray): DecodedPosition? {
        if (payload.isEmpty()) return null
        val pos = try {
            Position.ADAPTER.decode(payload)
        } catch (_: IOException) {
            return null
        }
        if (!hasValidCoordinates(pos)) return null
        return DecodedPosition(
            latitude = pos.latitude_i!! * 1e-7,
            longitude = pos.longitude_i!! * 1e-7,
            altitudeMeters = pos.altitude,
        )
    }

    fun decodeTelemetryPayload(payload: ByteArray): DecodedTelemetry? {
        if (payload.isEmpty()) return null
        val telemetry = try {
            Telemetry.ADAPTER.decode(payload)
        } catch (_: IOException) {
            return null
        }
        val battery = telemetry.device_metrics?.battery_level ?: return null
        return DecodedTelemetry(batteryLevel = battery)
    }

    private fun hasValidCoordinates(pos: Position?): Boolean {
        if (pos == null) return false
        val lat = pos.latitude_i ?: return false
        val lon = pos.longitude_i ?: return false
        if (lat == 0 && lon == 0 && pos.time == 0 && pos.altitude == null &&
            pos.location_source == Position.LocSource.LOC_UNSET
        ) {
            return false
        }
        val latDeg = lat * 1e-7
        val lonDeg = lon * 1e-7
        return latDeg in -90.0..90.0 && lonDeg in -180.0..180.0
    }

    private fun formatHardwareModel(model: HardwareModel): String? {
        if (model == HardwareModel.UNSET) return null
        return model.name.replace('_', ' ')
    }

    private fun formatRegionBand(region: Config.LoRaConfig.RegionCode): String? {
        return when (region) {
            Config.LoRaConfig.RegionCode.UNSET -> null
            Config.LoRaConfig.RegionCode.US -> "915 MHz US"
            Config.LoRaConfig.RegionCode.EU_433 -> "433 MHz EU"
            Config.LoRaConfig.RegionCode.EU_868 -> "868 MHz EU"
            Config.LoRaConfig.RegionCode.CN -> "470 MHz CN"
            Config.LoRaConfig.RegionCode.JP -> "920 MHz JP"
            Config.LoRaConfig.RegionCode.ANZ -> "915 MHz ANZ"
            Config.LoRaConfig.RegionCode.KR -> "920 MHz KR"
            Config.LoRaConfig.RegionCode.TW -> "920 MHz TW"
            Config.LoRaConfig.RegionCode.RU -> "868 MHz RU"
            Config.LoRaConfig.RegionCode.IN -> "865 MHz IN"
            Config.LoRaConfig.RegionCode.NZ_865 -> "865 MHz NZ"
            Config.LoRaConfig.RegionCode.TH -> "920 MHz TH"
            Config.LoRaConfig.RegionCode.LORA_24 -> "2.4 GHz"
            else -> region.name.replace('_', ' ')
        }
    }

    private fun encodeToRadioStrict(toRadio: ToRadio, maxBytes: Int): ByteArray {
        val bytes = ToRadio.ADAPTER.encode(toRadio)
        require(bytes.size <= maxBytes) {
            "ToRadio envelope size ${bytes.size} exceeds max $maxBytes bytes"
        }
        return bytes
    }

    data class DecodedUser(
        val shortName: String?,
        val longName: String?,
    )

    data class DecodedPosition(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Int?,
    )

    data class DecodedTelemetry(
        val batteryLevel: Int,
    )

    companion object {
        const val MAX_TO_FROM_RADIO_SIZE: Int = 512
        const val BROADCAST_NODE_NUM: Long = 0xFFFFFFFFL
    }
}
