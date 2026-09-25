// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.db.Waypoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WaypointRepository(
    private val db: AtlasDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val waypoints: Flow<List<Waypoint>> = db.atlasQueries.selectAllWaypoints()
        .asFlow()
        .mapToList(ioDispatcher)

    suspend fun saveWaypoint(waypoint: Waypoint) = withContext(ioDispatcher) {
        db.atlasQueries.insertWaypoint(
            id = waypoint.id,
            name = waypoint.name,
            latitude = waypoint.latitude,
            longitude = waypoint.longitude,
            timestamp = waypoint.timestamp,
            notes = waypoint.notes,
        )
    }

    suspend fun deleteWaypoint(id: String) = withContext(ioDispatcher) {
        db.atlasQueries.deleteWaypoint(id)
    }
}
