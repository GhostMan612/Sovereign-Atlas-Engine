# Phase 0.3 — Dependency Policy (ATLAS-NORMATIVE)

- **Status:** Normative policy effective immediately for all future changes.
- **Authority:** AGENTS.md §4 + ADR-001 + dependency-map.md. Violations require an ADR.

## 1. Dependency classes

```text
Core dependencies ......... allowed in atlas_core/atlas_geo ONLY if: pure, pinned, license-compatible,
                            offline-safe, and free of UI/renderer/platform/SDK types. Default: NONE.
Platform dependencies ..... confined to adapters (integrations/, app shells, renderer plugins).
                            NEVER in core public models.
Renderer dependencies ..... confined to renderer adapters behind atlas_map contracts.
Optional dependencies ..... provider plugins only; declared in descriptors; never required for boot.
Development dependencies .. test/lint/format/fixture tooling; never shipped in engine packages.
Application dependencies .. host-app concerns (meetings, ancestry, mesh SDKs); confined to integrations/.
```

## 2. What MUST NOT enter atlas_core (ATLAS-NORMATIVE, non-exhaustive)

```text
Flutter UI / widgets / BuildContext
Android Context / Activities / Views
MapLibre / flutter_map / any map SDK types
GPS / sensor / permission APIs
filesystem APIs
HTTP clients
provider credentials / API keys
concrete tile URLs
host-app SDKs (Mantle mesh/Hub clients, Recovery services)
```

Admission of any item above into core requires an explicit ADR with justification,
scope, and removal plan. Silence is denial.

## 3. Rules for other packages

- `atlas_geo`: same purity as core (no I/O, no UI, no renderer).
- `atlas_provider_api`: contracts only; implementations depend on it, never reverse.
- `atlas_tiles` / `atlas_offline`: policy + contracts in packages; concrete HTTP/fs clients in adapters/implementations.
- `atlas_map`: orchestrates via contracts; renderer SDKs stay in adapters.
- Reserved packages: no dependencies of any kind until opened by ADR.
- `secrets/` placeholder: no dependents until its ADR resolves.

## 4. Addition procedure (ATLAS-NORMATIVE)

1. Name the package + class (core/platform/renderer/optional/dev/app).
2. Justify why existing capability cannot serve (no duplication per AGENTS.md).
3. Record license, version pin, offline behavior, failure behavior, size/perf cost.
4. Prove boot still works without it (or classify it non-core).
5. ADR accept → add → lockfile/attribution update (mechanism TBD in DEC-016/019).

## 5. Current state

Zero production dependencies exist (SOURCE-VERIFIED: 112-file scaffold at `dee1f7e` + docs-only
commits `4adf96a/bf4766e/1aa9a0c` contain no manifests). This policy keeps it that way until Phase 0.5.
