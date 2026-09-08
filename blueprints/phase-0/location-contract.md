# ATLAS-LOC-001/002/003 — Location Contract (Normative)

- **Status:** PROPOSED (cascade + lifecycle precedents VERIFIED; state machine PROPOSED).
- **Trace:** CAP-014/F-08 SRC-A `:412-419/:461-466/:595-626/:620-623/:760-767/:2007-2022/:810-812/:601-603`;
  CAP-015/F-08 SRC-A `:73-75/:628-654/:1427-1503`; CAP-R04/F-11 SRC-B `:126-201/:315-338/:970-1028/:404-457`.
- **Normative keywords:** MUST / MUST NOT as written.

## 1. Resolution cascade ATLAS-LOC-001 (source behavior VERIFIED; Atlas correction NORMATIVE)

Observed Recovery cascade: service-off → fallback coords `44.9778/-93.2650` (`:136`);
permission-deny → same (`:152`); <5min last-known (`:157-169`); `medium` fix, 20s timeout (`:172-175`);
stale fallback (`:185-195`); recenter forces fresh fix + `move(…,14)` (`:320`).
Observed Mantle: `[LOC]`-armed `LocationManager` GPS+NETWORK @2s, no fused provider, last-known seed,
provider-enabled guard; fix killed on disarm.

**Atlas correction (NORMATIVE):** adapter fallback coordinates (Twin Cities, HOME, or any product default)
MUST NOT become engine-global defaults. Core reports resolution states; adapters choose defaults.

## 2. Fix ATLAS-LOC-002 (conceptual fields — PROPOSED)

`coordinate`, `accuracy` (meters, PROPOSED), `timestamp`, `freshness` class, `source`
(GPS/network/last-known/fallback), `mock` flag (observed `!isMocked` gate `:413/:427` VERIFIED —
mock awareness is normative; exact flag shape PROPOSED).

## 3. State machine ATLAS-LOC-003 (PROPOSED — labels are contract text, not observed enums)

```text
UNKNOWN → SERVICE_UNAVAILABLE → PERMISSION_REQUIRED → ACQUIRING → VALID_FIX → STALE_FIX → UNAVAILABLE
```

Transitions, dwell times, and freshness thresholds (e.g. what counts as STALE): DECISION REQUIRED
(DEC-012). Observed data points (5-min last-known window, 15-min peer-stale precedent, 20s timeout)
are source facts, not Atlas thresholds.

## 4. Permission and failure (normative precedent VERIFIED)

- Permission-denied and service-disabled are distinct states with distinct UX (observed SRC-B `:136-152`);
  Atlas MUST preserve the distinction (PROPOSED as normative).
- GPS works offline (VERIFIED both ecosystems); meeting/peer list failures surface explicitly
  (`_loadError :211`; `runCatching` patterns) — location failure MUST NOT masquerade as valid fix.
- Compass/orientation: counter-rotated true-north dial with UNRELIABLE dimming VERIFIED; sensor
  implementation is adapter-resident (REBUILD); heading-truth requirements DECISION REQUIRED (DEC-012).
