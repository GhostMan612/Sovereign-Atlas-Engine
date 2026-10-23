// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.gis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.gis.GisFeature
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import java.util.Locale

private val CharcoalDark = Color(0xFF10151C)
private val GraphiteCard = Color(0xFF1B2631)
private val ChromeBorder = Color(0x33A8B5C1)
private val NavCyan = Color(0xFF39C5F3)
private val MutedText = Color(0xFFA8B5C1)
private val WarningAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisFeatureInspectorSheet(
    feature: GisFeature,
    datasetName: String,
    onDismiss: () -> Unit,
    onNavigateTo: (AtlasCoordinate) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val firstCoord = feature.geometry.allCoordinates().firstOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalDark,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "GIS FEATURE INSPECTOR",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        text = "Layer: $datasetName",
                        color = NavCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MutedText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = GraphiteCard),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ChromeBorder, RoundedCornerShape(8.dp)),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "FEATURE ID",
                            color = MutedText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2A3B4C))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = when (feature.geometry) {
                                    is RenderGeometry.Point -> "POINT"
                                    is RenderGeometry.LineString -> "LINE"
                                    is RenderGeometry.Polygon -> "POLYGON"
                                    is RenderGeometry.MultiPolygon -> "MULTIPOLYGON"
                                },
                                color = WarningAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = feature.id,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (firstCoord != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "LAT: %.5f°  LON: %.5f°",
                                firstCoord.latitude,
                                firstCoord.longitude,
                            ),
                            color = NavCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            if (firstCoord != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        onNavigateTo(AtlasCoordinate(firstCoord.latitude, firstCoord.longitude))
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = CharcoalDark,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SET AS GOTO TARGET",
                        color = CharcoalDark,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ATTRIBUTES & METADATA",
                color = MutedText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (feature.properties.isEmpty()) {
                Text(
                    text = "No extended attributes present on this feature.",
                    color = MutedText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(feature.properties.toList(), key = { it.first }) { (key, prop) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(GraphiteCard)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = key,
                                color = MutedText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = when (prop) {
                                    is RenderProperty.Text -> prop.value
                                    is RenderProperty.Number -> String.format(Locale.US, "%.3f", prop.value)
                                    is RenderProperty.Flag -> prop.value.toString().uppercase()
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}
