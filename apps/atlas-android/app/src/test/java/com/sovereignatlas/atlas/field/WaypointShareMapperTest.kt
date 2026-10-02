// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.field

import com.sovereignatlas.atlas.db.AtlasDatabase
import com.sovereignatlas.atlas.db.Waypoint
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sovereignatlas.atlas.geo.cot.CotRevocationDraft
import com.sovereignatlas.atlas.geo.cot.OutboundWaypointDecision
import com.sovereignatlas.atlas.geo.cot.RecipientScope
import com.sovereignatlas.atlas.geo.cot.ShareableWaypoint
import com.sovereignatlas.atlas.geo.cot.WaypointSharingRouter
import com.sovereignatlas.atlas.geo.cot.WithholdReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun inMemoryDatabase(): AtlasDatabase {
    val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    AtlasDatabase.Schema.create(driver)
    return AtlasDatabase(driver)
}

/**
 * The seam between the persistence row and the outbound decision.
 *
 * Lives in `field/` rather than `geo/` because it is a mapping between an adapter
 * type and a generated database row — the pure tier has no business knowing what a
 * SQLDelight row is, and putting this converter in `geo/` would reintroduce the
 * dependency that ADR-006 removed.
 *
 * If the conversion were done ad hoc at each call site, the failure mode would be a
 * waypoint read with its policy defaulted somewhere quiet, which is the same class
 * of bug as routing a hostile marker into the friendly store. One tested mapping
 * instead of several hopeful ones.
 */
object WaypointShareMapper {

    /**
     * A waypoint as the outbound router sees it.
     *
     * Reads the policy through [WaypointSharingPolicy.fromStored], so a corrupt
     * column becomes [WaypointSharingPolicy.Private] at the boundary. That is the
     * single most important line in this file: a raw pass-through would let an
     * unparseable value reach the router as a permissive-looking string.
     */
    fun toShareable(waypoint: Waypoint): ShareableWaypoint = ShareableWaypoint(
        id = waypoint.id,
        name = waypoint.name,
        latitude = waypoint.latitude,
        longitude = waypoint.longitude,
        timestampMillis = waypoint.timestamp,
        notes = waypoint.notes,
        policy = WaypointSharingPolicy.fromStored(waypoint.sharingPolicy),
    )

    /**
     * The scope this row may be transmitted at, or null when it may not.
     *
     * Convenience for a caller that only wants to know whether to transmit. Null
     * means "do not transmit", so it cannot be accidentally treated as a valid
     * scope by a `when` that forgets a branch.
     */
    fun scopeOf(waypoint: Waypoint): RecipientScope? =
        RecipientScope.forPolicy(WaypointSharingPolicy.fromStored(waypoint.sharingPolicy))

    /** Whether this row is permitted onto the mesh at all. */
    fun isShareable(waypoint: Waypoint): Boolean =
        WaypointSharingPolicy.fromStored(waypoint.sharingPolicy) != WaypointSharingPolicy.Private
}

private fun waypoint(
    id: String,
    policy: String,
    latitude: Double = 44.9,
    longitude: Double = -93.1,
) = Waypoint(
    id = id,
    name = "Bridge Crossing",
    latitude = latitude,
    longitude = longitude,
    timestamp = 1_700_000_000_000L,
    notes = "north abutment",
    sharingPolicy = policy,
)

final class WaypointShareMapperTest {

    @Test
    fun everyColumnIsCarriedAcross() {
        val mapped = WaypointShareMapper.toShareable(
            waypoint("wp-1", WaypointSharingPolicy.Team.storedValue),
        )

        assertEquals("wp-1", mapped.id)
        assertEquals("Bridge Crossing", mapped.name)
        assertEquals(44.9, mapped.latitude, 0.0)
        assertEquals(-93.1, mapped.longitude, 0.0)
        assertEquals(1_700_000_000_000L, mapped.timestampMillis)
        assertEquals("north abutment", mapped.notes)
        assertEquals(WaypointSharingPolicy.Team, mapped.policy)
    }

    @Test
    fun aPrivateRowMapsToThePrivatePolicy() {
        val mapped = WaypointShareMapper.toShareable(
            waypoint("wp-1", WaypointSharingPolicy.Private.storedValue),
        )

        assertEquals(WaypointSharingPolicy.Private, mapped.policy)
        assertTrue(!WaypointShareMapper.isShareable(
            waypoint("wp-1", WaypointSharingPolicy.Private.storedValue),
        ))
    }

