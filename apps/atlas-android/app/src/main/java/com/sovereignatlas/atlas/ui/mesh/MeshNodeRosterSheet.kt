// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.mesh

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.android.settings.NetworkProfile
import com.sovereignatlas.atlas.tactical.mesh.MeshNodeInfo
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.tactical.mesh.RadioHardwareInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshNodeRosterSheet(
    visible: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    radioInfo: RadioHardwareInfo?,
    connectionState: RadioConnectionState,
    networkProfile: NetworkProfile,
    nodes: List<MeshNodeInfo>,
    onPingNode: (Long) -> Unit,
    onDirectTargetDrop: (MeshNodeInfo) -> Unit = {},
    onConnectRadio: () -> Unit = {},
) {
    if (!visible) return

    val cyanAccent = Color(0xFF39C5F3)
    val amberAccent = Color(0xFFF4B85C)
    val matteCharcoal = Color(0xFF10151C)
    val graphite = Color(0xFF1B2631)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = matteCharcoal,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LoRa Tactical Mesh Roster",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .background(
                            when (connectionState) {
                                RadioConnectionState.CONNECTED -> Color(0xFF1B5E20)
                                RadioConnectionState.STREAMING -> Color(0xFF0D47A1)
                                RadioConnectionState.CONNECTING,
                                RadioConnectionState.HANDSHAKING -> Color(0xFFE65100)
                                else -> Color(0xFF37474F)
                            },
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = connectionState.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = graphite),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = radioInfo?.radioModel ?: if (connectionState == RadioConnectionState.DISCONNECTED) {
                                "Meshtastic Radio (Not Connected)"
                            } else {
                                "Unknown Model"
                            },
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = radioInfo?.frequencyBand ?: "Region Unknown",
                            color = cyanAccent,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "NODE: ${radioInfo?.let { "!%08x".format(it.nodeNum and 0xFFFFFFFFL) } ?: "Unknown"} • FW: ${radioInfo?.firmwareVersion ?: "Unknown"}",
                        color = Color(0xFFA8B5C1),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isEmcon = networkProfile == NetworkProfile.RADIO_SILENCE
                        val isCloudOnly = networkProfile == NetworkProfile.CLOUD_ONLY
                        val isLinkReady = connectionState == RadioConnectionState.CONNECTED ||
                            connectionState == RadioConnectionState.STREAMING
                        val profileColor = if (isEmcon || isCloudOnly) amberAccent else cyanAccent
                        Text(
                            text = when {
                                isEmcon -> "RADIO SILENCE (EMCON) ACTIVE • TX INHIBITED"
                                isCloudOnly -> "CLOUD-ONLY PROFILE ACTIVE • MESH TX DISABLED"
                                connectionState == RadioConnectionState.HANDSHAKING -> "HANDSHAKING • AWAITING CONFIG COMPLETE"
                                isLinkReady -> "MESH TRANSMIT READY"
                                else -> "MESH LINK NOT READY"
                            },
                            color = profileColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (connectionState == RadioConnectionState.DISCONNECTED || connectionState == RadioConnectionState.ERROR) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onConnectRadio,
                            colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Connect Meshtastic Radio", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "DISCOVERED PEER NODES (${nodes.size})",
                color = Color(0xFFA8B5C1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (nodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No LoRa mesh nodes heard yet.\nListening for RF packets...",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(nodes, key = { it.nodeNum }) { node ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = graphite),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = node.callsign.ifBlank { node.user },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = node.nodeIdHex,
                                        color = cyanAccent,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    val bat = node.batteryLevel
                                    val snrText = node.snr?.let { "${if (it >= 0f) "+" else ""}$it dB" } ?: "Unknown"
                                    val rssiText = node.rssi?.let { "$it dBm" } ?: "Unknown"
                                    val batText = bat?.let { "$it%" } ?: "Unknown"
                                    val batColor = when {
                                        bat == null -> Color(0xFFA8B5C1)
                                        bat > 20 -> Color(0xFF4CAF50)
                                        else -> amberAccent
                                    }
                                    Text(
                                        text = "SNR: $snrText • RSSI: $rssiText",
                                        color = Color(0xFFA8B5C1),
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        text = "BAT: $batText",
                                        color = batColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onPingNode(node.nodeNum) },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("Ping", fontSize = 12.sp, color = cyanAccent)
                                    }
                                    if (node.latitude != null && node.longitude != null) {
                                        Button(
                                            onClick = { onDirectTargetDrop(node) },
                                            colors = ButtonDefaults.buttonColors(containerColor = cyanAccent),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Drop Target", fontSize = 12.sp, color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
