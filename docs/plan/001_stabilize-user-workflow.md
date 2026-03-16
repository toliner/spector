# Stabilize the Current User Workflow

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with [`.agents/PLANS.md`](/home/toliner/projects/spector/.agents/PLANS.md).

## Purpose / Big Picture

After this change, a new user can index a project, start the server, and query the API by following the repository documentation and tool scripts exactly as written. Today the core Kotlin code works and `./gradlew test` passes, but the public workflow is inconsistent: `README.md` points to `docs/usage.md`, which does not exist; `tools/spector-api` sends `kind=` while the server reads `kinds=`; several implemented HTTP endpoints are not exposed by the wrapper scripts or docs; and `IMPLEMENTATION_SUMMARY.md` still says that member indexing and inheritance are not implemented even though they are present in `src/main/kotlin/dev/toliner/spector/storage/TypeIndexer.kt`. This plan makes the repository honest and usable before larger feature work continues.

## Progress

- [x] (2026-03-17 03:00 JST) Repository survey completed. Confirmed that `./gradlew test` passes and that the highest-friction gaps are documentation and wrapper inconsistencies rather than failing core tests.
- [ ] Align `README.md`, `docs/tools-usage.md`, `IMPLEMENTATION_SUMMARY.md`, and `AGENTS.md` with the current implementation and actual file paths.
- [ ] Fix `tools/spector-api` so its query parameter names and supported subcommands match `src/main/kotlin/dev/toliner/spector/api/ApiConfiguration.kt`.
- [ ] Add end-to-end tests or script-level smoke checks that prove the documented commands actually work.
- [ ] Record final observed command transcripts and update this plan with completion notes.

## Surprises & Discoveries

- Observation: The repository rule in `AGENTS.md` references `.agent/PLANS.md`, but the checked-in file is `.agents/PLANS.md`.
  Evidence: `rg -n "PLANS" AGENTS.md` and `git ls-tree -r --name-only df11f97` show `.agents/PLANS.md`.
- Observation: `README.md` links to `docs/usage.md`, but only `docs/tools-usage.md` exists.
  Evidence: `rg --files` returned `docs/tools-usage.md` and no `docs/usage.md`.
- Observation: The API wrapper is out of sync with the server contract.
  Evidence: `tools/spector-api` sends `kind=` while `configureApi` parses `kinds`; the wrapper also lacks commands for `/v1/classes/{fqcn}/hierarchy`, `/v1/classes/{fqcn}/subclasses`, and `/v1/interfaces/{fqcn}/implementations`.
- Observation: The implementation summary is stale enough to mislead planning.
  Evidence: `IMPLEMENTATION_SUMMARY.md` lists member indexing and inheritance resolution as unimplemented, but `TypeIndexer.kt` already defines `indexMember`, `findInheritedMembers`, `findSubclasses`, and `findImplementations`.

## Decision Log

- Decision: Prioritize workflow stabilization before new feature work.
  Rationale: The repository already delivers useful behavior, but the advertised entry points do not describe it accurately. Fixing this improves adoption immediately and reduces the chance of building new features on top of incorrect documentation.
  Date/Author: 2026-03-17 / Codex
- Decision: Validate documentation with executable checks rather than prose-only updates.
  Rationale: The current drift happened because docs and scripts were allowed to diverge. A repeatable smoke test reduces recurrence.
  Date/Author: 2026-03-17 / Codex

## Outcomes & Retrospective

This plan is still a draft. The expected outcome is a repository where the fastest path for a new user is the documented path, not a path inferred by reading the source. Success means that a novice can follow one documented indexing flow and one documented query flow without editing the project or discovering hidden API details.

## Context and Orientation

The entry point for the CLI is `src/main/kotlin/dev/toliner/spector/Main.kt`. The HTTP routes are defined in `src/main/kotlin/dev/toliner/spector/api/ApiConfiguration.kt`. The three shell wrappers that new users are expected to touch are `tools/spector-index`, `tools/spector-server`, and `tools/spector-api`. The high-level documentation lives in `README.md`, while the more command-focused guide is `docs/tools-usage.md`. `IMPLEMENTATION_SUMMARY.md` is not part of runtime behavior, but it strongly affects planning because it states what is and is not implemented.

