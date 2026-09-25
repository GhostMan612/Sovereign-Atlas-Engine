// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.db.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TrackRepository(
    private val db: AtlasDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val tracks: Flow<List<Track>> = db.atlasQueries.selectAllTracks()
        .asFlow()
        .mapToList(ioDispatcher)

    suspend fun saveTrack(track: Track) = withContext(ioDispatcher) {
        db.atlasQueries.insertTrack(
            id = track.id,
            name = track.name,
            timestamp = track.timestamp,
            distance_meters = track.distance_meters,
            geometry = track.geometry,
        )
    }

    suspend fun deleteTrack(id: String) = withContext(ioDispatcher) {
        db.atlasQueries.deleteTrack(id)
    }
}
