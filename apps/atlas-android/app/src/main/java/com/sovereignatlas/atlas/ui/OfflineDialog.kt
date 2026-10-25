// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sovereignatlas.atlas.R
import com.sovereignatlas.atlas.core.MapNameFormatter
import com.sovereignatlas.atlas.android.offline.AndroidHttpDownloader
import com.sovereignatlas.atlas.core.KeyProvider
import com.sovereignatlas.atlas.ui.hud.AtlasMotionMath
import com.sovereignatlas.atlas.offline.DownloadResult
import com.sovereignatlas.atlas.offline.KEY_PLACEHOLDER
import com.sovereignatlas.atlas.offline.withApiKey
import com.sovereignatlas.atlas.offline.OfflineBuiltinProviders
import com.sovereignatlas.atlas.offline.OfflineDownloader
import com.sovereignatlas.atlas.offline.OfflineMapDownloader
import com.sovereignatlas.atlas.offline.OfflineMapKind
import com.sovereignatlas.atlas.offline.OfflineMapRepository
import com.sovereignatlas.atlas.offline.OfflinePackLifecycle
import com.sovereignatlas.atlas.offline.OfflinePackRecord
import com.sovereignatlas.atlas.offline.OfflineStore
import com.sovereignatlas.atlas.offline.PlanOutcome
import com.sovereignatlas.atlas.offline.formatBytes
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CatalogResource(
    val name: String,
    val category: String,
    val description: String,
    val subsystem: String,
    val onDeviceLocation: String,
    val url: String,
)

