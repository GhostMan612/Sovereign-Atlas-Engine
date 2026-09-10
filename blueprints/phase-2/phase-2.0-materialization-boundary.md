# Phase 2.0-K — Materialization Boundary (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Third of the I/J/K trio: reporting materialization without doing it.
- **Depends on:** 1.6 (pure materializer; ready/deferred/invalid), 1.9
  (materialize directive + materialized terminal), 2.0-D (no materialize
  command), 2.0-G (bytes boundary).

## 1. Derivation rule (normative)

- Representation derivation stays PURE and caller-side (`AtlasMaterializer`,
  1.6): the substrate never derives, never inspects, never parses
  representation strings. A representation is opaque DATA transiting the
  boundary, exactly like 1.6 (URL strings are data, never activity).
- Consequently no decode of any kind (image/vector/terrain/payload) exists
  in or behind the substrate's contract surface (2.0-A §3 refusals).

## 2. Report rule (normative)

- After `StoreHandoff` acknowledgment, the CALLER materializes purely and
  the pipeline terminates `materialized` (1.9 order: handoff construction →
  store offer → materialize → terminal). The substrate reports the store
  acknowledgment; it never reports `materialized` itself (that terminal
  belongs to the pipeline's vocabulary, not execution's).
- A caller-reported materialization-`invalid` after execution success stays
  `materializationFailed` (1.9-G preserved across execution — no silent
  conversion at any layer).

## 3. Non-inspection rule (normative)

- The substrate never branches on representation content, kind-specific
  fields, tile coordinates, or payload shape. Kind-agnosticism (1.9-K)
  survives execution: the same three commands serve raster, vector, geojson,
  elevation, local datasets, and every other kind identically.
