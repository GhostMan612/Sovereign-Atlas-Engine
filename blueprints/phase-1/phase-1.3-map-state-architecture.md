# Phase 1.3 — Map-State Architecture (Normative)

- **Status:** Normative for `atlas_map`. Renderer realization is downstream and
  never authoritative over these semantics.

## 1. Semantic model

`AtlasMapState = camera (AtlasCameraState) + composition (AtlasLayerStack)`.
Camera fields carry the 1.3-A table semantics. The six concepts stay distinct:
validation (accept/reject) / normalization (named geo ops, never hidden here) /
canonicalization (open, DEC-002) / serialization (pipe wire, §4) /
persistence (absent) / renderer realization (downstream, never here).

## 2. Zoom doctrine (1.3-D/L)

Four quantities, never conflated: Atlas camera capability ([0,24] model DATA) ≠
provider native ceiling (provider metadata, not in this package) ≠ renderer
implementation limit (downstream) ≠ dataset resolution (data domain). Deep-view
bands Z0–24 are conceptual viewing bands, not availability claims. A provider
that cannot realize a requested zoom fails OUTSIDE the camera contract.

## 3. Bearing and pitch (1.3-E)

Bearing: contracted shape + finite-only validation; coincident-point behavior
untouched (BRG-004 provisional throw lives in geo). Pitch: guarded scalar with
SOURCE-VERIFIED bounds; normative 3D meaning DEFERRED (no renderer-shaped model
invented; owned by contract text pending terrain phase).

## 4. Serialization doctrine (1.3-J, contract-gated basis)

The pipe wire exists because CAM-002 contracts round-trip stability. Canon:
`lat|lng|zoom|bearing|tilt`, fixed order, full-precision Dart doubles,
MALFORMED on structural/numeric failure, range re-check after parse,
unversioned (explicit gap). Deterministic per value; round-trip stable.

## 5. Composition doctrine (1.3-G)

`atlas_layers` is authoritative; `atlas_map` composes without duplicating.
Definition≠state, baseline≠law, identity levels, and attribution rules are
inherited unchanged. No renderer visibility objects, no provider ids as map
identity, no UI state.

## 6. Transition doctrine (1.3-H)

Pure functional recomposition (`copyWith`) only: deterministic, explicit,
synchronous, no events, no callbacks, no animation, no gestures, no async.
"A→B" description, never a pipeline.

## 7. Equality doctrine (1.3-I)

Structural equality throughout (camera fields; map = camera + stack; stack =
ordered states; definitions exclude titles). Identity questions are answered by
ids, never by equality operators. No titles/URLs/keys/handles participate.

## 8. Hard boundary restatement (1.3-N/O)

Nothing in §1.3-N's prohibited list enters `atlas_map`. Dependency direction
core→geo→layers→map holds with no reverse, renderer, provider, platform,
network, or storage edges.
