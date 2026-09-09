# Phase 1.6 — Resource Boundary Architecture (Normative)

- **Status:** Normative for `atlas_provider_api` resources. Stops at the
  acquisition boundary; transport, cache, offline, and rendering are downstream
  and never authoritative over these semantics.

## 1. Resource doctrine (1.6-B/C/D)

`AtlasResolvedResource{identity, provider, kind, tile?, scheme?, attribution?,
license?, sensitivity?}` is what resolution identified — never bytes obtained.
Identity is the opaque triple provider/kind/address (canonical tile form
`z=<z>/x=<x>/y=<y>@<scheme>` for tiles, documented rendering never a URL;
dataset qualifier or "" otherwise). Binding via `fromResolution` throws unless
resolved. Equality is identity-struct equality. All nine Phase 1.4 kinds
resolve; only raster/vector carry tile addresses (1.6-D acceptance holds).

## 2. Materialization doctrine (1.6-E/F — the justified smaller equivalent)

No lifecycle machine exists (evidence supports a boundary marker, not a
lifecycle). `AtlasMaterializer.materialize` derives representations without
acquiring: tiled + caller template → ready + string; tiled without template or
any non-tiled → deferred (never forced, never an error); invalid resource →
invalid. `Available/Unavailable/acquisition-Failed`/retry/progress are refused
as unjustified. Template mechanics reuse `AtlasTileRequest.resolveUrl` (no
duplication); unknown placeholders throw; layer binds unnamed (empty id marks
"unbound", documented — layer binding is downstream).

## 3. Boundary restatement (1.6-I/J/K/L/M)

URL strings are representation DATA, never network activity: no fetch/client/
socket/fs/cache/decode/renderer/pixel/credential anywhere (grep-verified).
Key≠entry intact alongside materialization (BOUND-001). Identity strings are
validated URL-free (ADV-042). `atlas_tiles`/`atlas_offline` remain empty.
Dependency direction holds (provider_api → core only). Zero production deps.

## 4. Verification snapshot (at closure)

- Runner: total=192, pass=152, fail=0, blocked=8, notApplicable=32 (two-run identical).
- 14 new fixtures (RESRC-001..004, MAT-001..004, AVAIL-001, RID-001, REP-001,
  BOUND-001, ADV-042/043), zero dup IDs across 190 files.
- `dart analyze`: clean. `dart format --check`: clean. Arch-leakage checks green.
- DEC-001..019 all open; no new DECs (all sub-details owned by contract text).
