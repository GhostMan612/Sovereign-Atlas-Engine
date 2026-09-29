---
description: Audit the engine/adapters boundary and header law, then report only violations
agent: build
---

Audit Sovereign Atlas for law violations. Do not edit anything.

Run the `atlas_purity_scan` tool first. It walks the pure-logic packages and reports every
forbidden import with `file:line`. Baseline as of this writing: 60 files scanned, 0
violations.

The pure packages that must never import MapLibre, Android, or Compose (RULES.md 2.1):
`core`, `geo`, `camera`, `layers`, `field`, `measure`, `goto`, `track`, `offline`,
`tactical`.

Then confirm the file-header law (RULES.md 1.1):
- All 113 tracked `.kt` files currently begin with the exact 4-line genesis signature.
  Any file that does not is a violation; new files must have it.
- No conversational or explanatory comments inside code. The genesis header is the only
  permitted comment.

Report violations as `path:line  law  offending-text`, then a one-line verdict. If clean,
say `BOUNDARY CLEAN` in one line and stop. Do not paste whole files, do not list passing
files, and do not speculate about violations you did not observe.
