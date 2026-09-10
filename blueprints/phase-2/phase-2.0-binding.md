# Phase 2.0-H — Resource-to-Execution Binding Contract (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Answers: the exact shape and ownership of the identity → abstract operation
  declaration, with the operation transport-blind.
- **Depends on:** 2.0-C (binding lives in context; miss → unsupported),
  2.0-D (binding external to commands), 2.0-G (four-layer separation).

## 1. Binding shape (normative)

- Binding is an EXPLICIT declaration value: resource identity →
  abstract operation, held in the execution context (2.0-C). Declared means
  constructed by the caller (a map/registry value), never discovered,
  probed, sniffed, or defaulted.
- Lookup is exact identity equality (the 1.6 identity-struct equivalence:
  provider/kind/address triple). No normalization, no fuzzy match, no
  fallback chain, no "closest provider".
- Lookup miss → declared `unsupported` WITHOUT touching any operation
  (2.0-C fixture obligation restated as contract: the miss path contacts
  nothing observable — provable via scripted-operation contact logs).

## 2. Identity-based, never URL-based (normative)

- The binding key is the resource IDENTITY triple. URL/request
  representations, cache keys, payload references, and templates are never
  keys and never participate in lookup — the 1.4/1.6 chain
  (provider ≠ request ≠ resource ≠ cache key ≠ entry ≠ payload) holds through
  execution.
- Corollary: changing a representation (new template, new URL rendering)
  never rebinds; changing the identity triple always rebinds. Binding
  changes when identity changes, and only then.

## 3. Abstract operation (behavior, not transport — normative)

- An operation receives (command payload, explicit context) and yields a
  future outcome value drawn from the closed semantic vocabulary. It is
  replaceable by a synchronous scripted double without changing command
  meaning (2.0-C engine test, operation side).
- The operation receives its world THROUGH the context and nothing else: no
  HTTP clients, credentials, clocks, caches, providers, global services,
  filesystem, or schedulers reached outward (arch-scan enforced, 2.0-P).
- Layer separation (2.0-G, operation side):

```text
AbstractOperation ≠ Provider ≠ Transport ≠ Payload decoder
```

- Binding never reinterprets the command: `RunAcquisition{request}` served
  through a binding stays an acquisition serving. The binding supplies WHICH
  operation serves; it never converts the command into a generic executor
  (2.0-D law 3, binding side).

## 4. Ownership

- The CALLER owns the declaration (which identities bind which operations).
  The SUBSTRATE owns lookup mechanics (exact match, miss → unsupported).
  The OPERATION owns its internal cooperation behavior (2.0-F) and nothing
  about routing. Three owners, no overlap.

## 5. Fixture obligations (→ 2.0-N, architect-specified)

- Known identity binds exactly to the declared operation.
- Unknown identity returns unsupported (and contacts nothing).
- No probing occurs (contact-log proof, not just outcome proof).
- Binding changes when identity changes.
- Representation/URL changes do not redefine identity (rebind check negative).
- Operation receives the explicit context (observable in the double).
- Operation cannot obtain hidden context (arch-scan + double with no
  ambient access).
- Same binding + same inputs → identical result, two-run identical
  (dovetails with 2.0-C determinism; preview of 2.0-M).
