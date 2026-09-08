# Phase 0.4 — Determinism Policy (Normative)

- **Status:** PROPOSED (normative for fixtures + future harnesses).

## Rules

1. **Floating point:** tolerance-based comparison only. Each float-bearing fixture declares
   `tolerance` (absolute, same units). No bit-for-bit equality claims for geospatial math.
   Reference constants (e.g. haversine R) are recorded per fixture.
2. **Coordinate serialization:** decimal degrees, fixed field order
   (`latitude`, `longitude`, then optionals); number formatting stable (no trailing-noise digits).
3. **Collection ordering:** significant by default (layer z-order, ring vertices, migration segments).
   `order_insensitive` must be declared explicitly where set semantics apply.
4. **IDs:** literal fixed strings in fixtures; no runtime-generated IDs in expected outputs.
5. **Timestamps:** fixed literal timestamps; freshness/state fixtures encode age as explicit
   durations relative to the fixed timestamp, never wall-clock.
6. **Generated geometry:** vertex counts and parameters fixed per fixture (e.g. rings fixture
   declares segment count + approximation method); implementations reproduce within tolerance.
7. **Filenames:** `<ID>_<slug>.json`, lowercase, stable; renames mint traceability entries.
8. **Canonical text:** UTF-8, LF, 2-space indent, sorted keys unless order is semantic;
   no NaN/Infinity literals (non-finite values are encoded as strings in adversarial fixtures).
9. **Hashes:** where integrity fixtures apply (offline packs), algorithm is named per fixture;
   no production hash algorithm is selected in Phase 0.4 (DEC-011 open).
