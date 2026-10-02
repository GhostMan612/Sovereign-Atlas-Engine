// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.db.Waypoint
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the ADR-007 migration actually migrates an EXISTING database.
 *
 * This is the test ADR-007 exists to make mandatory, and it is deliberately not a
 * fresh-schema test. Every other test in this repository builds a brand-new
 * database via `Schema.create`, which is precisely why a schema change that breaks
 * every installed device would pass the host gate green: nothing here ever opens a
 * database created by the PREVIOUS version of the code.
 *
 * So this test builds the version-1 schema by hand, inserts a legacy row that has
 * no `sharingPolicy` column at all, then runs the real generated migration and
 * asserts the row survived with the restrictive default.
 */
final class WaypointMigrationTest {

    /** The version-1 Waypoint DDL, verbatim from the pre-migration `Atlas.sq`. */
    private val LEGACY_SCHEMA = """
        CREATE TABLE IF NOT EXISTS Waypoint (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            timestamp INTEGER NOT NULL,
            notes TEXT
        );
    """.trimIndent()

    private fun version1File(): Pair<SqlDriver, String> {
        val dbFile = File(Files.createTempDirectory("atlas-migration").toFile(), "atlas.db")
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        val driver: SqlDriver = JdbcSqliteDriver(url)
        // Create ONLY the legacy table. Calling Schema.create here would build the
        // current version and there would be nothing to migrate.
        driver.execute(null, LEGACY_SCHEMA, 0)
        // Stamp it as version 1, which is what AndroidSqliteDriver reads to decide
        // a migration is owed. Without this the migrate call below is skipped.
        driver.execute(null, "PRAGMA user_version = 1;", 0)
        return driver to url
    }

    @Test
    fun aVersion1DatabaseHasNoSharingPolicyColumn() {
        // Proves the fixture is genuinely pre-migration. Without this, every other
        // assertion below could pass against a table that already had the column,
        // and the test would be proving nothing.
        val (driver, _) = version1File()
        try {
            val columns = driver.executeQuery(
                null,
                "SELECT name FROM pragma_table_info('Waypoint');",
                { cursor ->
                    app.cash.sqldelight.db.QueryResult.Value(
                        buildList {
                            while (cursor.next().value) {
                                add(cursor.getString(0)!!)
                            }
                        },
                    )
                },
                0,
            ).value

            assertTrue(
                "the legacy fixture must contain its expected columns (had $columns)",
                columns.containsAll(listOf("id", "name", "latitude")),
            )
            assertTrue(
                "the legacy table must not already have sharingPolicy (had $columns)",
                !columns.contains("sharingPolicy"),
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun migratingFrom1To2PreservesRowsAndDefaultsToPrivate() {
        val (driver, _) = version1File()
        try {
            // A legacy row, inserted through the PRE-migration column list only.
            driver.execute(
                null,
                """
                INSERT INTO Waypoint (id, name, latitude, longitude, timestamp, notes)
                VALUES ('legacy-1', 'Old Cache', 44.9, -93.1, 1000, 'pre-upgrade note');
                """.trimIndent(),
                0,
            )

            // The real generated migration.
            AtlasDatabase.Schema.migrate(driver, 1, 2)

            val migrated = AtlasDatabase(driver)
            val rows = migrated.atlasQueries.selectAllWaypoints().executeAsList()

            assertEquals("the legacy row must survive", 1, rows.size)
            val row = rows.first()
            assertEquals("legacy-1", row.id)
            assertEquals("Old Cache", row.name)
            assertEquals("pre-upgrade note", row.notes)
            assertEquals(44.9, row.latitude, 1e-9)
            assertEquals(-93.1, row.longitude, 1e-9)
            assertEquals(1000L, row.timestamp)

            // The whole point of the default: Phase 10 §10.6 requires EXPLICIT
            // OPT-IN, so an upgrade must not make a pre-existing waypoint shareable.
            assertEquals(
                "an upgraded waypoint must default to PRIVATE, not to a permissive policy",
                WaypointSharingPolicy.Private.storedValue,
                row.sharingPolicy,
            )
            assertEquals(
                WaypointSharingPolicy.Private,
                WaypointSharingPolicy.fromStored(row.sharingPolicy),
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun aMigratedDatabaseAcceptsNewWrites() {
        // A migration that lands the column but leaves the generated INSERT broken
        // would pass the assertions above. This proves the row is writable after.
        val (driver, _) = version1File()
        try {
            driver.execute(
                null,
                """
                INSERT INTO Waypoint (id, name, latitude, longitude, timestamp, notes)
                VALUES ('legacy-2', 'Another', 45.0, -93.0, 2000, NULL);
                """.trimIndent(),
                0,
            )
            AtlasDatabase.Schema.migrate(driver, 1, 2)

            val repository = WaypointRepository(AtlasDatabase(driver))
            runBlocking {
                repository.saveWaypoint(
                    Waypoint(
                        id = "post-migration",
                        name = "Written After Upgrade",
                        latitude = 46.0,
                        longitude = -94.0,
                        timestamp = 3000L,
                        notes = null,
                        sharingPolicy = WaypointSharingPolicy.Team.storedValue,
                    ),
                )
                val rows = withTimeout(10_000L) {
                    repository.waypoints.first { it.size >= 2 }
                }
                val fresh = rows.first { it.id == "post-migration" }
                assertEquals(
                    WaypointSharingPolicy.Team,
                    WaypointSharingPolicy.fromStored(fresh.sharingPolicy),
                )
                assertNotNull(rows.firstOrNull { it.id == "legacy-2" })
            }
        } finally {
            driver.close()
        }
    }

    @Test
    fun migratingTwiceIsHarmless() {
        // A device that was interrupted mid-upgrade may re-enter create(). The
        // migration must not throw when the column already exists.
        val (driver, _) = version1File()
        try {
            driver.execute(
                null,
                "INSERT INTO Waypoint (id, name, latitude, longitude, timestamp, notes) " +
                    "VALUES ('legacy-3', 'Idempotent', 47.0, -95.0, 4000, NULL);",
                0,
            )
            AtlasDatabase.Schema.migrate(driver, 1, 2)
            // Second pass: the column exists now. Creating the schema over the top
            // must be a no-op rather than an error, because AppServices calls
            // Schema.create on every launch.
            AtlasDatabase.Schema.create(driver)

            val rows = AtlasDatabase(driver).atlasQueries.selectAllWaypoints().executeAsList()
            assertEquals(1, rows.size)
            assertEquals(
                WaypointSharingPolicy.Private.storedValue,
                rows.first().sharingPolicy,
            )
        } finally {
            driver.close()
        }
    }
}
