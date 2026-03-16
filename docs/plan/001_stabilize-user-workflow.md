# Stabilize the Current User Workflow

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with [`.agent/PLANS.md`](/home/toliner/projects/spector/.agent/PLANS.md).

## Purpose / Big Picture

After this change, a new user can index a project, start the server, and query the API by following the repository documentation and tool scripts exactly as written. The main value of this work is not a new storage feature; it is that the public workflow now reflects the code that already exists. A user should be able to run one documented indexing command, one documented server command, and one documented query command without discovering hidden parameter names or undocumented endpoints.

## Progress

- [x] (2026-03-17 03:00 JST) Repository survey completed. Confirmed that `./gradlew test` passed before edits and that the highest-friction issues were documentation and wrapper drift.
- [x] (2026-03-17 03:40 JST) Updated `tools/spector-api` so package and member filters match the server contract and added wrapper commands for hierarchy, subclasses, and implementations.
- [x] (2026-03-17 03:40 JST) Rewrote `README.md`, `docs/tools-usage.md`, and `IMPLEMENTATION_SUMMARY.md` so the documented workflow and feature summary match the current implementation.
- [x] (2026-03-17 03:40 JST) Added `tools/smoke-user-workflow` as an executable end-to-end check for the documented user workflow.
- [x] (2026-03-17 03:06 JST) Ran `./gradlew test` and `./tools/smoke-user-workflow /tmp/spector-workflow.db`. Both completed successfully; the smoke test proved indexing, server startup, package listing, member filtering, and hierarchy lookup through the wrapper scripts.

## Surprises & Discoveries

- Observation: The largest usability break was not in Kotlin code but in shell and documentation drift.
  Evidence: `tools/spector-api` previously sent `kind=` while `src/main/kotlin/dev/toliner/spector/api/ApiConfiguration.kt` expects `kinds`.
- Observation: The repository already implements hierarchy and implementation queries, but those paths were absent from the wrapper and under-documented.
  Evidence: `ApiConfiguration.kt` defines `/v1/classes/{fqcn}/hierarchy`, `/v1/classes/{fqcn}/subclasses`, and `/v1/interfaces/{fqcn}/implementations`.
- Observation: The earlier plan draft itself contained a stale path to the ExecPlan instructions file.
  Evidence: The checked-in file is `.agent/PLANS.md`, not `.agents/PLANS.md`.

## Decision Log

- Decision: Keep verification at the shell-wrapper level instead of adding another Kotlin integration test.
  Rationale: The drift that broke the user workflow lived in wrapper scripts and docs, so the guard should exercise those same scripts directly.
  Date/Author: 2026-03-17 / Codex
- Decision: Rewrite the public-facing docs rather than applying small line edits.
  Rationale: The documents had drifted in multiple directions at once. A concise rewrite is easier for a novice reader to trust than a partially patched document with old framing left behind.
  Date/Author: 2026-03-17 / Codex

## Outcomes & Retrospective

The repository now has one clear wrapper-level workflow: index with `tools/spector-index`, serve with `tools/spector-server`, query with `tools/spector-api`, and validate the whole path with `tools/smoke-user-workflow`. Validation succeeded after adjusting the smoke script to strip ANSI color codes from `jq -C` output and to verify member filtering semantically with `jq` when available.

## Context and Orientation

The CLI entry point is `src/main/kotlin/dev/toliner/spector/Main.kt`. The HTTP API routes live in `src/main/kotlin/dev/toliner/spector/api/ApiConfiguration.kt`. The user-facing shell wrappers are `tools/spector-index`, `tools/spector-server`, and `tools/spector-api`. The public documentation for the quick path lives in `README.md` and `docs/tools-usage.md`. `IMPLEMENTATION_SUMMARY.md` affects future planning because contributors use it to decide what already exists.

In this repository, a wrapper script is a Bash command that hides a longer Gradle or curl invocation. If that wrapper script sends the wrong query parameter, a novice sees a clean shell command but gets misleading API results. The smoke test added by this plan intentionally goes through those wrappers rather than talking to Ktor directly.

## Plan of Work

First, make `tools/spector-api` match the server contract exactly. Package listing must send `kinds=` when class kinds are supplied. Member listing must send `kinds=` and keep `visibility=`, and it must expose `--inherited` because the server already supports inherited member queries. The wrapper must also expose direct commands for hierarchy, subclasses, and implementations because those endpoints already exist in `ApiConfiguration.kt`.

Second, align the public documentation with the implementation. `README.md` must link to `docs/tools-usage.md`, describe the project as an HTTP API server rather than an already-finished MCP server, and list the hierarchy-related endpoints that are implemented today. `docs/tools-usage.md` must document only real wrapper commands and real parameter names. `IMPLEMENTATION_SUMMARY.md` must stop claiming that member indexing and inheritance support are missing.

Third, add a repeatable shell-based proof under `tools/` so later edits can confirm the workflow still works end to end. The smoke script should index this project, start the server, query health, package listing, member filtering, and hierarchy, then stop the server and clean up its temporary database.

## Concrete Steps

Run these commands from `/home/toliner/projects/spector`.

```bash
./gradlew test
./tools/smoke-user-workflow /tmp/spector-workflow.db
```

Observed evidence, abbreviated:

```text
BUILD SUCCESSFUL
Workflow smoke test passed
```

## Validation and Acceptance

Acceptance is behavioral. A novice must be able to follow the repository docs and complete one full wrapper-based workflow without reading Kotlin code to discover missing details. `./gradlew test` must pass. `./tools/smoke-user-workflow /tmp/spector-workflow.db` must prove all of the following in one run: indexing succeeds, the server starts, `/health` returns JSON status, package listing returns `ok: true`, member listing applies a kind filter, and hierarchy lookup reaches the hierarchy endpoint successfully.

## Idempotence and Recovery

The documentation rewrites and wrapper changes are safe to repeat. The smoke script uses a temporary database path and deletes it during cleanup. If a stale PID file or background server remains, run `./tools/spector-server stop` once before retrying the smoke script.

## Artifacts and Notes

The critical evidence from the final run is:

```text
$ ./gradlew test
BUILD SUCCESSFUL

$ ./tools/smoke-user-workflow /tmp/spector-workflow.db
Checking health endpoint
Checking package listing
Checking member kind filter
Checking hierarchy endpoint
Workflow smoke test passed
```

## Interfaces and Dependencies

No new external library is required. The interfaces that must stay aligned are the CLI contract in `dev.toliner.spector.MainKt`, the HTTP contract in `dev.toliner.spector.api.configureApi`, and the user-facing shell wrappers in `tools/`. At completion, `tools/spector-api` must remain a direct mapping of those HTTP endpoints rather than a partial or renamed subset.

Revision note: Updated on 2026-03-17 to reflect the wrapper and documentation rewrite, correct the `PLANS.md` path, add `tools/smoke-user-workflow`, and record the successful validation transcript.
