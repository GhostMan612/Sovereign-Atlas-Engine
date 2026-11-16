# Path C requirements source matrix — review draft

**Status: quarantined requirements research, not an implementer handoff.** This document does not approve any source, claim independent authorship, specify a complete Meshtastic wire contract, or authorize distribution. The researching agent had access to the existing Atlas checkout and inherited task context. A qualified reviewer must assess this document and each proposed source before any material crosses to a separately controlled implementation team. Do not provide this draft or its links to that team until the ADR-013 review and access controls are satisfied.

Research date: 2026-11-15. The public pages below were read on that date; live pages may change. Meshtastic pages displayed documentation version 2.8, which is not by itself proof of the exact supported firmware revision. No source snapshot, content digest, fixture, review disposition, or permission for implementation use has yet been established. Public accessibility is not a rights grant; the applicable documentation license and permitted transformations require review. These are pointers to evidence, not approved inputs.

## Source observations and limits

| Source | Observation, paraphrased | Evidence limit and rights disposition |
| --- | --- | --- |
| [Protocol Buffers encoding](https://protobuf.dev/programming-guides/encoding/) | Defines generic binary record types, integer encoding, field ordering, and handling of repeated values. It states that serialization order is not a stable byte identity. | Generic format only; does not identify Meshtastic fields, values, versions, or limits. Rights and implementation use: unreviewed. |
| [Protocol Buffers techniques](https://protobuf.dev/programming-guides/techniques/) | A protobuf message stream needs an external way to establish message boundaries. | Generic format only; does not establish Meshtastic USB or BLE framing. Rights and implementation use: unreviewed. |
| [Meshtastic getting started](https://meshtastic.org/docs/getting-started/) and [web client connections](https://meshtastic.org/docs/software/web-client/) | Users connect clients to devices by Bluetooth or USB, among other transports. | Establishes a capability, not the on-wire sequence or bytes. Rights and implementation use: unreviewed. |
| [Meshtastic serial module](https://meshtastic.org/docs/configuration/module/serial/) | Its protobuf client API mode exposes a client interface on a serial port. | This documents a serial-module configuration, not every USB transport's framing. Rights and implementation use: unreviewed. |
| [Meshtastic mesh algorithm](https://meshtastic.org/docs/overview/mesh-algo/) | Describes acknowledgments and distinguishes broadcast behavior from direct-message behavior. | Describes radio-network behavior, not a complete client-facing envelope. Its text includes material attributed to a schema; do not pass that quoted material to the implementer. Rights and implementation use: unreviewed. |
| [Meshtastic Android messages](https://meshtastic.org/docs/software/android/user/messages-and-channels/) | Distinguishes a node accepting a message, mesh delivery, and a direct recipient acknowledgment in user-facing status. | User-facing evidence only; it does not define the exact codec fields or establish equivalent Atlas behavior. Rights and implementation use: unreviewed. |

Search results also exposed client source excerpts. They were not used as implementation inputs or as the basis for field mappings in this document. The researcher is not an independent codec implementer.

## Capability and missing-evidence register

The following identifies what must be specified and tested, not a complete protocol design. Atlas-specific requirements and the present domain interface also require provenance review before they are supplied to an implementer.

| Capability | Minimum question for reviewed specification | Present evidence status |
| --- | --- | --- |
| Handshake and readiness | Which request and response bytes, correlation values, completion criteria, failure behavior, and version limits define a usable connection? | No independent reviewed client-envelope mapping or test vector. |
| Send and receive packets | Which exact message fields, ranges, presence semantics, envelope bounds, and port identifiers apply? | Generic protobuf documentation and high-level transport evidence only; exact mapping missing. |
| Delivery and routing acknowledgments | Which observable events identify local acceptance, peer acknowledgment, broadcast propagation, failure, timeout and correlation? | High-level documentation exists; client-facing encodings and controlled compatibility evidence are missing. |
| Node discovery and metadata | Which messages identify the local node and remote nodes, and what are the supported field values and missing-value rules? | Exact mappings, versioned fixtures and source rights not reviewed. |
| Position and telemetry | Which fields, units, integer encodings, optionality and validity limits must be supported? | Exact mappings, units and validated vectors not established by the sources reviewed here. |
| BLE and USB | Which message boundaries, failure modes and lifecycle events are required at the existing transport/codec interface? | Client capability documented; complete per-transport requirements and physical-device evidence missing. |
| Malformed, unknown and future fields | What size, nesting, integer, duplicate-field and unknown-field policy preserves safety and interoperability? | Generic protobuf rules are available; Atlas-specific bounds and policy need a separately reviewed specification and fixtures. |

## Conditions for the next handoff

1. Identify and record a genuinely separate implementer, their prior access and the technical restrictions on their inputs and tools. Obtain qualified review of the process and sources.
2. Replace each unknown above with independently sourced, version-specific functional facts, traceable to permitted material and approved by a reviewer. Record source revision or retrieved snapshot and its digest, rights basis, transformation and reviewer decision. Do not copy source, comments, schema text or generated code into the requirements.
3. Obtain independently sourced, provenance-reviewed positive and adversarial fixtures covering the accepted firmware versions. Black-box observations and comparison may require separate review; feedback to the implementer must be screened for restricted expression.
4. Require compatibility, source and artifact inspection and independent provenance review before removing the legacy implementation. Physical radio acceptance, qualified licensing review, the independent publisher and existing release holds remain separate requirements.

Until these conditions are satisfied, this matrix is insufficient to implement or validate a wire-compatible replacement. It does not change Issue #16, the hardware hold on Issue #9, or the release hold on Issue #10.
