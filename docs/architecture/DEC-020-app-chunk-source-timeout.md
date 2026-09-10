# DEC-020 — App-Side Chunk-Source Timeout (Offline Areas)

- **Status:** Accepted (implementation prompt authorized from the timeout
  semantics investigation).
- **Date:** 2026-09-10.
- **Baseline:** `96f81b7` (pushed). Builds on APP-AUDIT-001 (local, then
  pushed as `cfe3d20`) and the timeout investigation (no code changed).
- **Scope:** Application adapter only (`apps/atlas`). No engine file is
  modified by this decision. No new engine contract is created.

## 1. Context

A combined device run observed a real Esri tile fetch stall with no
terminal and no timeout: the downloader awaited indefinitely. Evidence
(`packages/atlas_providers/lib/src/tile_fetch_operation.dart`,
`httpTransport`): the engine transport imposes no deadline, and no
cancellation handle escapes the in-await phases. A permanently stalled
request additionally wedges the pack: `startDownload`'s `finally` never
runs, so the stale `_downloads[packId]` entry makes every later
`startDownload` return early silently
(`apps/atlas/lib/offline/offline_repository.dart`, guard + cleanup).

`timedOut` exists in the acquisition subsystem
(`AtlasAcquisitionState.timedOut`, `AtlasAcquisitionFailure.timeout`,
`AtlasAcquisitionPolicy.timeoutSeconds`, pure `checkTimeout`), but the
Offline Areas downloader does not use that subsystem — wiring it there
would not fix the observed path
(`OfflineRepository → AtlasPackDownloader → defaultChunkSource →
httpTransport → HttpClient`).

## 2. Decision

**Timeout detection at the app chunk-source boundary → existing
downloader `failed` terminal → preserve `TimeoutException` identity in
`failureDetail`.** Concretely:

1. The repository wraps its chunk source per tile with a bounded wait
   (`Future.timeout`, default `kDefaultPerTileTimeout` = 30 s app policy,
   constructor-injectable for tests). Expiry surfaces as
   `TimeoutException`, which the downloader's existing catch records as
   `failed` with `failureDetail = 'TimeoutException: ...'`.
2. No new terminal state. No downloader lifecycle change. `failed` is the
   semantically correct carrier (an attempt ran and did not succeed) and
   already provides the operator Resume path; received tiles are
   preserved by the existing received-map mechanics.
3. Cancellation retains priority: if a timeout fires while cancellation
   was requested for that pack, the app records `cancelled`, not
   `failed` (extends the downloader's existing observe-cancellation-first
   loop order to the race the engine cannot observe mid-await).
4. The bounded wait guarantees every `download()` call settles, so the
   `finally` cleanup always runs — the stale-`_downloads` wedge is closed
   by construction (worst case is bounded lateness, never permanence).
5. Explicitly NOT decided here: a downloader `timedOut` terminal,
   provider-specific timeout fields, engine-transport deadlines, retry
   backoff/counters. The acquisition `timedOut` stays unwired (no
   speculative wiring).

## 3. Consequences

- One new app policy constant (documented, adjustable) + one injectable
  constructor parameter. No hosted dependencies. No UI changes.
- `failureDetail` text contract extended by convention and test:
  timeout identity must read `TimeoutException` (diagnostics distinguish
  via detail text; taxonomy stays `failed`).
- Deterministic proof via never-completing fake sources (host) and a
  forced short-deadline injection on device (terminal mapping only —
  real socket-timing remains inherently nondeterministic and unclaimed).
- Known residual: worst-case apparent stall ≈ timeout × remaining tiles
  (bounded, never indefinite). A future engine-transport deadline (for a
  second host) would obsolete the app-side default, not conflict with it.

## 4. Alternatives rejected (evidence in the investigation)

- **Downloader `timedOut` terminal:** correct acquisition semantic,
  wrong layer for this path — would cascade into lifecycle + UI +
  persistence + proofs for one stall observation.
- **Provider-specific timeouts:** no evidence providers need different
  deadlines; adds an unjustified policy dimension + contract/ADR change.
- **Engine-transport deadline now:** right mechanism, wrong scope —
  benefits only future hosts while changing shared behavior all current
  proofs rely on.
- **No timeout (document the hang):** rejected — the restart wedge makes
  "hang" a liveness defect, not a cosmetic gap.
