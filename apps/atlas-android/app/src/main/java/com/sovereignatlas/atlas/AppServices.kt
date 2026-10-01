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
import com.sovereignatlas.atlas.android.comms.MarkerStore
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
import com.sovereignatlas.atlas.geo.cot.MessageStore
import com.sovereignatlas.atlas.geo.cot.PliStore
import com.sovereignatlas.atlas.geo.cot.SyncProvider
import com.sovereignatlas.atlas.geo.location.LocationEngine
import com.sovereignatlas.atlas.geo.routing.RoutingState
import com.sovereignatlas.atlas.offline.OfflineStore
import com.sovereignatlas.atlas.offline.PACK_JOURNAL_DIR
import com.sovereignatlas.atlas.offline.LocalTileServer
import com.sovereignatlas.atlas.offline.PackTileServer
import com.sovereignatlas.atlas.offline.mbtiles.DefaultMbtilesScanner
import com.sovereignatlas.atlas.offline.mbtiles.MbtilesScanner
import com.sovereignatlas.atlas.track.TrackRepository
import com.sovereignatlas.atlas.track.TrackScrubState
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

    val historicalAssets = LocalHistoricalAssetRepository(
        directoryProvider = { appContext.getExternalFilesDir(HISTORICAL_ASSET_DIR) },
        metadataReader = AndroidMetadataReader(),
    )

    val pliStore = PliStore(localDeviceUid = localDeviceUid, ttlMillis = 15 * 60 * 1000L)

    val messageStore = MessageStore(localDeviceUid = localDeviceUid)

    private val markerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val markerStore = MarkerStore(markerScope)

    val multicastListener = AtakMulticastListener(
        context = appContext,
        pliStore = pliStore,
        messageStore = messageStore,
        markerStore = markerStore,
        parser = AtakPayloadParser(),
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
