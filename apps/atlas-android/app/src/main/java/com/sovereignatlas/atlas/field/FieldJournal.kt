// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

import com.sovereignatlas.atlas.core.AtlasContractException
import com.sovereignatlas.atlas.geo.AtlasCoordinates
import com.sovereignatlas.atlas.location.AtlasLocationFix
import java.io.File

class FieldJournal(
    private val directoryProvider: () -> File,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val waypoints = LinkedHashMap<String, StoredWaypoint>()
    private val tracks = LinkedHashMap<String, StoredTrack>()
    private var sequence = 0
    private var trackSequence = 0
    private var skipped = 0
    private var lastError: Throwable? = null
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun waypoints(): List<StoredWaypoint> = waypoints.values.toList()

    fun lookup(id: String): StoredWaypoint? = waypoints[id]

    fun tracks(): List<StoredTrack> = tracks.values.toList()

    fun lookupTrack(id: String): StoredTrack? = tracks[id]

    fun lastErrorOrNull(): Throwable? = lastError

    fun skippedCount(): Int = skipped

    fun create(
        latitude: Double,
        longitude: Double,
        label: String = "",
        note: String = "",
        source: WaypointSource = WaypointSource.mapSelected,
    ): StoredWaypoint {
        val point = AtlasCoordinates.checked(latitude, longitude)
        val id = allocateId()
        val record = StoredWaypoint(
            id = id,
            latitude = point.latitude,
            longitude = point.longitude,
            createdAt = clockMs(),
            label = label,
            note = note,
            source = source,
        )
        waypoints[id] = record
        lastError = null
        persist()
        notifyListeners()
        return record
    }

    fun updateLabel(id: String, label: String): Boolean {
        val current = waypoints[id] ?: return false
        waypoints[id] = current.copy(label = label)
        lastError = null
        persist()
        notifyListeners()
        return true
    }

    fun updateNote(id: String, note: String): Boolean {
        val current = waypoints[id] ?: return false
        waypoints[id] = current.copy(note = note)
        lastError = null
        persist()
        notifyListeners()
        return true
    }

    fun remove(id: String): Boolean {
        if (waypoints.remove(id) == null) return false
        lastError = null
        persist()
        notifyListeners()
        return true
    }

    fun saveTrack(fixes: List<AtlasLocationFix>): StoredTrack? {
        if (fixes.isEmpty()) return null
        val id = allocateTrackId()
        val points = fixes.mapIndexed { index, fix ->
            StoredWaypoint(
                id = "$id-p${(index + 1).toString().padStart(4, '0')}",
                latitude = fix.position.latitude,
                longitude = fix.position.longitude,
                createdAt = fix.atMs,
                source = WaypointSource.gpsRecorded,
            )
        }
        val record = StoredTrack(
            id = id,
            createdAt = fixes.first().atMs,
            points = points,
            source = WaypointSource.gpsRecorded,
        )
        tracks[id] = record
        lastError = null
        persist()
        notifyListeners()
        return record
    }

    fun removeTrack(id: String): Boolean {
        if (tracks.remove(id) == null) return false
        lastError = null
        persist()
        notifyListeners()
        return true
    }

    fun persist() {
        try {
            val dir = journalDir()
            File(dir, JournalCodec.JOURNAL_FILE).writeText(
                JournalJson.render(
                    JournalCodec.envelope(waypoints.values, tracks.values),
                ),
            )
        } catch (error: Throwable) {
            lastError = error
            notifyListeners()
        }
    }

    fun restore() {
        try {
            restoreUnsafe()
        } catch (error: Throwable) {
            lastError = error
            notifyListeners()
        }
    }

    private fun journalDir(): File {
        val dir = File(directoryProvider(), JournalCodec.JOURNAL_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun restoreUnsafe() {
        val file = File(journalDir(), JournalCodec.JOURNAL_FILE)
        if (!file.exists()) {
            lastError = null
            notifyListeners()
            return
        }
        val decoded = try {
            JournalJson.parse(file.readText())
        } catch (error: Throwable) {
            lastError = error
            notifyListeners()
            return
        }
        val data = try {
            JournalCodec.parseEnvelope(decoded)
        } catch (error: AtlasContractException) {
            lastError = IllegalStateException(error.rejection.message)
            notifyListeners()
            return
        }
        waypoints.clear()
        waypoints.putAll(data.waypoints)
        tracks.clear()
        tracks.putAll(data.tracks)
        sequence = data.waypointSequence
        trackSequence = data.trackSequence
        skipped = data.skipped
        lastError = null
        notifyListeners()
    }

    private fun allocateId(): String {
        var candidate = sequence + 1
        while (waypoints.containsKey(JournalCodec.waypointIdFor(candidate))) {
            candidate += 1
        }
        sequence = candidate
        return JournalCodec.waypointIdFor(candidate)
    }

    private fun allocateTrackId(): String {
        var candidate = trackSequence + 1
        while (tracks.containsKey(JournalCodec.trackIdFor(candidate))) {
            candidate += 1
        }
        trackSequence = candidate
        return JournalCodec.trackIdFor(candidate)
    }

    private fun notifyListeners() {
        for (listener in listeners.toList()) {
            listener()
        }
    }
}
