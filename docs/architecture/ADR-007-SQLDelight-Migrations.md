# ADR-007 — SQLDelight Schema Migration Infrastructure

- **Status:** Accepted
- **Date:** 2026-10-02
- **Deciders:** Operator directive + execution agent
- **Scope:** Schema versioning and migration for the single local database
  (`atlas.db`). No change to any table shape in this ADR; it establishes the
  mechanism that a later change will use.

## 1. Context

`Atlas.sq` defines one table with no versioning and no migration path.

`AppServices.kt` opens the database like this:

```kotlin
val driver = AndroidSqliteDriver(AtlasDatabase.Schema, appContext, "atlas.db")
AtlasDatabase.Schema.create(driver)   // unconditional, every launch
```

`build.gradle` declares the database with no `version` property:

```groovy
sqldelight {
    databases {
        AtlasDatabase {
            packageName = 'com.sovereignatlas.atlas.db'
        }
    }
}
```

With one `.sq` file and no `.sqm` directory, the schema version is therefore 1,
permanently. `CREATE TABLE IF NOT EXISTS` is a no-op against a table that already
exists, so the on-disk shape never changes to match the source.

**Why this is a latent crash, not a style issue.** Adding a column to the `CREATE`
statement — the obvious first move — changes the generated `insertWaypoint` to name
a column that does not exist on disk. Every waypoint save then throws
`SQLiteException: no column named sharingPolicy`, and `selectAllWaypoints` (`SELECT *`,
compiled against the new shape) fails the same way. There is no fallback: `Schema.create`
cannot fail against an existing table, so the app cannot recover at runtime either.

**Why the gate would not catch it.** Every `WaypointRepositoryTest` case builds a
FRESH database via `AtlasDatabase.Schema.create(driver)`. `fileBackedDatabaseSurvivesReopen`
closes and reopens with the SAME code, so it never opens a pre-change database. There is
no old-schema artifact anywhere in the tree to migrate from. A change that crashes every
installed device can therefore pass the host gate green — the exact false-green this
repository's verification laws exist to prevent.

**Why it bites now.** Phase 10 §10.6 requires sharing to be explicit, scoped, and
controllable. The blueprint's `Waypoint` carries a `sharingPolicy` field; the current
table carries six columns and none of them is that. Adding it is the first schema change
this project has attempted, and therefore the first to hit the missing migration path.

**What is already correct.** `AtlasDatabase.Schema.create(driver)` is passed a
`SqlSchema<QueryResult.Value<Unit>>` that includes a `migrate` function, and
`AndroidSqliteDriver` invokes it when the on-disk `PRAGMA user_version` is below the
schema version. The driver-side machinery exists and is unused; only the version number
and the migration scripts are absent. This is a configuration gap, not a dependency to
add.

## 2. Decision

- **Introduce `.sqm` migrations and declare the schema version in Gradle.** A
  `migrations/` directory under the SQLDelight source set, plus an explicit `version`
  on the database declaration so the version is stated rather than inferred from file
  count.
- **Every future schema alteration MUST ship a migration script**, in the same commit as
  the `.sq` change. A column addition is not complete without the corresponding
  `.sqm` step that adds it and backfills a default for rows that already exist.
- **Migrations use `ALTER TABLE ... ADD COLUMN`**, never a destructive reset. Local
  tactical data on a field device — waypoints, offline pack records, settings — is the
  user's, and it cannot be recovered if an upgrade discards it.
- **Backfill defaults are chosen per column and recorded in the migration comment.**
  For `Waypoint.sharingPolicy` the default must be the MOST RESTRICTIVE value, because
  Phase 10 §10.6 requires explicit opt-in: a pre-existing waypoint must not become
  shared merely because a new app version was installed.
- **A migration test opens a real pre-migration database.** The test writes a database
  at the OLD schema version, runs the migration, and asserts both that the new column
  exists and that existing rows read back with the documented backfilled default.
  Without this artifact the false-green described in §1 returns.
- **The version bump is explicit and reviewed.** A `.sq` change that does not also
  increment `version` is an incomplete change, on the same footing as a failing test.

## 3. Consequences

- Existing installs upgrade in place. No crash on launch, and no data loss.
- Schema changes carry slightly more boilerplate: a `.sqm` file, a version bump, and a
  migration test. That is the cost of being able to change the schema at all on
  hardware already in the field.
- The migration test is the part most likely to be skipped under time pressure, and it
  is the only thing standing between a schema change and a fleet of unbootable apps.
  It is treated as mandatory, not optional.
- Because the default is the most restrictive value, a user upgrading with existing
  waypoints sees those waypoints as NOT shared. That is the correct reading of "explicit
  opt-in", and it is a visible state change; the migration comment must say so.
- The migration mechanism applies to `atlas.db` only. Offline packs, the Tink-encrypted
  key store, and downloaded tile data are separate persistence concerns with their own
  versioning rules, and are out of scope here.

## 4. Rejected alternatives

- **Delete and recreate the database on version change.** Rejected: it discards every
  waypoint, offline pack record, and stored setting on the user's device. A field
  device may not be able to reach a backup.
- **Continue using `CREATE TABLE IF NOT EXISTS` and rely on it being harmless.**
  Rejected: it is the current cause of the crash. It is harmless only while the schema
  never changes.
- **Move the schema to Room, which has first-class migration support.** Rejected as
  speculative: RULES 2.4 requires an ADR, and swapping the persistence library is a far
  larger change than adding the versioning the current driver already supports.