In this repository, a "wrapper script" means a small Bash command under `tools/` that invokes `./gradlew run` or `curl` for the user. If a wrapper and the server disagree on parameter names, the user sees a successful shell invocation that silently returns the wrong result set. A "smoke test" means a short, end-to-end check that proves a basic workflow works, such as indexing a small classpath, starting the server, and receiving an expected JSON response.

## Plan of Work

Start by reconciling the authoritative behavior in Kotlin code with every user-facing document and script. In `README.md`, replace broken links, remove claims that the project is already an MCP server if only HTTP is present, and document the hierarchy and implementation-query endpoints that already exist. In `docs/tools-usage.md`, ensure every listed command is implemented in the wrappers and every implemented wrapper command is documented. In `IMPLEMENTATION_SUMMARY.md`, update the status of member indexing and inheritance support so future planning starts from the current code rather than an older snapshot. In `AGENTS.md`, fix the `PLANS.md` path reference so future ExecPlans point at the real file.

Then update `tools/spector-api` to match the HTTP server exactly. The wrapper should send `kinds=` for member filtering, continue using `visibility=`, and add first-class commands for hierarchy, subclasses, and interface implementations. If the wrapper exposes booleans such as recursive or include-subclasses, name them to match the server query parameters so curl transcripts can be copied directly into docs.

After the text and wrappers are aligned, add verification. The lightest acceptable option is a new shell-based smoke test script under `tools/` or a focused Kotlin integration test that exercises one documented wrapper path. The important point is that the repository gains one executable guard against this class of drift.

## Concrete Steps

Run these commands from `/home/toliner/projects/spector`.

```bash
./gradlew test
./tools/spector-index project /tmp/spector-workflow.db
./tools/spector-server start /tmp/spector-workflow.db
./tools/spector-api health
./tools/spector-api packages dev.toliner.spector.storage
./tools/spector-api members dev.toliner.spector.storage.TypeIndexer --kind METHOD
./tools/spector-api hierarchy dev.toliner.spector.storage.TypeIndexer
./tools/spector-server stop
```

Expected evidence, abbreviated:

```text
BUILD SUCCESSFUL
{
  "status": "ok"
}
{
  "ok": true,
  "data": {
    "packageName": "dev.toliner.spector.storage",
    "classes": [...]
  }
}
```

If wrapper commands change during implementation, update this section in the same commit so the plan remains executable.

## Validation and Acceptance

Acceptance is behavioral. A novice must be able to follow the README or `docs/tools-usage.md` and complete one full workflow without discovering undocumented requirements. `./gradlew test` must still pass. A wrapper-based smoke flow must prove at least these behaviors: indexing completes, `/health` returns HTTP 200 with JSON status, package listing returns `ok: true`, member listing honors the requested kind filter, and the new wrapper command for hierarchy or subclasses reaches the corresponding server endpoint and returns JSON.

## Idempotence and Recovery

This work is additive and safe to repeat. Documentation edits and wrapper fixes can be rerun without side effects. Use a temporary database path such as `/tmp/spector-workflow.db` during validation so repeated runs do not pollute the repository. If the server PID file under `.local/` becomes stale, run `./tools/spector-server stop` once to clean it up before retrying.

## Artifacts and Notes

Keep the final patch focused on truthfulness and operability. Include short transcripts showing that the documented commands now succeed. If a wrapper command name changes, include one before/after example in the commit message or this plan so future contributors understand the migration.

## Interfaces and Dependencies

No new external library is required for this plan. The main interfaces that must remain aligned are the CLI contract in `dev.toliner.spector.MainKt`, the HTTP contract in `dev.toliner.spector.api.configureApi`, and the Bash wrappers under `tools/`. At completion, `tools/spector-api` must expose subcommands that map directly onto the currently implemented server endpoints, and the documentation must describe those commands using their exact names and arguments.

Revision note: Draft created on 2026-03-17 after repository survey to capture the highest-priority stabilization work before new feature development.
