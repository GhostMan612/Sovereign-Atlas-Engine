# ADR-013: Meshtastic compatibility replacement boundary

- **Status:** Approved engineering direction; replacement and distribution clearance pending
- **Date:** 2026-11-15
- **Issues:** #16 (licensing), #9 (physical interoperability)
- **Related:** ADR-011, ADR-012, `docs/atlas-production/evidence/MESHTASTIC_LICENSING_AUDIT.md`

## Context

The current SDK generates and contains classes from pinned GPL-licensed Meshtastic schemas. The operator reports that the maintainer declined additional or alternative licensing for those schemas at this time in [Meshtastic protobufs issue #1183](https://github.com/meshtastic/protobufs/issues/1183#issuecomment-6901884709). This is not a grant of permission. A GPL-compliant distribution remains an alternative subject to review of the exact artifacts and their obligations.

The operator has selected independent protocol compatibility as the primary engineering direction. Interoperability and a separation between a wire protocol and source expression do not establish that any particular implementation is independently authored or legally clear. The existing generated implementation remains controlled development material and distribution stays on hold.

## Decision

Introduce a pure codec interface at the existing session boundary. Until a replacement has been independently developed and accepted, the existing adapter remains the default, including the pinned schemas and generation dependencies; the interface alone changes neither artifact contents nor licensing. Preserve existing session, BLE and USB behavior and their security interlocks. Review the domain model and every input supplied across the boundary for provenance rather than presuming it is independent. This decision qualifies ADR-011's specific adapter choice and ADR-012's transition plan; their established transport and distribution holds remain in effect.

This transition exposes codec injection to the session and BLE and USB clients. The application still constructs the legacy default, and session compatibility constructors still reference the legacy implementation. Replacing or removing the implementation requires a later migration of application construction and the module graph, with source and binary compatibility assessed explicitly.

An independently developed implementation is a separate future work item. It must not be produced by translating or consulting the current adapter, generated sources, vendored schemas, or tests that derive their expression from those sources. A directory, WSL installation, agent label, or fresh conversation alone does not establish separation.

The operator is a solo owner and may coordinate the roles; a second human developer is not a prerequisite. Any proposed use of a separately scoped agent must still document the actual access controls, inputs and history, and receive the process and source review described below. Those controls do not by themselves establish legal clearance.

## Required process before implementation

1. Identify a requirements team and an implementation team with documented identities, access history, responsibilities and conflicts. Obtain qualified review of the proposed process and of the allowed inputs. An agent that has inspected restricted source, including inherited conversation or tool output, cannot be represented as an independently isolated implementer merely by starting another agent.
2. Prepare and review functional requirements from individually identified sources whose use is permitted for that purpose. Record source, revision or retrieval date, rights basis, transformations and reviewer disposition. Do not pass schema text, generated output, source structure, copied comments, source-derived tests or unreviewed protocol details to the implementation team. Resolve unknowns instead of guessing; stop if a necessary requirement cannot be supplied on an appropriate basis.
3. Provision a separately controlled workspace and identity with access limited to the approved requirements and permitted tools. Record the controls and actual materials received, including any model context, prompts and tool access. Record author and dependency provenance and retain a contemporaneous development history. Neither separation of accounts nor documentation alone is proof of independent authorship.
4. Maintain independently sourced compatibility fixtures with provenance, permission and version records. Keep restricted comparison materials outside the implementer's workspace. An independent test role may run black-box compatibility and differential checks where permitted; feedback crossing the boundary must be reviewed for restricted expression before delivery.
5. Review the proposed codec, its dependencies, retained domain models, fixture provenance and exact distribution artifacts with qualified counsel and independent reviewers. A clean scan, passing tests or artifact replacement does not establish ownership, license compatibility or legal clearance.

## Acceptance and transition

The replacement must meet the approved functional contract, bounded parsing and malformed-input behavior, session safety and transmission policy requirements. Tests must cover protocol interoperability, version behavior, error handling and preservation of the existing BLE/USB responsibilities using independently sourced evidence. Actual BLE and USB interoperability remains unverified until physical hardware acceptance. Preserve old behavior and the current implementation until the replacement passes review and compatibility tests; removal of schemas, generated classes and dependencies is a separate reviewed change verified against the exact SDK and application artifacts.

No migration step authorizes distribution. Issue #16 remains open, and signing, publication and distribution remain denied until artifact-specific rights and dependency review, independent approval, and the existing publisher controls are complete. Issue #9 remains on hardware hold.