private val CATALOG_RESOURCES = listOf(
    // Vector & Raster Basemaps
    CatalogResource(
        name = "Protomaps Extracts",
        category = "Vector Basemaps",
        description = "Free custom OpenStreetMap extracts for any bounding box worldwide in PMTiles & MBTiles format.",
        subsystem = "Vector Basemap & Overlays",
        onDeviceLocation = "/Android/data/.../files/mbtiles/",
        url = "https://protomaps.com/extracts",
    ),
    CatalogResource(
        name = "OpenMapTiles",
        category = "Vector Basemaps",
        description = "Pre-rendered city, country, and global vector tiles using the open Mapbox Vector Tile schema.",
        subsystem = "Vector Basemap",
        onDeviceLocation = "/Android/data/.../files/mbtiles/",
        url = "https://openmaptiles.org/downloads/",
    ),
    CatalogResource(
        name = "Geofabrik OSM Extracts",
        category = "Raw GIS & Routing",
        description = "Daily regional OpenStreetMap raw (.osm.pbf) data extracts covering every continent and country.",
        subsystem = "Road Network Compiler & GIS",
        onDeviceLocation = "/Android/data/.../files/navigation/roads.pack",
        url = "https://download.geofabrik.de/",
    ),
    CatalogResource(
        name = "Tilemaker Tool",
        category = "MBTiles Generator",
        description = "High-performance CLI tool to generate vector MBTiles from .osm.pbf files with zero database dependencies.",
        subsystem = "Workstation Converter",
        onDeviceLocation = "Compile on PC -> Push to mbtiles/",
        url = "https://tilemaker.org/",
    ),
    CatalogResource(
        name = "Tippecanoe (Felt)",
        category = "Vector Tile Compiler",
        description = "Mapbox tool to build scale-independent vector MBTiles from GeoJSON, Shapefiles, and spatial geometries.",
        subsystem = "Workstation Converter",
        onDeviceLocation = "Compile on PC -> Push to mbtiles/",
        url = "https://github.com/felt/tippecanoe",
    ),

    // Elevation & Viewshed
    CatalogResource(
        name = "USGS 3DEP Elevation",
        category = "LiDAR & DEM",
        description = "1m, 3m LiDAR point clouds and 10m/30m National Elevation Dataset DEMs covering the United States.",
        subsystem = "2.5D Hillshading & Dynamic Viewshed",
        onDeviceLocation = "/Android/data/.../files/mbtiles/*_dem.mbtiles",
        url = "https://apps.nationalmap.gov/downloader/",
    ),
    CatalogResource(
        name = "AWS Terrain-RGB (Mapzen)",
        category = "Global Terrain-RGB",
        description = "Global 30m Copernicus and SRTM elevation tiles pre-encoded in Mapbox Terrain-RGB formula.",
        subsystem = "Terrain DEM & Line-of-Sight",
        onDeviceLocation = "/Android/data/.../files/mbtiles/*_dem.mbtiles",
        url = "https://registry.opendata.aws/terrain-tiles/",
    ),
    CatalogResource(
        name = "OpenDEM",
        category = "Global Elevation",
        description = "Worldwide open digital elevation models, shaded relief hillshades, and topographic contours.",
        subsystem = "Terrain Elevation & Contours",
        onDeviceLocation = "/Android/data/.../files/mbtiles/*_dem.mbtiles",
        url = "https://www.opendem.info/",
    ),

    // Historical Archival & Blueprints
    CatalogResource(
        name = "Library of Congress Sanborn Maps",
        category = "Historical Blueprints",
        description = "Over 12,000 American towns georeferenced fire insurance blueprints detailing structural building footprints.",
        subsystem = "Sanborn Historical Blueprint Layer",
        onDeviceLocation = "/Android/data/.../files/mbtiles/",
        url = "https://www.loc.gov/collections/sanborn-maps/",
    ),
    CatalogResource(
        name = "USGS Historical Topographic Maps (HTMC)",
        category = "Historical Quad Maps",
        description = "Over 178,000 archival US topographic quadrangle maps spanning 1884 through 2006.",
        subsystem = "Historical Topo Overlays",
        onDeviceLocation = "/Android/data/.../files/mbtiles/",
        url = "https://www.usgs.gov/programs/national-geospatial-program/historical-topographic-maps-preserving-past",
    ),
    CatalogResource(
        name = "BLM General Land Office Records",
        category = "Land Patents & Surveys",
        description = "Access official federal land titles, homestead patents, survey plats, and township/range section lines.",
        subsystem = "BLM Land Patent Layer",
        onDeviceLocation = "Field GIS Import (.geojson)",
        url = "https://glorecords.blm.gov/",
    ),
    CatalogResource(
        name = "David Rumsey Map Collection",
        category = "Archival Cartography",
        description = "Premier digital collection of 130,000+ georeferenced historical maps from 1500 to present.",
        subsystem = "Historical Dossiers & Archival Overlays",
        onDeviceLocation = "/Android/data/.../files/knowledge/",
        url = "https://www.davidrumsey.com/",
    ),
    CatalogResource(
        name = "National Archives (NARA) Maps",
        category = "Military & Boundary Cartography",
        description = "Over 15 million maps and battlefield charts documenting civil defense, military campaigns, and frontiers.",
        subsystem = "Historical Research Dossiers",
        onDeviceLocation = "/Android/data/.../files/knowledge/",
        url = "https://www.archives.gov/research/military/maps",
    ),

    // Field GIS
    CatalogResource(
        name = "USGS National Hydrography Dataset",
        category = "Hydrography Vectors",
        description = "High-resolution river flowlines, watershed basins, lakes, canals, and stream gauge networks.",
        subsystem = "Field GIS (Shapefile / GeoPackage)",
        onDeviceLocation = "Command Center -> Field GIS -> Import",
        url = "https://www.usgs.gov/national-hydrography",
    ),
    CatalogResource(
        name = "Natural Earth Data",
        category = "Physical & Cultural GIS",
        description = "Public domain vector and raster map datasets at 1:10m, 1:50m, and 1:110m scales for world mapping.",
        subsystem = "Field GIS (Shapefile / GeoPackage)",
        onDeviceLocation = "Command Center -> Field GIS -> Import",
        url = "https://www.naturalearthdata.com/",
    ),
    CatalogResource(
        name = "NOAA Nautical Charts",
        category = "Maritime Navigation",
        description = "Official US electronic navigational charts (ENC) and bathymetric depth sounding charts.",
        subsystem = "Field GIS / Nautical Overlays",
        onDeviceLocation = "Field GIS Import",
        url = "https://charts.noaa.gov/",
    ),
    CatalogResource(
        name = "OpenSeaMap",
        category = "Maritime Hydrography",
        description = "Crowdsourced nautical charting with beacons, buoys, harbor information, and navigation warnings.",
        subsystem = "Marine Layers & Field GIS",
        onDeviceLocation = "Field GIS / Online Tile Layer",
        url = "https://map.openseamap.org/",
    ),
)

