# Phase 0.4 — Contract Test Harness Spec (Specification Only, Not Implemented)

- **Status:** PROPOSED specification. No harness code exists; no language selected.
- **Boundary:** if realizing any section below forces a language/toolchain choice, STOP and file
  DECISION REQUIRED (DEC-016/017) instead of choosing.

## Pipeline (conceptual)

```text
fixture discovery (scan test/golden/**.json by ID)
  → fixture parsing (plain JSON; schema-check fixture envelope)
  → implementation invocation (adapter calls one contract operation per fixture)
  → output normalization (native → canonical expected shape)
  → comparison (exact / tolerance / rejection-category per cross-language-test-model.md)
  → pass/fail classification (FAIL on tolerance breach, missing rejection, or silent coercion)
  → diagnostic output (fixture ID, contract ID, diff, tolerance, trace link)
```

## Requirements

- Hermetic: no network, no wall-clock, no randomness; fixed timestamps only.
- Discovery is by fixture ID, never by filesystem walk order (ordering-insensitive runner).
- Invocation adapters are per-implementation shims; the harness core (if any) stays
  language-neutral. A language-specific runner is permitted ONLY as a thin adapter, never as
  the fixture authority.
- Diagnostics link back to `fixture-traceability.md` (contract → evidence).
- Result taxonomy: `PASS / FAIL / ERROR (harness fault) / SKIP with reason (explicit DECISION REQUIRED only)`.
  Silent skips are forbidden.

## Non-requirements (Phase 0.4)

No CI wiring, no coverage gates, no performance benchmarks, no renderer/pixel comparison.
