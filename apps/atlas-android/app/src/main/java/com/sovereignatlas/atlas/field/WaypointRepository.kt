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
            sharingPolicy = waypoint.sharingPolicy,
        )
    }

    /**
     * The stored sharing policy for [id], or null when no such waypoint exists.
     *
     * Reads through [WaypointSharingPolicy.fromStored] rather than exposing the raw
     * column, so a corrupt value resolves to [WaypointSharingPolicy.Private] instead
     * of reaching a caller as an unparseable string.
     */
    suspend fun sharingPolicyFor(id: String): WaypointSharingPolicy? =
        withContext(ioDispatcher) {
            db.atlasQueries.selectAllWaypoints().executeAsList()
                .firstOrNull { it.id == id }
                ?.let { WaypointSharingPolicy.fromStored(it.sharingPolicy) }
        }

    /**
     * Changes a waypoint's sharing policy in place.
     *
     * Reads the row, rewrites the column, and writes it back. A dedicated UPDATE
     * query would be one fewer round trip, but the row has to be re-read anyway to
     * preserve every other column, and a re-read-and-write that silently drops a
     * column added by a later migration is exactly the class of bug ADR-007 exists
     * to prevent.
     */
    suspend fun setSharingPolicy(id: String, policy: WaypointSharingPolicy) =
        withContext(ioDispatcher) {
            val existing = db.atlasQueries.selectAllWaypoints().executeAsList()
                .firstOrNull { it.id == id } ?: return@withContext
            db.atlasQueries.insertWaypoint(
                id = existing.id,
                name = existing.name,
                latitude = existing.latitude,
                longitude = existing.longitude,
                timestamp = existing.timestamp,
                notes = existing.notes,
                sharingPolicy = policy.storedValue,
            )
        }

    suspend fun deleteWaypoint(id: String) = withContext(ioDispatcher) {
        db.atlasQueries.deleteWaypoint(id)
    }
}
