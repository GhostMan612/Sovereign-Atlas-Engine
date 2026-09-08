# Phase 0.4 — Cross-Language Test Model (Normative)

- **Status:** PROPOSED. Must support unresolved DEC-014 (Dart / Kotlin / Swift / TypeScript / Rust / C++ / polyglot).
- **Rule:** fixtures are language-independent; NOTHING in `test/golden/` may import, name, or
  assume a language-specific type, SDK, or test framework.

## Model

- **Canonical input:** the fixture `inputs` object, parsed with a plain JSON parser in the target language.
- **Canonical output:** implementation produces its native values, then normalizes to the
  fixture's `expected` shape (field names, units, ordering) before comparison.
- **Comparison:** exact for strings/ints/bools/enums; tolerance-bounded for floats
  (`|actual − expected| ≤ tolerance`); rejection fixtures compare category + mandatory flag.
- **Tolerance:** per-fixture absolute tolerances; harness MUST NOT widen them silently.
- **Ordering:** significant unless `order_insensitive`; harness compares accordingly.
- **Errors:** compare `rejection.category` + `mandatory` only, never stack traces or SDK error text.
- **Versions:** harness checks `fixture_version`; unknown major versions fail loudly (no silent skip).
- **Fixture IDs:** the join key across languages; results are reported per `(fixture ID, implementation)`.

## Language notes (non-selecting)

- Dart / Kotlin / Swift / TypeScript / Rust / C++: all can parse canonical JSON and implement
  pure comparisons; FFI shapes (DEC-018) do not affect fixture content.
- If a language cannot represent a fixture input (e.g. non-finite literals), it uses the
  fixture's string encoding and converts at the boundary — the fixture does not change.
