// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

import com.sovereignatlas.atlas.core.AtlasContractException
import com.sovereignatlas.atlas.core.AtlasIds
import com.sovereignatlas.atlas.core.AtlasRejection
import com.sovereignatlas.atlas.geo.AtlasCoordinates

enum class WaypointSource { mapSelected, gpsRecorded }

fun waypointSourceName(source: WaypointSource): String {
    return when (source) {
        WaypointSource.mapSelected -> "map_selected"
        WaypointSource.gpsRecorded -> "gps_recorded"
    }
}

fun waypointSourceFromName(raw: Any?): WaypointSource? {
    return when (raw) {
        "map_selected" -> WaypointSource.mapSelected
        "gps_recorded" -> WaypointSource.gpsRecorded
        else -> null
    }
}

data class StoredWaypoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long,
    val label: String = "",
    val note: String = "",
    val source: WaypointSource,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "latitude" to latitude,
        "longitude" to longitude,
        "created_at" to createdAt,
        "label" to label,
        "note" to note,
        "source" to waypointSourceName(source),
    )

    companion object {
        fun tryParse(raw: Any?): StoredWaypoint? {
            if (raw !is Map<*, *>) return null
            val id = raw["id"]
            if (id !is String || !AtlasIds.check(id).isValid) return null
            val latitude = (raw["latitude"] as? Number)?.toDouble()
            val longitude = (raw["longitude"] as? Number)?.toDouble()
            if (latitude == null || longitude == null) return null
            if (!AtlasCoordinates.validate(latitude, longitude).isValid) return null
            val createdAt = raw["created_at"]
            if (createdAt !is Int && createdAt !is Long) return null
            val label = raw["label"]
            val note = raw["note"]
            val source = waypointSourceFromName(raw["source"]) ?: return null
            return StoredWaypoint(
                id = id,
                latitude = latitude,
                longitude = longitude,
                createdAt = (createdAt as Number).toLong(),
                label = if (label is String) label else "",
                note = if (note is String) note else "",
                source = source,
            )
        }
    }
}

data class StoredTrack(
    val id: String,
    val createdAt: Long,
    val points: List<StoredWaypoint> = emptyList(),
    val source: WaypointSource,
) {
    val pointCount: Int get() = points.size

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "created_at" to createdAt,
        "source" to waypointSourceName(source),
        "points" to points.map { it.toMap() },
    )

    companion object {
        fun tryParse(raw: Any?): StoredTrack? {
            if (raw !is Map<*, *>) return null
            val id = raw["id"]
            if (id !is String || !AtlasIds.check(id).isValid) return null
            val createdAt = raw["created_at"]
            if (createdAt !is Int && createdAt !is Long) return null
            val source = waypointSourceFromName(raw["source"]) ?: return null
            val rawPoints = raw["points"]
            if (rawPoints !is List<*>) return null
            val points = ArrayList<StoredWaypoint>(rawPoints.size)
            for (entry in rawPoints) {
                points.add(StoredWaypoint.tryParse(entry) ?: return null)
            }
            return StoredTrack(
                id = id,
                createdAt = (createdAt as Number).toLong(),
                points = points,
                source = source,
            )
        }
    }
}

data class JournalData(
    val waypoints: Map<String, StoredWaypoint>,
    val tracks: Map<String, StoredTrack>,
    val skipped: Int,
    val waypointSequence: Int,
    val trackSequence: Int,
)

object JournalCodec {
    const val VERSION = 1
    const val JOURNAL_DIR = "field_journal"
    const val JOURNAL_FILE = "journal.json"

    fun waypointIdFor(sequence: Int): String =
        "wp-" + sequence.toString().padStart(6, '0')

    fun trackIdFor(sequence: Int): String =
        "trk-" + sequence.toString().padStart(6, '0')

    fun waypointSequenceOf(id: String): Int {
        if (!id.startsWith("wp-")) return 0
        return id.substring(3).toIntOrNull() ?: 0
    }

    fun trackSequenceOf(id: String): Int {
        if (!id.startsWith("trk-")) return 0
        return id.substring(4).toIntOrNull() ?: 0
    }

    fun envelope(
        waypoints: Collection<StoredWaypoint>,
        tracks: Collection<StoredTrack>,
    ): Map<String, Any?> = mapOf(
        "version" to VERSION,
        "waypoints" to waypoints.map { it.toMap() },
        "tracks" to tracks.map { it.toMap() },
    )

    fun parseEnvelope(decoded: Any?): JournalData {
        val version = (decoded as? Map<*, *>)?.get("version") as? Number
        if (decoded !is Map<*, *> || version?.toDouble() != VERSION.toDouble()) {
            throw AtlasContractException(
                AtlasRejection(
                    "UNSUPPORTED_JOURNAL",
                    "unsupported field journal version",
                ),
            )
        }
        val rawWaypoints = decoded["waypoints"]
        if (rawWaypoints !is List<*>) {
            throw AtlasContractException(
                AtlasRejection(
                    "JOURNAL_NO_WAYPOINTS",
                    "field journal has no waypoint list",
                ),
            )
        }
        val loaded = LinkedHashMap<String, StoredWaypoint>()
        var skipped = 0
        var maxSequence = 0
        for (raw in rawWaypoints) {
            val record = StoredWaypoint.tryParse(raw)
            if (record == null || loaded.containsKey(record.id)) {
                skipped += 1
                continue
            }
            loaded[record.id] = record
            val sequence = waypointSequenceOf(record.id)
            if (sequence > maxSequence) maxSequence = sequence
        }
        val loadedTracks = LinkedHashMap<String, StoredTrack>()
        var maxTrackSequence = 0
        val rawTracks = decoded["tracks"]
        if (rawTracks != null) {
            if (rawTracks !is List<*>) {
                throw AtlasContractException(
                    AtlasRejection(
                        "JOURNAL_NO_TRACKS",
                        "field journal has no track list",
                    ),
                )
            }
            for (raw in rawTracks) {
                val record = StoredTrack.tryParse(raw)
                if (record == null || loadedTracks.containsKey(record.id)) {
                    skipped += 1
                    continue
                }
                loadedTracks[record.id] = record
                val sequence = trackSequenceOf(record.id)
                if (sequence > maxTrackSequence) maxTrackSequence = sequence
            }
        }
        return JournalData(
            waypoints = loaded,
            tracks = loadedTracks,
            skipped = skipped,
            waypointSequence = maxSequence,
            trackSequence = maxTrackSequence,
        )
    }
}