@Composable
fun OfflineDialog(
    store: OfflineStore,
    maps: OfflineMapRepository,
    tileHits: () -> Long,
    basemapHits: () -> Long,
    demHits: () -> Long,
    keyProvider: KeyProvider,
    onUsePack: (String) -> Unit,
    initialTab: Int = 0,
    onClose: () -> Unit,
) {
    val tab = remember { mutableStateOf(initialTab) }
    val cancels = remember { mutableMapOf<String, AtomicBoolean>() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val cartoKey = keyProvider.cartoKey
    AlertDialog(
        onDismissRequest = {
            cancels.values.forEach { it.set(true) }
            onClose()
        },
        title = { Text("Offline Areas & Catalogs") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    TextButton(onClick = { tab.value = 0 }) { Text("Packs") }
                    TextButton(onClick = { tab.value = 1 }) { Text("Plan") }
                    TextButton(onClick = { tab.value = 2 }) { Text("Providers") }
                    TextButton(onClick = { tab.value = 3 }) { Text("Diag") }
                    TextButton(onClick = { tab.value = 4 }) { Text("Maps") }
                    TextButton(onClick = { tab.value = 5 }) { Text("Catalogs") }
                }
                when (tab.value) {
                    0 -> PacksTab(store, cancels, mainHandler, cartoKey, onUsePack)
                    1 -> PlanTab(store)
                    2 -> ProvidersTab()
                    3 -> DiagnosticsTab(store, tileHits, basemapHits, demHits)
                    4 -> MapsTab(maps, onExploreCatalogs = { tab.value = 5 }, onClose = onClose)
                    else -> CatalogsTab()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun PacksTab(
    store: OfflineStore,
    cancels: MutableMap<String, AtomicBoolean>,
    mainHandler: Handler,
    cartoKey: StateFlow<String?>,
    onUsePack: (String) -> Unit,
) {
    val packs = store.packs()
    // Read inside the click, not here. A download started from a tab composed
    // before the key was saved must still use the key stored at the moment of the
    // click, so the flow is captured and `.value` is read on demand.
    val error = store.lastErrorOrNull()
    if (error != null) Text("Store unavailable: $error")
    var totalBytes = 0L
    for (pack in packs) totalBytes += pack.receivedBytes
    Text("Packs: ${packs.size} • Stored: ${formatBytes(totalBytes)}")
    if (packs.isEmpty()) {
        Text("No packs yet. Use Plan to schedule one.")
    } else {
        LazyColumn {
            items(packs, key = { it.packId }) { pack ->
                ListItem(
                    headlineContent = {
                        Text("${pack.packId} • ${pack.providerTitle}")
                    },
                    supportingContent = {
                        Column {
                            Text(
                                "${pack.lifecycle.name} • " +
                                    "${pack.receivedTiles}/${pack.tileCount} tiles • " +
                                    formatBytes(pack.receivedBytes) +
                                    if (pack.failureDetail.isNotEmpty()) {
                                        " • ${pack.failureDetail}"
                                    } else {
                                        ""
                                    },
                            )
                            if (pack.lifecycle == OfflinePackLifecycle.downloading) {
                                LinearProgressIndicator(
                                    progress = {
                                        AtlasMotionMath.packDownloadFraction(
                                            pack.receivedTiles,
                                            pack.tileCount,
                                        )
                                    },
                                    color = Color(0xFF39C5F3),
                                    trackColor = Color(0xFF1B2631),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                        .height(4.dp),
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Row {
                            if (pack.lifecycle == OfflinePackLifecycle.complete) {
                                TextButton(
                                    onClick = { onUsePack(pack.packId) },
                                ) {
                                    Text("Use")
                                }
                            }
                            if (pack.lifecycle == OfflinePackLifecycle.planned ||
                                pack.lifecycle == OfflinePackLifecycle.failed ||
                                pack.lifecycle == OfflinePackLifecycle.cancelled
                            ) {
                                TextButton(
                                    onClick = {
                                        startDownload(
                                            store,
                                            pack,
                                            cancels,
                                            mainHandler,
                                            cartoKey.value,
                                        )
                                    },
                                ) {
                                    Text("Get")
                                }
                            }
                            if (pack.lifecycle == OfflinePackLifecycle.downloading) {
                                TextButton(
                                    onClick = {
                                        cancels[pack.packId]?.set(true)
                                    },
                                ) {
                                    Text("Stop")
                                }
                            }
                            TextButton(
                                onClick = { store.removePack(pack.packId) },
                            ) {
                                Text("Del")
                            }
                        }
                    },
                )
            }
        }
    }
    val events = store.events().take(8)
    if (events.isNotEmpty()) {
        Text("Recent:")
        for (event in events) Text(event)
    }
}

private fun startDownload(
    store: OfflineStore,
    pack: OfflinePackRecord,
    cancels: MutableMap<String, AtomicBoolean>,
    mainHandler: Handler,
    cartoKey: String?,
) {
    val descriptor = OfflineBuiltinProviders.lookup(pack.providerId) ?: return
    // The credential is injected HERE, at the one place that can read the
    // encrypted key store. The pure descriptor registry never holds a key and the
    // pure tier never learns one exists; this is the boundary crossing, and it is
    // the same crossing AtlasMap.kt:2297 already makes for the live renderer.
    val provider = descriptor.withApiKey(cartoKey)
    if (cartoKey != null && provider === descriptor && descriptor.urlTemplate?.contains(KEY_PLACEHOLDER) == true) {
        store.log("no CARTO key stored; prefetch will be rejected by the provider")
    }
    val cancel = AtomicBoolean(false)
    cancels[pack.packId] = cancel
    pack.lifecycle = OfflinePackLifecycle.downloading
    store.notifyChanged()
    thread(isDaemon = true) {
        // Typed as the pure contract (ADR-006). The transport is supplied here, at the
        // one place that knows it is running on a device: AndroidHttpDownloader is
        // the entire java.net surface of the offline path. The composition root
        // still has no downloader binding - it is constructed at its single call
        // site - and that is unchanged by the relocation.
        val downloader: OfflineMapDownloader =
            OfflineDownloader(AndroidHttpDownloader::httpChunk)
        val result = downloader.download(
            record = pack,
            descriptor = provider,
            dir = store.packDir(pack.packId),
            onProgress = { _, _ ->
                mainHandler.post { store.notifyChanged() }
            },
            isCancelled = { cancel.get() },
        )
        when (result) {
            is DownloadResult.Complete -> store.log(
                "done ${pack.packId} ${result.tiles} tiles",
            )
            is DownloadResult.Cancelled -> store.log("cancel ${pack.packId}")
            is DownloadResult.Failed -> store.log(
                "fail ${pack.packId} ${result.detail}",
            )
        }
        store.persist()
        mainHandler.post { store.notifyChanged() }
    }
}

@Composable
private fun PlanTab(store: OfflineStore) {
    val providerId = remember { mutableStateOf("esri-imagery") }
    val expanded = remember { mutableStateOf(false) }
    val zMin = remember { mutableStateOf("10") }
    val zMax = remember { mutableStateOf("12") }
    val xMin = remember { mutableStateOf("1") }
    val xMax = remember { mutableStateOf("2") }
    val yMin = remember { mutableStateOf("2") }
    val yMax = remember { mutableStateOf("3") }
    val approvedBulk = remember { mutableStateOf(false) }
    val prefetch = remember { mutableStateOf(false) }
    val message = remember { mutableStateOf("") }
    val current = OfflineBuiltinProviders.lookup(providerId.value)
    Text(
        current?.title ?: providerId.value,
        modifier = Modifier.clickable { expanded.value = true },
    )
    DropdownMenu(
        expanded = expanded.value,
        onDismissRequest = { expanded.value = false },
    ) {
        for (provider in OfflineBuiltinProviders.all) {
            DropdownMenuItem(
                text = { Text(provider.title) },
                onClick = {
                    providerId.value = provider.id
                    expanded.value = false
                },
            )
        }
    }
    for (row in listOf("z" to (zMin to zMax), "x" to (xMin to xMax), "y" to (yMin to yMax))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${row.first} ")
            OutlinedTextField(
                value = row.second.first.value,
                onValueChange = { row.second.first.value = it.filter { c -> c.isDigit() } },
                label = { Text("min") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = row.second.second.value,
                onValueChange = { row.second.second.value = it.filter { c -> c.isDigit() } },
                label = { Text("max") },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = approvedBulk.value,
            onCheckedChange = { approvedBulk.value = it },
        )
        Text("Approve bulk")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = prefetch.value,
            onCheckedChange = { prefetch.value = it },
        )
        Text("Prefetch")
    }
    if (message.value.isNotEmpty()) Text(message.value)
    Button(
        onClick = {
            val outcome = store.plan(
                providerId = providerId.value,
                zMin = zMin.value.toIntOrNull() ?: 0,
                zMax = zMax.value.toIntOrNull() ?: 0,
                xMin = xMin.value.toIntOrNull() ?: 0,
                xMax = xMax.value.toIntOrNull() ?: 0,
                yMin = yMin.value.toIntOrNull() ?: 0,
                yMax = yMax.value.toIntOrNull() ?: 0,
                approvedBulk = approvedBulk.value,
                isPrefetch = prefetch.value,
            )
            message.value = when (outcome) {
                is PlanOutcome.Planned -> "Planned ${outcome.record.packId}."
                is PlanOutcome.Refused -> "Refused: ${outcome.reason}"
                is PlanOutcome.Blocked -> "Blocked: ${outcome.reason}"
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text("Plan pack")
    }
}

@Composable
private fun ProvidersTab() {    LazyColumn {
        items(OfflineBuiltinProviders.all, key = { it.id }) { provider ->
            ListItem(
                headlineContent = { Text(provider.title) },
                supportingContent = {
                    Text(
                        "${provider.attribution} • ${provider.license} • " +
                            "z${provider.minZoom}-${provider.maxZoom} • " +
                            if (provider.prefetchAllowed) {
                                "prefetch ok"
                            } else {
                                "approval required"
                            },
                    )
                },
            )
        }
    }
}

@Composable
private fun DiagnosticsTab(
    store: OfflineStore,
    tileHits: () -> Long,
    basemapHits: () -> Long,
    demHits: () -> Long,
) {
    val packs = store.packs()
    var tiles = 0
    var stored = 0L
    for (pack in packs) {
        tiles += pack.receivedTiles
        stored += pack.receivedBytes
    }
    val providers = OfflineBuiltinProviders.all
    val valid = providers.count { it.urlTemplate != null }
    Text("Sovereign Atlas native host v1.0")
    Text("Providers self-valid: $valid/${providers.size}")
    Text("Packs: ${packs.size} • Tiles stored: $tiles • Bytes: ${formatBytes(stored)}")
    Text("Local tile serves since launch: ${tileHits()}")
    Text("Basemap serves: ${basemapHits()} • DEM serves: ${demHits()}")
    val error = store.lastErrorOrNull()
    if (error != null) Text("Store unavailable: $error")
    LazyColumn {
        items(store.events(), key = { it }) { event ->
            Text(event)
        }
    }
}

@Composable
private fun MapsTab(
    maps: OfflineMapRepository,
    onExploreCatalogs: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available by maps.availableMaps.collectAsState()
    val active by maps.activeMap.collectAsState()
    val activeDem by maps.activeDem.collectAsState()
    LaunchedEffect(Unit) {
        maps.scanForMaps()
    }
    if (available.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tool_offline),
                contentDescription = "No offline maps",
                tint = Color(0xFF39C5F3),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "NO OFFLINE MAPS FOUND",
                color = Color(0xFFE2E8F0),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Push .mbtiles files via ADB/USB to:",
                color = Color(0xFFA8B5C1),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "/Android/data/${context.packageName}/files/mbtiles/",
                color = Color(0xFF6B7D8D),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onExploreCatalogs,
                border = BorderStroke(1.dp, Color(0x8039C5F3)),
            ) {
                Text(
                    text = "Browse Data Catalogs & Sources",
                    color = Color(0xFF39C5F3),
                )
            }
        }
        return
    }
    LazyColumn {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${available.size} offline map(s) found",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFA8B5C1),
                )
                TextButton(onClick = onExploreCatalogs) {
                    Text("Get More", color = Color(0xFF39C5F3))
                }
            }
        }
        items(available, key = { it.absolutePath }) { map ->
            val isDem = map.kind == OfflineMapKind.DEM
            ListItem(
                headlineContent = { Text(MapNameFormatter.format(map.name)) },
                supportingContent = {
                    Text(
                        android.text.format.Formatter.formatShortFileSize(context, map.sizeBytes) +
                            (if (isDem) " • DEM relief" else "") +
                            if (map.absolutePath == active?.absolutePath) " • active" else "",
                    )
                },
                trailingContent = {
                    if (isDem) {
                        val demActive = map.absolutePath == activeDem?.absolutePath
                        TextButton(
                            onClick = {
                                scope.launch {
                                    maps.setActiveDem(if (demActive) null else map)
                                }
                            },
                        ) {
                            Text(if (demActive) "Relief on" else "Relief")
                        }
                    }
                },
                modifier = Modifier.clickable {
                    if (!isDem) {
                        scope.launch {
                            maps.setActiveMap(map)
                            onClose()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CatalogsTab() {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = "Authoritative Subsystem Data Sources",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF39C5F3),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tap 'Visit Portal' to open the provider's website. Downloaded .mbtiles, .pack, and GIS datasets can be pushed to their target folder or imported on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA8B5C1),
                )
            }
        }
        items(CATALOG_RESOURCES, key = { it.name }) { item ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0x8010151C),
                border = BorderStroke(1.dp, Color(0x26A8B5C1)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0x3339C5F3),
                            border = BorderStroke(1.dp, Color(0x6639C5F3)),
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF39C5F3),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Subsystem: ${item.subsystem}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                    )
                    Text(
                        text = "Target: ${item.onDeviceLocation}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        },
                        border = BorderStroke(1.dp, Color(0x6639C5F3)),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            text = "Visit Portal",
                            color = Color(0xFF39C5F3),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}
