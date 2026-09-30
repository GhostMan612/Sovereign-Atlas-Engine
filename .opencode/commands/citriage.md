---
description: Triage the red Actions run: identify the first failing step and whether tests ran
agent: build
---

Diagnose the GitHub Actions failure for `GhostMan612/Sovereign-Atlas-Engine`. Do not edit.

Logs on the run page require sign-in, and `gh` is not installed, so use the unauthenticated
REST API from PowerShell:

    $r = Invoke-RestMethod -Uri "https://api.github.com/repos/GhostMan612/Sovereign-Atlas-Engine/actions/runs?per_page=5" -Headers @{ "User-Agent" = "atlas" }
    $r.workflow_runs | ForEach-Object { "$($_.run_number) $($_.head_sha.Substring(0,7)) $($_.conclusion)" }

Then, for the run of interest, get per-step conclusions. This is the step that matters,
because the job-level conclusion alone hides a setup failure:

    $id = $r.workflow_runs[0].id
    $j = Invoke-RestMethod -Uri "https://api.github.com/repos/GhostMan612/Sovereign-Atlas-Engine/actions/runs/$id/jobs" -Headers @{ "User-Agent" = "atlas" }
    $j.jobs | ForEach-Object { $_.steps | ForEach-Object { "$($_.name) -> $($_.conclusion)" } }

Read the result this way:
- A job that fails in seconds with the Unit tests step SKIPPED is an infrastructure or setup
  failure, not application code. That was the case for 60 consecutive runs at
  `android-actions/setup-android@v3`; it is now pinned to cmdline-tools 11076708 with an
  explicit package list and licence acceptance.
- If the Unit tests step ran, report its conclusion and the pass/fail count. A compile error
  in a step means application code.

Report: run number, head sha, first failing step, whether tests ran, and a one-line verdict
of infrastructure versus application. If the API is unreachable, say so instead of guessing
from the HTML page.
