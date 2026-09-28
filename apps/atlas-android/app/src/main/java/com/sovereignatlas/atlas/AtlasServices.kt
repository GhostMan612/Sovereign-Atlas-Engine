// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import com.sovereignatlas.atlas.core.KeyProvider
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.field.WaypointRepository
import com.sovereignatlas.atlas.geo.ImageDecoder
import com.sovereignatlas.atlas.geo.LoSState
import com.sovereignatlas.atlas.android.settings.SettingsRepository
import com.sovereignatlas.atlas.geo.cot.MessageStore
import com.sovereignatlas.atlas.geo.cot.PliStore
import com.sovereignatlas.atlas.geo.location.LocationEngine
import com.sovereignatlas.atlas.geo.routing.RoutingState
import com.sovereignatlas.atlas.android.DemSession
import com.sovereignatlas.atlas.android.comms.AtakBroadcaster
import com.sovereignatlas.atlas.android.comms.AtakMulticastListener
import com.sovereignatlas.atlas.android.comms.MarkerStore
import com.sovereignatlas.atlas.geo.los.ElevationProvider
import com.sovereignatlas.atlas.geo.los.LineOfSightEngine
import com.sovereignatlas.atlas.offline.OfflineMapRepository
import com.sovereignatlas.atlas.track.TrackRepository
import com.sovereignatlas.atlas.track.TrackScrubState
import com.sovereignatlas.atlas.goto.GoToState
import com.sovereignatlas.atlas.heading.HeadingService
import com.sovereignatlas.atlas.location.LocationService
import com.sovereignatlas.atlas.map.MapBehavior
import com.sovereignatlas.atlas.measure.MeasureState
import com.sovereignatlas.atlas.offline.OfflineStore
import com.sovereignatlas.atlas.offline.PackTileServer
import com.sovereignatlas.atlas.track.TrackRecorder

class AtlasServices(
    val location: LocationService,
    val recorder: TrackRecorder,
    val goTo: GoToState,
    val measure: MeasureState,
    val behavior: MapBehavior,
    val offline: OfflineStore,
    val heading: HeadingService,
    val tiles: PackTileServer,
    val keys: KeyProvider,
    val maps: OfflineMapRepository,
    val database: AtlasDatabase,
    val waypointRepository: WaypointRepository,
    val trackRepository: TrackRepository,
    val imageDecoder: ImageDecoder,
    val scrubState: TrackScrubState,
    val losState: LoSState,
    val routing: RoutingState,
    val pli: PliStore,
    val messages: MessageStore,
    val markers: MarkerStore,
    val settingsRepository: SettingsRepository,
    val locationEngine: LocationEngine,
    val multicastListener: AtakMulticastListener,
    val atakBroadcaster: AtakBroadcaster,
) {
    val lineOfSightEngine = LineOfSightEngine(
        elevationProvider = object : ElevationProvider {
            override suspend fun getElevations(points: List<Pair<Double, Double>>): List<Double?> {
                val engine = DemSession.engine
                return engine?.getElevationsBatch(points) ?: List(points.size) { null }
            }
        },
    )
}
