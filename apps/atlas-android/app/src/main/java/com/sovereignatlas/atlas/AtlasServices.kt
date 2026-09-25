// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas

import com.sovereignatlas.atlas.core.KeyProvider
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.field.WaypointRepository
import com.sovereignatlas.atlas.offline.OfflineMapRepository
import com.sovereignatlas.atlas.track.TrackRepository
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
)
