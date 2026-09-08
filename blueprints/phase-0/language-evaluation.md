# Phase 0.3 — Language Evaluation (Evidence-Based, No Selection)

- **Status:** Evaluation only. No language selected; no implementation implied.
- **Rule (architect directive):** "We already use Flutter" and "Mantle is Kotlin" are
  adapter facts, NOT core-language justifications. Capability is extracted, constraints are not inherited.
- **Criteria:** GIS ecosystem, mobile/desktop/web support, native interop, performance/FFI,
  offline + large-dataset + 3D/terrain fitness, ergonomics, testing, distribution, long-term portability.

## 1. What the sources prove (SOURCE-VERIFIED)

- Mantle TacMap is Kotlin + Android + MapLibre-native (SRC-A imports `:92-113`); its portable
  logic (haversine, bearing, serde, converters, policies) is already separable from SDK calls (F-01..F-09).
- Recovery map is Dart + flutter_map + `dart:io`/`http`/`path_provider` (SRC-B/C); its portable
  logic (slippy math, cache-first flow, prefetch runner shape) is separable from widgets (F-10/F-11).
- Neither fact selects the Atlas Core language. Both select adapter languages for their own hosts.

## 2. Candidate comparison (PROPOSED assessment; ranks are relative, not scores)

| Criterion | Dart | Kotlin | Swift | TypeScript | Rust | C/C++ |
|---|---|---|---|---|---|---|
| GIS ecosystem | small; GIS libs thin (INFERRED) | moderate (JVM geo libs) | small (Apple-centric) | large (web GIS: GDAL-adjacent, turf, loaders) | growing (geo/georust, GDAL bindings) | largest legacy (GDAL/PROJ/GEOS native) |
| Mobile | excellent (Flutter iOS+Android) | excellent (Android native; KMP evolving) | excellent (iOS native only) | good (React Native/Ionic/WebView) | good via FFI, not UI-native | good via NDK/SDK interop, heavy |
| Desktop | good (Flutter Win/Linux/macOS) | good (JVM/KMP) | poor (macOS only) | excellent (Electron/web) | excellent (native) | excellent (native) |
| Web | Flutter-web (heavier) | Kotlin/Wasm evolving | negligible | excellent (native) | excellent via Wasm | good via Wasm (heavy toolchain) |
| Native interop | FFI (Dart FFI) + platform channels | JNI easy; Obj-C harder | C-interop good; Android none | Wasm/JS bridges | FFI-first; best embed story | the interop target itself |
| Perf: pure geo math | adequate | good (JIT/AOT) | good | adequate (JIT) | excellent (SIMD-friendly) | excellent |
| Perf: large datasets/3D/terrain | adequate; GC pauses a risk | good | good (Apple HW) | adequate; needs native/Wasm assist | excellent (no-GC, threading) | excellent |
| Offline processing | good (Flutter fs/plugins) | excellent (Android fs/work) | good (iOS fs) | constrained (browser storage) | excellent (fs + threading + zero-dep bins) | excellent |
| Ergonomics (value types, null-safety, testing) | good (sound null-safety, test pkg) | excellent (data class, coroutines, rich tests) | good | good (npm test runners) | strict (ownership learning curve) | poor (manual memory, slow iteration) |
| Distribution | pub.dev | Maven/Gradle | SPM/CocoaPods | npm | crates.io + static/Wasm | system pkgs, painful mobile |
| Long-term portability | tied to Flutter's fate | tied to JVM/Android + KMP maturity | Apple-bound | most portable UI, weakest compute story | most portable compute, weakest UI story | portable but costly everywhere |

## 3. Architecture-shaped conclusion (PROPOSED, not a selection)

- The evidence favors a **platform-neutral core + platform adapters** shape regardless of language:
  pure geo/data/policy logic has no UI/renderer imports in either source family.
- Plausible shapes (all PROPOSED, trade-offs open):
  - (a) **Dart core** — fastest path to Recovery/Atlas-app reuse; risk: ties core to Flutter ecosystem, weakest GIS/3D story.
  - (b) **Kotlin core (incl. KMP)** — closest to Mantle + strong Android; risk: iOS/web maturity, Flutter interop cost.
  - (c) **Rust core + thin SDK bindings** — strongest portability/compute/FFI story for geo/terrain/analysis; risk: team ramp, UI still needs Dart/Kotlin/Swift shells, distribution complexity.
  - (d) **TypeScript core** — strongest web story; risk: mobile-offline + heavy-compute weakness.
  - (e) **Polyglot**: neutral core (Rust or portable Dart/Kotlin subset) + per-platform adapters. Most faithful to the portability requirement; highest governance cost.
- **No selection is made here.** DEC-014 remains DECISION REQUIRED. Phase 0.4 fixtures are specified
  to be language-neutral (coordinate-in/number-out vectors) precisely so the language decision
  does not invalidate them.
