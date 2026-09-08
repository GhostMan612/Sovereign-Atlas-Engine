# Phase 0.4 — Fixture Architecture (Normative)

- **Status:** PROPOSED (normative for Phase 0.4+). No harness implemented.
- **Vocabulary:** SOURCE-VERIFIED / ATLAS-NORMATIVE / PROPOSED / DECISION REQUIRED (Phase 0.2 review).

## 1. Purpose

Golden fixtures make Phase 0.2 contracts mechanically checkable without selecting DEC-014
(language). A fixture is an observable contract behavior: canonical input → expected output
(or expected rejection) within documented tolerance. Fixtures MUST NOT encode internal classes.

## 2. Categories and naming

Categories mirror `test/golden/` subdirs (Workstream 9): `geo`, `distance`, `bearing`,
`camera`, `layers`, `tiles`, `cache`, `offline`, `provenance`, `geometry`, `parcels`, `h3`,
`structures`, `migration`, `tactical`, `radio`, `adversarial`. Catalog: `golden-fixture-catalog.md`.

## 3. Fixture IDs and filenames

- Stable ID per fixture: `<CATEGORY>-<NNN>` (e.g. `DIST-010`, `CAM-003`, `ADV-017`).
- Filename: `<ID>_<short-slug>.json` (lowercase, hyphens). IDs are never reused; renaming requires
  a traceability update (`fixture-traceability.md`).

## 4. Representation

- Deterministic text-based canonical JSON (UTF-8, LF, 2-space indent, sorted keys where
  semantic order is irrelevant — see `determinism-policy.md`).
- Every fixture file carries: `id`, `contract` (contract ID(s) or `none/proposal`),
  `status` (SOURCE-VERIFIED-derived / ATLAS-NORMATIVE / PROPOSED / DECISION REQUIRED),
  `inputs`, `expected` (output OR rejection), `tolerance` (for floats; absent only for exact types),
  `trace` (CAP → F-trace → SRC `file:line`, or `Source: none / Origin: architectural proposal`),
  `notes`.

## 5. Canonical policies

- **Serialization:** canonical JSON per `determinism-policy.md` (key order, number formatting, no NaN/Infinity literals — non-finite inputs are strings, see §7).
- **Numeric precision:** inputs verbatim; expected floats with ≥6 significant digits; comparison is
  tolerance-based, never bit-equality (harness spec). Haversine reference radius documented per
  fixture (`R=6371.0088` for Phase 0.4 vectors; SRC-A uses R6371 — tolerance absorbs the delta).
- **Ordering:** sequences significant unless declared `order_insensitive` (layer z-order IS significant).
- **Null/absence:** absent field ≠ null field ≠ empty value; fixtures use whichever the contract specifies.
- **Invalid inputs:** represented explicitly with `expected.rejection` (category + mandatory flag +
  coercion prohibition); see `adversarial-fixture-catalog.md`.
- **Errors:** categories only (e.g. `INVALID_COORDINATE`, `OUT_OF_RANGE`, `MALFORMED`), NOT detailed
  code enums (none established by contracts — DEC-006).
- **Timestamps:** fixed strings (`2026-01-01T00:00:00Z` style); no wall-clock in fixtures.
- **Identifiers:** fixed literal strings; no generated UUIDs in fixtures.
- **Provenance:** embedded `provenance` object where the contract requires carriage.
- **Versioning:** each fixture has `fixture_version: 1`; breaking semantic changes mint a new ID and
  deprecate the old (migration rule); additive clarifications bump `fixture_version` with a changelog note.

## 6. Ownership and review

- Owner: Atlas architect (content) + fixture author (mechanics). Every new fixture needs:
  contract link (or explicit `none`), tolerance justification, and review sign-off before the
  Phase 0.4 gate counts it. Adversarial fixtures additionally need the coercion verdict.
