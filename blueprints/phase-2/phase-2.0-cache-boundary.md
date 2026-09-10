# Phase 2.0-I — Cache Execution Boundary (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  First of the guarded I/J/K trio: cache crossing without cache ownership.
- **Depends on:** 1.7 (lookup/entry semantics), 2.0-D (`ServeEntry`,
  `StoreHandoff`), 2.0-H (identity binding).

## 1. Serve rule (normative)

- The substrate serves the NAMED entry — it never re-runs lookup, never
  re-evaluates freshness, never revalidates. The pipeline already decided
  (1.9); re-deciding would violate 2.0-A Law 1.
- Binding key is `entry.resource` (the identity). Entries WITHOUT a resource
  identity cannot bind → `unsupported` (failed/UNSUPPORTED_BINDING at
  execution level): tile-key-only entries gain resource linkage downstream;
  the substrate invents no identity (2.0-A Law 3).
- The `fallback` flag travels into the result untouched (2.0-D law 1):
  execution reports fallback-serving; it never re-derives staleness.
- Serving performs no retention act: no remove, no expire, no invalidate, no
  refresh, no write-through. Retention machinery does not exist here (1.7
  §10 refusal extended across the boundary).

## 2. Handoff rule (normative)

- `StoreHandoff` OFFERS the candidate entry to the bound operation, which
  acknowledges by returning it (echo/normalized). Offer ≠ persist: the
  substrate cannot verify persistence and never claims it — the result
  reports `stored` (acknowledged), never `persisted`.
- Binding key is `handoff.resource` (always present by 1.9 construction).
- No storage verbs exist in the substrate: no put/get/delete, no paths, no
  handles, no database rows, no file writes. The operation behind the binding
  may store (downstream concern); the substrate only offers and reports.

## 3. Lookup ownership (normative)

`AtlasCache.lookup` stays the SOLE decider and stays pure in 1.7. The
substrate may not import new decision logic, cache policies, TTL
interpretation, or eviction heuristics. Cache Policy (DEC-009: eviction/TTL
values) remains OPEN and untouched — execution adds no facts about it.
