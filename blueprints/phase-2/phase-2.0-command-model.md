# Phase 2.0-D — Command Model Contract (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Answers: what exactly is an executable command/value?
- **Depends on:** 2.0-A (demand rows 1–3), 2.0-C (commands are caller-awaited
  values; context carries time/cancel/binding).

## 1. Command set (closed, three members — each a value, no behavior)

| Command | Serves pipeline directive | Carries (exactly) |
|---|---|---|
| `ServeEntry` | `useCache{entry}` / `useStaleCache{entry}` | the entry + `fallback` flag (false = usable hit, true = declared stale fallback — provenance from the outcome, not rediscovered) |
| `RunAcquisition` | `acquire{request}` | the acquisition request (identity + declared bounds) |
| `StoreHandoff` | `materialize{…, handoff}` (store half) | the candidate handoff entry |

Deliberately ABSENT: any `ExecuteResource` / `FetchResource` / `LoadResource`
generic — the 2.0-I/J/K guard: collapsing the three boundaries into one
abstraction would lose stage provenance, so the type system keeps them apart.
Also absent: a materialize command (representation derivation is pure and
already exists — 1.6 `AtlasMaterializer`; execution adds nothing) and any
report/terminal command (terminals are reported, never executed).

## 2. Value laws (normative)

1. Commands are immutable values with equality (like every Phase 1 value).
2. A command carries semantic payloads ONLY (entries, requests). It never
   carries operations, contexts, callbacks, futures, clocks, or credentials —
   otherwise the command would smuggle the environment 2.0-C closed off.
3. Operation binding happens at serve time from context (2.0-H), never inside
   the command: the same command value serves identically under any binding.
4. Construction is total over its payloads (no validation beyond non-null
   required fields — payload validity belongs to the semantic contracts that
   built them; execution never re-validates, Law 1 of 2.0-A §4).
5. One command = one directive served (2.0-A). Batching/sequencing/chaining
   commands is caller choreography, not a command type — no composite,
   pipeline, or transaction command exists.

## 3. Non-contents (refusals, not gaps)

No priority, no timeout-per-command (deadlines live in acquisition policy,
1.8-C), no retry count (no scheduler exists), no progress callback (no
mechanism to report from), no owner/issuer identity (no principals in the
substrate), no TTL/cache-directive (1.8-C refusal extended).

## 4. Fixture obligations (→ 2.0-N)

- Each pipeline directive maps to exactly one command type (useCache→
  ServeEntry[fallback=false], useStaleCache→ServeEntry[fallback=true],
  acquire→RunAcquisition, materialize→StoreHandoff); terminals map to none.
- Same directive values → equal commands (value semantics).
- A command holds no executable reference: serving the same command under two
  bindings executes the two bound operations (binding-sensitivity without
  command-awareness — preview of 2.0-H/M).
