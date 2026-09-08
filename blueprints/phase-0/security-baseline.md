# Phase 0.7 — Security Baseline

Authority: master blueprint Master Security/Provenance models; `AGENTS.md` §3–4.
No production code in this change; this is the policy baseline that later
contracts must enforce.

## 1. Data classes

```text
PUBLIC       — open data, attribution required (e.g. OSM with license terms TBD per provider).
COMMUNITY    — shared within a defined community; redistribution bounded by source terms. TBD catalog.
PRIVATE      — user- or org-owned; explicit recipient scope + TTL. Never implicitly public.
LOCAL_ONLY   — must never leave the device; no silent public endpoint fallback, no auto-upload.
SENSITIVE    — restricted/cultural/safety-critical; explicit access control + provenance + audit where appropriate.
```

Indigenous and historical geographic data default to no lower than
`COMMUNITY` and rise to `SENSITIVE` where the source community, treaty
context, or cultural sensitivity requires it. Never flatten source context
into generic POIs.

## 2. Required controls (Phase 0 contract obligations)

- [ ] Explicit sharing state on every shareable object (waypoint, track, pin, pack).
- [ ] Provider permissions declared in `atlas_provider_api` descriptors (network, storage, location, user-data, sharing).
- [ ] Dataset sensitivity metadata on every normalized feature (see §4).
- [ ] `LOCAL_ONLY` providers cannot silently fall back to public endpoints.
- [ ] Private imagery never receives an automatic public upload/fallback path.
- [ ] Offline data encryptable where required (mechanism TBD — later ADR).
- [ ] User tracks/locations have clear retention controls (policy TBD — later spec).
- [ ] Logs avoid unnecessary precise-location exposure.
- [ ] Expensive operations support progress, cancellation, and bounded resource use (carried into offline/prefetch contracts).

## 3. Provenance model (carried with the data)

```text
AtlasProvenance
├── provider
├── dataset
├── datasetVersion
├── retrievedAt
├── sourceDate
├── license
├── geometryAccuracy
├── positionalConfidence
├── transformationHistory
└── notes
```

- Every transformation that materially changes geometry must be able to record source geometry, transformation used, output geometry, and confidence/accuracy effect. Critical for cadastral, historic, and derived layers.
- Normalized feature envelope (see `atlas_data`):

```text
id, geometry, properties, source, sourceVersion, retrievedAt,
license, confidence, accuracy, sensitivity
```

## 4. Open issues (TBD, each needs an ADR/spec before code)

1. `atlas_security/lib/src/secrets/` placeholder — policy contract vs deletion (see ADR index #3).
2. Repository license file MISSING; per-provider license catalog TBD (Phase 2).
3. Encryption mechanism, retention durations, audit-record shape: TBD.
4. Community/sensitive review workflow for Indigenous and historical datasets: TBD with source communities, not unilaterally.
5. Log-scrubbing rules and diagnostics redaction policy: TBD.

## 5. Phase 0 gate relevance

No provider implementation, pack format, or sharing model passes Phase 0
without explicit offline behavior, provenance fields, sensitivity labels,
and provider-failure behavior documented per the change-control checklist
(`AGENTS.md` §7).
