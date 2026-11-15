# ADR-012: SDK licensing and distribution boundary

- **Status:** Proposed architecture; rights and distribution clearance pending
- **Date:** 2026-11-14
- **Issue:** #16, work order WO-16-A3
- **Related:** ADR-011; `docs/atlas-production/evidence/ATLAS_CODE_PROVENANCE.md`; `docs/atlas-production/evidence/MESHTASTIC_LICENSING_AUDIT.md`

## Context

The SDK currently includes generated Meshtastic protocol classes from GPL-3.0 schemas. Both application variants also resolve Firebase and proprietary Google/Play artifacts. The current SDK artifact is therefore not an independently licensed core, and neither an open-source dependency inventory nor a passing test demonstrates permission to distribute the combination. The chain of title for Atlas contributions and the TAK schema provenance remain unresolved.

## Decision

1. Apache-2.0 is the **proposed** outbound license for an independently owned Atlas core, subject to confirming chain of title, contributor rights, source provenance and all relevant notices. This proposal does not grant a license to current files or artifacts.
2. Retain the existing Meshtastic-derived implementation and pinned schemas while permission and GPL compliance are reviewed. Do not alter the wire protocol or represent generated output as independently owned. A GPL-compliant Meshtastic distribution remains an option only after its actual source, notices, combination and delivery obligations are reviewed and satisfied.
3. The Android application requires a separate licensing and distribution decision. Publishing SDK source alone does not establish satisfaction of obligations for a combined application. Evaluate the exact app flavor, dependency set and delivery method.
4. Preserve the product goal that independently owned core functionality can be used by closed-source applications. That goal requires an actual reviewed dependency and artifact boundary; the current `:atlas-sdk` is not that boundary because it contains Meshtastic-generated classes.
5. If alternative permission cannot be obtained, evaluate a separately distributed GPL-covered adapter or companion program. Its interfaces, combination and distribution model require legal and technical review **before implementation**; a process or module boundary alone is not a legal conclusion.
6. Preserve `SyncProvider` as the application-facing abstraction and isolate provider creation and activation from offline initialization. Both existing flavors retain their cloud capability unless a product decision authorizes its removal. A generation-based coordinator serializes settings transitions with initiation of authentication and chat writes; previously authorized queued operations must revalidate before starting either. A submitted request is not represented as canceled merely because policy later changes. Firebase-controlled background activity and delivery of requests already submitted are not revoked by this boundary. Cloud dependency selection is not itself a licensing clearance.

## Consequences and release hold

No root license declaration, broad relicensing, public Maven publication or release APK delivery follows from this ADR. The provenance ledger tracks unknowns and supporting evidence; the distribution gate must fail closed when reviewed dispositions, notices or applicable source-delivery evidence are absent. Each proposed artifact and its exact dependency combination needs a documented rights-holder and distribution review. Issue #16 remains open until actual rights and obligations are resolved.
