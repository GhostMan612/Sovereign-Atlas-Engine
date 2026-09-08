# Phase 0.4 — Architecture Test Spec (ATLAS-NORMATIVE expectations, harness PLANNED)

- **Status:** PROPOSED spec. No tooling implemented (would force DEC-016; deferred).
- **Authority:** dependency ban-list (`dependency-policy.md` §2, Phase 0.3 PASS) + dependency-map.md + ADR-001.

## atlas_core forbidden-dependency checks (ATLAS-NORMATIVE)

Future tooling MUST prove `atlas_core` (and `atlas_geo` purity surface) contains no imports of:

```text
Flutter / widgets / BuildContext
Android Context / Activities / Views
MapLibre / flutter_map / any map SDK
GPS / sensor / permission APIs
filesystem APIs
HTTP clients
credentials / API keys
concrete tile URLs / provider endpoints
host-app SDKs (Mantle mesh/Hub, Recovery services)
```

Check form (tool-agnostic): enumerate each package's import/dependency declarations and fail on
any match against the ban-list plus any `packages/ → apps|integrations` edge. Exact matcher is
language-dependent (custom_lint / Gradle rules / crate boundaries / eslint boundaries per
`build-toolchain-spec.md`) and therefore PLANNED, not specified here.

## Structural checks (PROPOSED)

- Package boundary violations (imports outside `dependency-map.md` allowed directions).
- Forbidden imports (ban-list above, extended per package per `package-boundary-spec.md`).
- Circular package dependencies (none permitted).
- Production implementation in the wrong package (e.g. mesh transport outside `integrations/`,
  renderer SDK types in `packages/` public models).
- Renderer leakage into core/geo (any map-SDK type in core/geo public surface).
- Provider-specific behavior in core (concrete URLs, keys, rate values, single-vendor branches).
- Reserved-package activity (`atlas_analysis/terrain/history/plugins` gain no code/deps/edges without an ADR).

## Gate posture

These checks are specified in 0.4 and enforced from 0.5 onward (first production code).
Phase 0.4 itself is verified by inspection (this spec + clean status), not by tooling.
