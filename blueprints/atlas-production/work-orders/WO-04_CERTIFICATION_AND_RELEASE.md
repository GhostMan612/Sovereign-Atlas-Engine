# WO-04 — CERTIFICATION AND RELEASE

## Scope and delivery
Audit security/privacy, data licensing/attribution, offline/online degradation, crash/ANR, battery/thermal, accessibility, low-memory, physical performance, two flavors, CI, SBOM, package/version/signing, SDK publishing, operator runbooks and recovery. No release without evidence and explicit operator approval.

## Execution protocol
- [ ] Read canonical RULES/AGENTS/handoff; record exact HEAD and dirty state
- [ ] Map each requirement to existing code before modifying; never replace functioning implementations without regression proof
- [ ] Record design decision/ADR BEFORE any new dependency, package or architecture boundary change
- [ ] Implement coherent end-to-end functionality with failure modes and synthetic fixtures
- [ ] Record data source license, terms, provenance, attribution, validity and privacy for each dataset/provider
- [ ] Add pure tests, adapter tests and instrumented test plans as appropriate; never weaken existing tests
- [ ] Run sanctioned two-flavor host gate once for this phase; record actual XML results
- [ ] Produce evidence chain and matrix using `docs/atlas-production/templates/`
- [ ] Preserve operator's pre-existing changes; no automatic commits/pushes, no device runs/builds without separate approval
- [ ] Exit only on verified acceptance; otherwise mark BLOCKED with actionable next step

## Mandatory evidence
SOURCE → REQUIREMENT → DECISION → BLUEPRINT → IMPLEMENTATION → FIXTURE → TEST → DEVICE/USER ACCEPTANCE → RELEASE DISPOSITION.

## Acceptance
- [ ] All scoped features have named acceptance IDs and no unresolved high-severity regressions
- [ ] Contracts remain renderer-independent and provenance-aware
- [ ] Offline/permissions/error paths exercised with appropriate evidence
- [ ] Test counts and results are measured, not copied from historical notes
- [ ] Operator can independently verify and reverse this work order
