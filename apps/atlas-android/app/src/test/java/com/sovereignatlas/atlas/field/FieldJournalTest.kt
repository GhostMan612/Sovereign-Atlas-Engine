// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.location.AtlasLocationFix
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

final class FieldJournalTest {
    private fun journal(dir: File): FieldJournal {
        return FieldJournal(directoryProvider = { dir })
    }

    private fun tempDir(): File {
        return Files.createTempDirectory("atlas-journal-test").toFile()
    }

    private fun fix(latitude: Double, longitude: Double, atMs: Long): AtlasLocationFix {
        return AtlasLocationFix(
            position = AtlasCoordinate(latitude = latitude, longitude = longitude),
            atMs = atMs,
            source = "gps",
        )
    }

    @Test
    fun createAssignsMonotonicIdsAndPersists() {
        val dir = tempDir()
        val journal = journal(dir)
        val first = journal.create(45.0, -93.0, label = "Alpha")
        val second = journal.create(46.0, -94.0)
        assertEquals("wp-000001", first.id)
        assertEquals("wp-000002", second.id)
        assertTrue(File(dir, "field_journal/journal.json").exists())
        val reloaded = journal(dir)
        reloaded.restore()
        assertEquals(listOf("wp-000001", "wp-000002"), reloaded.waypoints().map { it.id })
        assertEquals("Alpha", reloaded.lookup("wp-000001")?.label)
    }

    @Test
    fun deletedIdsAreNeverReused() {
        val dir = tempDir()
        val journal = journal(dir)
        journal.create(1.0, 1.0)
        journal.create(2.0, 2.0)
        assertTrue(journal.remove("wp-000002"))
        val third = journal.create(3.0, 3.0)
        assertEquals("wp-000003", third.id)
        val reloaded = journal(dir)
        reloaded.restore()
        assertEquals(listOf("wp-000001", "wp-000003"), reloaded.waypoints().map { it.id })
    }

    @Test
    fun updateLabelAndNote() {
        val dir = tempDir()
        val journal = journal(dir)
        journal.create(1.0, 1.0, label = "A")
        assertTrue(journal.updateLabel("wp-000001", "B"))
        assertTrue(journal.updateNote("wp-000001", "note"))
        assertFalse(journal.updateLabel("wp-missing", "X"))
        assertFalse(journal.updateNote("wp-missing", "X"))
        val reloaded = journal(dir)
        reloaded.restore()
        assertEquals("B", reloaded.lookup("wp-000001")?.label)
        assertEquals("note", reloaded.lookup("wp-000001")?.note)
    }

    @Test
    fun removeMissingReturnsFalse() {
        val journal = journal(tempDir())
        assertFalse(journal.remove("wp-000001"))
    }

    @Test
    fun saveTrackBuildsPoints() {
        val dir = tempDir()
        val journal = journal(dir)
        assertNull(journal.saveTrack(emptyList()))
        val track = journal.saveTrack(
            listOf(fix(10.0, 20.0, 1000L), fix(11.0, 21.0, 2000L)),
        )
        assertNotNull(track)
        assertEquals("trk-000001", track?.id)
        assertEquals(1000L, track?.createdAt)
        assertEquals(2, track?.pointCount)
        assertEquals("trk-000001-p0001", track?.points?.get(0)?.id)
        assertEquals("trk-000001-p0002", track?.points?.get(1)?.id)
        assertEquals(WaypointSource.gpsRecorded, track?.source)
        val reloaded = journal(dir)
        reloaded.restore()
        assertEquals(2, reloaded.lookupTrack("trk-000001")?.pointCount)
    }

    @Test
    fun corruptFileSurfacesErrorAndKeepsMemory() {
        val dir = tempDir()
        val journal = journal(dir)
        journal.create(1.0, 1.0)
        File(dir, "field_journal/journal.json").writeText("{not json")
        journal.restore()
        assertNotNull(journal.lastErrorOrNull())
        assertEquals(listOf("wp-000001"), journal.waypoints().map { it.id })
    }

    @Test
    fun versionMismatchSurfacesError() {
        val dir = tempDir()
        val fileDir = File(dir, "field_journal")
        fileDir.mkdirs()
        File(fileDir, "journal.json").writeText(
            """{"version":2,"waypoints":[],"tracks":[]}""",
        )
        val journal = journal(dir)
        journal.restore()
        assertNotNull(journal.lastErrorOrNull())
        assertTrue(journal.waypoints().isEmpty())
    }

    @Test
    fun flutterWrittenEnvelopeReadsWithSkipAndDup() {
        val dir = tempDir()
        val fileDir = File(dir, "field_journal")
        fileDir.mkdirs()
        File(fileDir, "journal.json").writeText(
            """{"version":1,"waypoints":[""" +
                """{"id":"wp-000001","latitude":45.0,"longitude":-93.0,"created_at":1000,"label":"A","note":"","source":"map_selected"},""" +
                """{"id":"wp-000001","latitude":0.0,"longitude":0.0,"created_at":1001,"label":"dup","note":"","source":"gps_recorded"},""" +
                """{"id":"","latitude":0.0,"longitude":0.0,"created_at":1002,"label":"bad","note":"","source":"map_selected"},""" +
                """{"id":"wp-000002","latitude":91.0,"longitude":0.0,"created_at":1003,"label":"badlat","note":"","source":"map_selected"}""" +
                """],"tracks":[]}""",
        )
        val journal = journal(dir)
        journal.restore()
        assertNull(journal.lastErrorOrNull())
        assertEquals(listOf("wp-000001"), journal.waypoints().map { it.id })
        assertEquals("A", journal.lookup("wp-000001")?.label)
        assertEquals(3, journal.skippedCount())
    }

    @Test
    fun kotlinWrittenEnvelopeKeepsFlutterShape() {
        val dir = tempDir()
        val journal = journal(dir)
        journal.create(45.0, -93.0, label = "A", source = WaypointSource.gpsRecorded)
        val text = File(dir, "field_journal/journal.json").readText()
        assertTrue(text.contains("\"version\":1"))
        assertTrue(text.contains("\"waypoints\":[{"))
        assertTrue(text.contains("\"tracks\":[]"))
        assertTrue(text.contains("\"created_at\":"))
        assertTrue(text.contains("\"source\":\"gps_recorded\""))
    }

    @Test
    fun invalidCoordinateRejected() {
        val journal = journal(tempDir())
        try {
            journal.create(91.0, 0.0)
            throw AssertionError("latitude 91 must be rejected")
        } catch (error: Exception) {
            assertTrue(journal.waypoints().isEmpty())
        }
    }
}