    @Test
    fun aCorruptPolicyColumnMapsToPrivateAtTheBoundary() {
        // The whole reason fromStored exists, asserted at the seam where it matters.
        // A pass-through of the raw string would put "PUBLICC" in front of the
        // router looking, to any code that skipped the parse, like a public row.
        for (corrupt in listOf("", "   ", "PUBLICC", "team", "0", null)) {
            val mapped = WaypointShareMapper.toShareable(waypoint("wp-bad", corrupt ?: ""))

            assertEquals(
                "corrupt value '$corrupt' must map to PRIVATE",
                WaypointSharingPolicy.Private,
                mapped.policy,
            )
            assertEquals(null, WaypointShareMapper.scopeOf(waypoint("wp-bad", corrupt ?: "")))
        }
    }

    @Test
    fun scopeIsNullForAPrivateRow() {
        assertEquals(
            null,
            WaypointShareMapper.scopeOf(waypoint("wp-1", WaypointSharingPolicy.Private.storedValue)),
        )
    }

    @Test
    fun scopeFollowsTheStoredPolicy() {
        assertEquals(
            RecipientScope.TEAM,
            WaypointShareMapper.scopeOf(waypoint("wp-1", WaypointSharingPolicy.Team.storedValue)),
        )
        assertEquals(
            RecipientScope.PUBLIC,
            WaypointShareMapper.scopeOf(waypoint("wp-1", WaypointSharingPolicy.Public.storedValue)),
        )
    }

    @Test
    fun aMappedPrivateRowIsRefusedByTheRealRouter() {
        // End to end across the seam: a stored row, the mapper, the outbound router.
        val router = WaypointSharingRouter(
            serializer = { _, _ -> error("must not serialize a private waypoint") },
            revocationSerializer = CotRevocationDraft.asSerializer(),
        )
        val privateRow = waypoint("wp-live", WaypointSharingPolicy.Private.storedValue)

        val decision = router.route(WaypointShareMapper.toShareable(privateRow))

        assertEquals(OutboundWaypointDecision.Withhold(WithholdReason.PRIVATE_POLICY), decision)
    }

    @Test
    fun aMappedTeamRowIsPermittedByTheRealRouter() {
        val router = WaypointSharingRouter(
            serializer = { waypoint, scope ->
                "${waypoint.id}:$scope".toByteArray()
            },
            revocationSerializer = CotRevocationDraft.asSerializer(),
        )

        val decision = router.route(
            WaypointShareMapper.toShareable(waypoint("wp-live", WaypointSharingPolicy.Team.storedValue)),
        )

        assertTrue(decision is OutboundWaypointDecision.Permit)
        assertEquals(
            "wp-live:TEAM",
            (decision as OutboundWaypointDecision.Permit).payload.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun revertingARowThroughTheRepositoryMakesItUnshareable() {
        // The stop-sharing control's persistence half: after setSharingPolicy, a
        // re-read row must map to a scope of null. If this did not hold, the pure
        // transition would be correct and the stored state would not.
        runBlocking {
            val db = inMemoryDatabase()
            val repository = WaypointRepository(db)
            repository.saveWaypoint(
                waypoint("wp-stop", WaypointSharingPolicy.Team.storedValue),
            )
            val before = WaypointShareMapper.scopeOf(
                db.atlasQueries.selectAllWaypoints().executeAsList().single(),
            )
            assertEquals(RecipientScope.TEAM, before)

            repository.setSharingPolicy("wp-stop", WaypointSharingPolicy.Private)

            val after = db.atlasQueries.selectAllWaypoints().executeAsList().single()
            assertEquals(WaypointSharingPolicy.Private, WaypointShareMapper.toShareable(after).policy)
            assertEquals(null, WaypointShareMapper.scopeOf(after))
            assertTrue(!WaypointShareMapper.isShareable(after))
        }
    }

    @Test
    fun aDefaultedRowIsUnshareableEndToEnd() {
        // Raw SQL, deliberately. SQLDelight's generated insert names every column,
        // so going through it would pass an explicit policy and test nothing. The
        // column DEFAULT is what a migration or any other writer that omits the
        // column relies on, and it has to be exercised as raw SQL to be exercised
        // at all. This is the backfill path the whole §10.6 feature rests on.
        runBlocking {
            val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            AtlasDatabase.Schema.create(driver)
            driver.execute(
                identifier = null,
                sql = """
                    INSERT INTO Waypoint (id, name, latitude, longitude, timestamp, notes)
                    VALUES ('wp-default', 'Legacy', 44.0, -93.0, 1, NULL);
                """.trimIndent(),
                parameters = 0,
            )

            val row = AtlasDatabase(driver).atlasQueries
                .selectAllWaypoints().executeAsList().single()

            assertEquals(WaypointSharingPolicy.Private, WaypointShareMapper.toShareable(row).policy)
            assertEquals(null, WaypointShareMapper.scopeOf(row))
            assertTrue(!WaypointShareMapper.isShareable(row))
        }
    }
}
