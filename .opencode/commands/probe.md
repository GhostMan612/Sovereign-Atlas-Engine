---
description: Probe a large file without reading it whole (counts/preview)
---

Probe the file at `$ARGUMENTS` without loading the entire file into context.

- If no argument given, ask the user for a path.
- Run a short `python -c` script reporting: line count, size (KB), first 5 lines (truncated to 200 chars), and top-level shape if JSON/data.
- Do NOT read the whole file. Summarize the shape so the caller can decide what to read next.
- Never modify the probed file. Read-only.
