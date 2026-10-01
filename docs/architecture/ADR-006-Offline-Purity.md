# ADR-006 — Abstract Offline Socket Networking

- **Status:** Accepted
- **Date:** 2026-09-30
- **Deciders:** Operator directive + execution agent
- **Scope:** Where `offline/` networking lives. No renderer change, no provider
  change, no pack-format change.

## 1. Context

`offline/` is a pure-tier package that opens real sockets and makes real HTTP
calls. RULES 2.1 does not currently forbid this, so the purity scan passes it.
It nonetheless makes the package untestable off-device and is the weak point in
the tier.

Specifically, `offline/PackTileServer.kt` imports `java.net.ServerSocket` and
`java.net.Socket`, and `offline/OfflineDownloader.kt` imports
`java.net.HttpURLConnection` and `java.net.URL`.

Two things are deliberately **not** claimed here:

- This is **not** a RULES 2.1 violation. 2.1 names MapLibre, Android, Compose,
  and AndroidX. It does not mention `java.net`. An earlier draft of this ADR
  asserted that `offline/` violated the pure-logic boundary; that was wrong and
  was retracted. `atlas_purity_scan` correctly reports the package clean.
- `java.net` **was** once nominally forbidden, by
  `blueprints/MASTER_OPERATING_DIRECTIVE.md` §2. That document is retired
  (RULES §3, "Dead docs do not bind") because it demanded a build per prompt.
  Its `java.net` clause died with it and was never carried into RULES.

The real argument is weaker and stronger than a rule violation. Weaker: nothing
currently stops the next agent from adding a third socket to this package.
Stronger: `PackTileServer` and `OfflineDownloader` cannot be exercised on the
JVM without binding a real port and opening real sockets, so the pure tier's
central property — that its logic is unit-testable without a device — does not
hold for the two largest files in `offline/`.

## 2. Decision

- **Do not weaken the purity scan.** `FORBIDDEN_IMPORTS` in
  `.opencode/plugin/atlas-tools.ts` stays exactly as it is. Adding `java.net` to
  it would be a cheap way to make this decision self-enforcing and would fail the
  gate on four existing lines. It would also be a rule change smuggled into a
  refactor, which RULES 2.4 forbids. The scan is not the enforcement mechanism
  here.
- **Extract the networking implementations into the `android/` layer.**
  `PackTileServer` and `OfflineDownloader` move to
  `com.sovereignatlas.atlas.android.offline`, alongside the other platform
  adapters already in `android/` (`android/data/`, `android/comms/`,
  `android/location/`). `android/` is not a pure package, so sockets are legal
  there.
- **Define pure Kotlin interfaces in `offline/`** that those implementations
  satisfy. The contracts carry only values — paths, byte counts, intervals,
  callbacks — and never a socket, stream, or connection type. Callers in `offline/`
  depend on the interface; `android/` supplies the implementation at the existing
  DI seam in `AppServices.kt`.
- **Interfaces before implementations.** The interfaces and their tests land in
  this ADR's scope. The `android/` extraction lands as the follow-on change that
  makes them used. A refactor that lands both halves at once cannot be reviewed
  for behaviour preservation, because every test that proves the old behaviour
  also proves the new one.

## 3. Consequences

- Keeps the pure tier fully testable on the JVM. `offline/` logic becomes
  testable against a fake transport with no port bound and no socket opened.
- Requires a refactor of the existing offline tile server and downloader logic.
  That work is not free and is not scoped here.
- `PackTileServerTest` and `TileBucketQuotaTest` currently import `java.net`
  themselves. They move with the implementations, or they are rewritten against
  the interface. Leaving them where they are would preserve the exact coupling
  this ADR exists to remove.
- The rate-limit and timeout constants (500 ms floor, 30 s per-tile) currently
  live on `OfflineDownloader`. They are policy, not transport, so they belong on
  the `offline/` interface and not in the `android/` implementation.
- If a future RULES change adds `java.net` to 2.1, this ADR's extraction becomes
  mandatory rather than advisory. It does not become wrong.

## 4. Rejected alternatives

- **Add `java.net` to `FORBIDDEN_IMPORTS`.** Rejected: it fails the gate
  immediately on four existing lines, and it changes RULES by side effect.
  Revisit if and when RULES 2.1 is amended deliberately.
- **Move the whole `offline/` package to `android/`.** Rejected: `offline/`
  also holds genuine pure logic — `OfflineStore`, `OfflinePacks`, `DemPack`,
  `OfflineProviders`. Only the two networking files move.
- **Leave it and document the exception.** Rejected: this ADR is the
  documentation, and it commits to the extraction rather than excusing it.