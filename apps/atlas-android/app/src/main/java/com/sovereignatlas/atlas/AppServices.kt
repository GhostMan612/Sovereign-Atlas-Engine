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
import com.sovereignatlas.atlas.android.location.AndroidLocationEngine
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.field.WaypointRepository
import com.sovereignatlas.atlas.geo.LoSState
import com.sovereignatlas.atlas.geo.cot.MessageStore
import com.sovereignatlas.atlas.geo.cot.PliStore
import com.sovereignatlas.atlas.geo.location.LocationEngine
import com.sovereignatlas.atlas.geo.routing.RoutingState
import com.sovereignatlas.atlas.offline.OfflineStore
import com.sovereignatlas.atlas.offline.PACK_JOURNAL_DIR
import com.sovereignatlas.atlas.offline.PackTileServer
import com.sovereignatlas.atlas.track.TrackRepository
import com.sovereignatlas.atlas.track.TrackScrubState
import java.io.File
import java.util.UUID

class AppServices(private val context: Context) {
    private val appContext: Context get() = context.applicationContext

    val localDeviceUid: String =
        android.provider.Settings.Secure.getString(
            appContext.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ) ?: UUID.randomUUID().toString()

    val offline = OfflineStore(directoryProvider = { appContext.filesDir })

    val tiles = PackTileServer(packsDir = { File(appContext.filesDir, PACK_JOURNAL_DIR) })

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

    val pliStore = PliStore(localDeviceUid = localDeviceUid, ttlMillis = 15 * 60 * 1000L)

    val messageStore = MessageStore(localDeviceUid = localDeviceUid)

    val multicastListener = AtakMulticastListener(
        context = appContext,
        pliStore = pliStore,
        messageStore = messageStore,
        parser = AtakPayloadParser(),
    )

    val atakBroadcaster = AtakBroadcaster(
        listener = multicastListener,
        localUid = localDeviceUid,
        callsignProvider = {
            appContext.getSharedPreferences("atlas_prefs", Context.MODE_PRIVATE)
                .getString("PREF_CALLSIGN", "User-${localDeviceUid.takeLast(6)}")
                ?: "User-${localDeviceUid.takeLast(6)}"
        },
    )

    val locationEngine: LocationEngine = AndroidLocationEngine(
        context = appContext,
        database = database,
    )

    fun start() {
        offline.restore()
        tiles.start()
    }
}
