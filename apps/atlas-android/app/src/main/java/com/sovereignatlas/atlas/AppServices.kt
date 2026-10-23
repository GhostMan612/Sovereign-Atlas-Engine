// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.sovereignatlas.atlas.android.AndroidImageDecoder
import com.sovereignatlas.atlas.android.comms.AtakBroadcaster
import com.sovereignatlas.atlas.android.comms.AtakMulticastListener
import com.sovereignatlas.atlas.android.comms.AtakPayloadParser
import com.sovereignatlas.atlas.geo.cot.CotMessageRouter
import com.sovereignatlas.atlas.geo.cot.LegacyCotReader
import com.sovereignatlas.atlas.geo.cot.MarkerStore
import com.sovereignatlas.atlas.android.data.HISTORICAL_ASSET_DIR
import com.sovereignatlas.atlas.android.data.LocalHistoricalAssetRepository
import com.sovereignatlas.atlas.android.location.AndroidLocationEngine
import com.sovereignatlas.atlas.android.map.mbtiles.AndroidMetadataReader
import com.sovereignatlas.atlas.android.map.mbtiles.MbtilesCache
import com.sovereignatlas.atlas.android.settings.SettingsRepository
import com.sovereignatlas.atlas.android.sync.FirestoreSyncProvider
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.field.WaypointRepository
import com.sovereignatlas.atlas.geo.LoSState
import com.sovereignatlas.atlas.geo.gis.GisRepository
import com.sovereignatlas.atlas.geo.gis.InMemoryGisRepository
import com.sovereignatlas.atlas.geo.viewshed.ViewshedState
import com.sovereignatlas.atlas.geo.cot.MessageStore
import com.sovereignatlas.atlas.geo.cot.PliStore
import com.sovereignatlas.atlas.geo.cot.SyncProvider
import com.sovereignatlas.atlas.geo.location.LocationEngine
import com.sovereignatlas.atlas.geo.routing.RoutingState
import com.sovereignatlas.atlas.offline.OfflineStore
import com.sovereignatlas.atlas.offline.PACK_JOURNAL_DIR
import com.sovereignatlas.atlas.android.offline.PackTileServer
import com.sovereignatlas.atlas.offline.LocalTileServer
import com.sovereignatlas.atlas.offline.mbtiles.DefaultMbtilesScanner
import com.sovereignatlas.atlas.offline.mbtiles.MbtilesScanner
import com.sovereignatlas.atlas.track.TrackRepository
import com.sovereignatlas.atlas.track.TrackScrubState
import com.sovereignatlas.atlas.android.navigation.AndroidRoadGraphLoader
import com.sovereignatlas.atlas.android.navigation.AndroidVoiceGuidance
import com.sovereignatlas.atlas.core.knowledge.ConfidenceScore
import com.sovereignatlas.atlas.core.knowledge.EntityType
import com.sovereignatlas.atlas.core.knowledge.HistoricalDossier
import com.sovereignatlas.atlas.core.knowledge.HistoricalKnowledgeIndex
import com.sovereignatlas.atlas.core.knowledge.KnowledgeBundleSerializer
import com.sovereignatlas.atlas.core.knowledge.SourceCitation
import com.sovereignatlas.atlas.core.knowledge.TemporalInterval
import com.sovereignatlas.atlas.geo.navigation.RoadNavigationState
import com.sovereignatlas.atlas.geo.navigation.RoadRoutingEngine
import com.sovereignatlas.atlas.geo.navigation.VoiceGuidance
import com.sovereignatlas.atlas.layers.compositor.LayerAttribution
import com.sovereignatlas.atlas.layers.compositor.LayerCompositor
import com.sovereignatlas.atlas.layers.compositor.LayerDescriptor
import com.sovereignatlas.atlas.layers.compositor.LayerType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppServices(private val context: Context) {
    private val appContext: Context get() = context.applicationContext

    val localDeviceUid: String =
        android.provider.Settings.Secure.getString(
            appContext.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ) ?: UUID.randomUUID().toString()

    val offline = OfflineStore(directoryProvider = { appContext.filesDir })

    // The composition root is the ONE place that names PackTileServer. Everything
    // downstream sees LocalTileServer (ADR-006), so Phase 2 can relocate the
    // socket implementation to android/ without touching a single caller.
    val tiles: LocalTileServer = PackTileServer(
        packsDir = { File(appContext.filesDir, PACK_JOURNAL_DIR) },
    ).also { server ->
        // Single root, on purpose: MbtilesCache's containment guard is what stops a
        // pack name from walking out of the journal. Historical Sanborn packs live in
        // external storage and are read by MapLibre's own mbtiles:// scheme, not
        // through this server.
        server.mbtilesStore = MbtilesCache(File(appContext.filesDir, PACK_JOURNAL_DIR))
    }

    val mbtilesScanner: MbtilesScanner = DefaultMbtilesScanner(
        packsDir = { File(appContext.filesDir, PACK_JOURNAL_DIR) },
        metadataReader = AndroidMetadataReader(),
    )

    val keys = AndroidKeyProvider(appContext)

    val maps = AndroidOfflineMapRepository(appContext)

    val database: AtlasDatabase = run {
        val driver = AndroidSqliteDriver(AtlasDatabase.Schema, appContext, "atlas.db")
        AtlasDatabase.Schema.create(driver)
        AtlasDatabase(driver)
    }

    val waypointRepository = WaypointRepository(database)
    val trackRepository = TrackRepository(database)
    val imageDecoder = AndroidImageDecoder()
    val scrubState = TrackScrubState()
    val losState = LoSState()
    val routing = RoutingState()
    val voiceGuidance: VoiceGuidance = AndroidVoiceGuidance(appContext)
    val roadNavigation: RoadNavigationState = RoadNavigationState()
    val roadRoutingEngine: RoadRoutingEngine = RoadRoutingEngine(
        AndroidRoadGraphLoader.loadSynchronousOrEmpty(appContext, allowDemoSimulation = false),
    )
    val gisRepository: GisRepository = InMemoryGisRepository()
    val viewshedState: ViewshedState = ViewshedState()

    val compositor: LayerCompositor = LayerCompositor().apply {
        registerLayer(
            LayerDescriptor(
                id = "blend-usgs-hillshade",
                title = "USGS 3DEP Shaded Relief",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.40f,
                defaultZIndex = 2,
                attribution = LayerAttribution("USGS", "Public Domain", "USGS 3DEP Elevation Program"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-usgs-hydrography",
                title = "USGS NHD Hydrography",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.75f,
                defaultZIndex = 3,
                attribution = LayerAttribution("USGS", "Public Domain", "USGS National Hydrography Dataset"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-usgs-topo",
                title = "USGS Topo Overlay",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.45f,
                defaultZIndex = 4,
                attribution = LayerAttribution("USGS", "Public Domain", "USGS The National Map"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-usgs-imagery-topo",
                title = "USGS Imagery + Topo Overlay",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.45f,
                defaultZIndex = 5,
                attribution = LayerAttribution("USGS", "Public Domain", "USGS The National Map (NAIP + Topo)"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-opentopomap",
                title = "OpenTopoMap Overlay",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.45f,
                defaultZIndex = 6,
                attribution = LayerAttribution("OpenTopoMap", "CC-BY-SA", "© OpenStreetMap contributors, SRTM | style: © OpenTopoMap (CC-BY-SA)"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-esri-imagery",
                title = "Esri World Imagery Overlay",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.45f,
                defaultZIndex = 7,
                attribution = LayerAttribution("Esri", "Esri Terms of Use", "Esri, Maxar, Earthstar Geographics, and the GIS User Community"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-esri-light-gray",
                title = "Esri Light Gray Canvas Overlay",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.45f,
                defaultZIndex = 7,
                attribution = LayerAttribution("Esri", "Esri Terms of Use", "Esri, HERE, Garmin, © OpenStreetMap contributors"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-esri-dark-gray",
                title = "Esri Dark Gray Canvas Overlay",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.45f,
                defaultZIndex = 7,
                attribution = LayerAttribution("Esri", "Esri Terms of Use", "Esri, HERE, Garmin, © OpenStreetMap contributors"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-osm-standard",
                title = "OpenStreetMap Overlay",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.40f,
                defaultZIndex = 8,
                attribution = LayerAttribution("OpenStreetMap", "ODbL", "© OpenStreetMap contributors"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-carto-positron",
                title = "CARTO Positron Overlay",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.40f,
                defaultZIndex = 9,
                attribution = LayerAttribution("CARTO", "CC-BY-SA", "© OpenStreetMap contributors © CARTO"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-carto-dark-matter",
                title = "CARTO Dark Matter Overlay",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.40f,
                defaultZIndex = 9,
                attribution = LayerAttribution("CARTO", "CC-BY-SA", "© OpenStreetMap contributors © CARTO"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "blend-openseamap-nautical",
                title = "OpenSeaMap Nautical Marks",
                type = LayerType.RASTER_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.85f,
                defaultZIndex = 10,
                attribution = LayerAttribution("OpenSeaMap", "CC-BY-SA", "© OpenSeaMap contributors"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "graticule",
                title = "Graticule",
                type = LayerType.VECTOR_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.8f,
                defaultZIndex = 15,
                attribution = LayerAttribution("USGS", "Public Domain", "USGS Graticule"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "mgrs",
                title = "MGRS Grid",
                type = LayerType.VECTOR_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.8f,
                defaultZIndex = 20,
                attribution = LayerAttribution("NGA", "Public Domain", "NGA MGRS Grid"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "rings",
                title = "Range Rings",
                type = LayerType.VECTOR_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 0.9f,
                defaultZIndex = 25,
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "historical-dossiers",
                title = "Historical Dossier Pins",
                type = LayerType.HISTORICAL_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 1.0f,
                defaultZIndex = 32,
                attribution = LayerAttribution("NPS / HABS", "Public Domain", "National Park Service / HABS / GGNRA"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "historical-patents",
                title = "BLM Land Patent Parcels",
                type = LayerType.HISTORICAL_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 0.70f,
                defaultZIndex = 34,
                attribution = LayerAttribution("BLM GLO", "Public Domain", "Bureau of Land Management GLO"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "historical-blueprints",
                title = "Sanborn Fire Insurance Blueprints",
                type = LayerType.HISTORICAL_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 0.85f,
                defaultZIndex = 36,
                attribution = LayerAttribution("Library of Congress", "Public Domain", "Library of Congress Sanborn Maps"),
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "waypoints",
                title = "Waypoints",
                type = LayerType.GEOJSON_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 1.0f,
                defaultZIndex = 40,
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "fence",
                title = "Radial Geofence",
                type = LayerType.GEOJSON_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 0.9f,
                defaultZIndex = 48,
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "track",
                title = "Track",
                type = LayerType.GEOJSON_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 1.0f,
                defaultZIndex = 50,
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "ops-graphics",
                title = "Operational Graphics (Zones/Lines)",
                type = LayerType.GEOJSON_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 0.9f,
                defaultZIndex = 52,
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "cot-markers",
                title = "CoT Tactical Markers",
                type = LayerType.GEOJSON_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 1.0f,
                defaultZIndex = 55,
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "pli-mesh",
                title = "Blue-Force PLI & Mesh Tracks",
                type = LayerType.GEOJSON_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 1.0f,
                defaultZIndex = 58,
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "measure",
                title = "Measure",
                type = LayerType.GEOJSON_OVERLAY,
                defaultVisibility = false,
                defaultOpacity = 1.0f,
                defaultZIndex = 60,
            ),
        )
        registerLayer(
            LayerDescriptor(
                id = "route",
                title = "Road Navigation Route",
                type = LayerType.GEOJSON_OVERLAY,
                defaultVisibility = true,
                defaultOpacity = 1.0f,
                defaultZIndex = 70,
                attribution = LayerAttribution("OpenStreetMap", "ODbL", "© OpenStreetMap contributors"),
            ),
        )
    }

    val historicalIndex: HistoricalKnowledgeIndex = HistoricalKnowledgeIndex().apply {
        insert(
            HistoricalDossier(
                id = "hd-001",
                name = "Fort Point Garrison",
                entityType = EntityType.STRUCTURE,
                temporal = TemporalInterval(1853, 1914),
                latitude = 37.8106,
                longitude = -122.4770,
                description = "Brick masonry coastal fortification built by the US Army Corps of Engineers prior to the Civil War to defend San Francisco Bay.",
                confidence = ConfidenceScore.CONFIRMED,
                primaryCitation = SourceCitation(
                    citationId = "NPS-FP-1853",
                    title = "Historic American Buildings Survey: Fort Point",
                    authorOrArchive = "National Park Service / Library of Congress",
                    publicationYear = 1934,
                    urlOrShelfmark = "HABS CA-1230",
                    license = "Public Domain",
                ),
                tags = listOf("military", "fortification", "masonry", "harbor"),
            ),
        )
        insert(
            HistoricalDossier(
                id = "hd-002",
                name = "Portsmouth Plaza Boundary Survey",
                entityType = EntityType.PARCEL,
                temporal = TemporalInterval(1847, 1906),
                latitude = 37.7946,
                longitude = -122.4050,
                description = "Public square of Yerba Buena originally laid out by Mexican authorities and surveyed by Jasper O'Farrell in 1847.",
                confidence = ConfidenceScore.CONFIRMED,
                primaryCitation = SourceCitation(
                    citationId = "BLM-CA-YP1847",
                    title = "Official Survey Map of the Town of San Francisco",
                    authorOrArchive = "Jasper O'Farrell / Bureau of Land Management",
                    publicationYear = 1847,
                    urlOrShelfmark = "SF-ARCH-0042",
                    license = "Public Domain",
                ),
                tags = listOf("survey", "plaza", "alta-california", "municipal"),
            ),
        )
        KnowledgeBundleSerializer.loadVerifiedDirectory(File(appContext.filesDir, "knowledge"), this)
        KnowledgeBundleSerializer.loadVerifiedDirectory(appContext.getExternalFilesDir("knowledge"), this)
    }

    val historicalAssets = LocalHistoricalAssetRepository(
        directoryProvider = { appContext.getExternalFilesDir(HISTORICAL_ASSET_DIR) },
        metadataReader = AndroidMetadataReader(),
    )

    val pliStore = PliStore(localDeviceUid = localDeviceUid, ttlMillis = 15 * 60 * 1000L)

    val messageStore = MessageStore(localDeviceUid = localDeviceUid)

    private val markerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val markerStore = MarkerStore(markerScope)

    // Composition root for CoT ingest. The router is pure and owns every routing
    // rule; the only thing the Android tier supplies is the escape hatch for wire
    // protobuf and GeoChat detail, neither of which a pure string reader can
    // decode. That keeps the decision provable on the JVM while the Android tier
    // keeps the ATAK codec.
    val cotRouter = CotMessageRouter(
        pliStore = pliStore,
        markerStore = markerStore,
        messageStore = messageStore,
        legacyReader = LegacyCotReader { packetData -> AtakPayloadParser().parse(packetData) },
    )

    val multicastListener = AtakMulticastListener(
        context = appContext,
        router = cotRouter,
    )

    val syncProvider: SyncProvider = FirestoreSyncProvider(
        localDeviceUid = localDeviceUid,
        callsignProvider = {
            appContext.getSharedPreferences("atlas_prefs", Context.MODE_PRIVATE)
                .getString("PREF_CALLSIGN", "User-${localDeviceUid.takeLast(6)}")
                ?: "User-${localDeviceUid.takeLast(6)}"
        },
    )

    val settingsRepository = SettingsRepository(appContext)

    val atakBroadcaster = AtakBroadcaster(
        listener = multicastListener,
        localUid = localDeviceUid,
        settingsRepository = settingsRepository,
        syncProvider = syncProvider,
    )

    init {
        val savedCompositor = settingsRepository.compositorSnapshot.value
        if (savedCompositor.isNotBlank()) {
            compositor.restoreStateSnapshot(savedCompositor)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            syncProvider.ensureAuthenticated()
        }
    }

    val locationEngine: LocationEngine = AndroidLocationEngine(
        context = appContext,
        database = database,
    )

    fun start() {
        offline.restore()
        tiles.start()
    }
}
