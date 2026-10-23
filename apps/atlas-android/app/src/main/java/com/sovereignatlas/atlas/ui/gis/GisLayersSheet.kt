// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui.gis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereignatlas.atlas.geo.gis.GisDataset
import com.sovereignatlas.atlas.geo.gis.GisStyle

private val CharcoalDark = Color(0xFF10151C)
private val GraphiteCard = Color(0xFF1B2631)
private val SurfaceDark = Color(0xFF141C24)
private val ChromeBorder = Color(0x33A8B5C1)
private val NavCyan = Color(0xFF39C5F3)
private val WarningAmber = Color(0xFFF59E0B)
private val MutedText = Color(0xFFA8B5C1)

private val PaletteColors = listOf(
    "#39C5F3" to "Cyan",
    "#F59E0B" to "Amber",
    "#10B981" to "Emerald",
    "#8B5CF6" to "Purple",
    "#F43F5E" to "Rose",
    "#F8FAFC" to "White",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisLayersSheet(
    datasets: List<GisDataset>,
    onDismiss: () -> Unit,
    onImportClick: () -> Unit,
    onToggleVisibility: (String, Boolean) -> Unit,
    onDeleteDataset: (String) -> Unit,
    onUpdateStyle: (String, GisStyle) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        text = "FIELD GIS LAYERS",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        text = "OGC GeoPackage, KML/KMZ, Shapefile, GeoJSON",
                        color = MutedText,
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

            Button(
                onClick = onImportClick,
                colors = ButtonDefaults.buttonColors(containerColor = NavCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = CharcoalDark,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "IMPORT FIELD GIS DATASET",
                    color = CharcoalDark,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (datasets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "NO FIELD GIS DATASETS LOADED\nTap above to import files from external storage",
                        color = MutedText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(datasets, key = { it.id }) { dataset ->
                        GisDatasetCard(
                            dataset = dataset,
                            onToggleVisibility = { isVis -> onToggleVisibility(dataset.id, isVis) },
                            onDelete = { onDeleteDataset(dataset.id) },
                            onStyleChange = { newStyle -> onUpdateStyle(dataset.id, newStyle) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GisDatasetCard(
    dataset: GisDataset,
    onToggleVisibility: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onStyleChange: (GisStyle) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2A3B4C))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = dataset.format.displayName,
                                color = NavCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF222D39))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = dataset.srid.displayName,
                                color = WarningAmber,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = dataset.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = "${dataset.featureCount} features • Tap to customize style",
                        color = MutedText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = dataset.isVisible,
                        onCheckedChange = onToggleVisibility,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CharcoalDark,
                            checkedTrackColor = NavCyan,
                            uncheckedThumbColor = MutedText,
                            uncheckedTrackColor = SurfaceDark,
                        ),
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ChromeBorder),
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "PALETTE COLOR",
                    color = MutedText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for ((hex, _) in PaletteColors) {
                        val isSelected = dataset.style.strokeColorHex.equals(hex, ignoreCase = true)
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    val alphaHex = "33" + hex.removePrefix("#")
                                    onStyleChange(
                                        dataset.style.copy(
                                            strokeColorHex = hex,
                                            fillColorHex = "#$alphaHex",
                                            pointColorHex = hex,
                                        ),
                                    )
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "LINE WIDTH: ${dataset.style.strokeWidthDp.toInt()} DP",
                    color = MutedText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Slider(
                    value = dataset.style.strokeWidthDp,
                    onValueChange = { onStyleChange(dataset.style.copy(strokeWidthDp = it)) },
                    valueRange = 1f..6f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = NavCyan,
                        activeTrackColor = NavCyan,
                        inactiveTrackColor = SurfaceDark,
                    ),
                )
            }
        }
    }
}
