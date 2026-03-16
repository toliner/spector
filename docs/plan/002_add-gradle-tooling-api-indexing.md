# Add Robust Gradle Project Indexing

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with [`.agents/PLANS.md`](/home/toliner/projects/spector/.agents/PLANS.md).

## Purpose / Big Picture

After this change, a user can point Spector at an arbitrary Gradle project and build an index without modifying that target project's build scripts. Today `tools/spector-index gradle` only works if the target project already defines a custom `printRuntimeCp` task, which defeats the purpose of a reusable indexer. This plan replaces that fragile requirement with direct Gradle Tooling API access, so Spector can discover runtime classpaths from outside the target project and then feed those paths into the existing indexer.

## Progress

- [x] (2026-03-17 03:00 JST) Repository survey completed. Confirmed that current `tools/spector-index gradle` shells into another project and aborts unless `printRuntimeCp` already exists.
- [ ] Design a small internal adapter that asks Gradle for one or more classpath variants without requiring repository edits in the target project.
- [ ] Implement the adapter and connect it to either `Main.kt` or `tools/spector-index` so the improved path becomes the default user flow.
- [ ] Add tests against a fixture Gradle project that does not define `printRuntimeCp`.
- [ ] Document the supported Gradle assumptions and fallback behavior.

## Surprises & Discoveries

- Observation: The README architecture diagram already mentions "Tooling API / Gradle task", but the implementation only supports the "Gradle task" half.
  Evidence: `README.md` contains that diagram; `tools/spector-index` checks for `printRuntimeCp` and prints manual instructions if it is missing.
- Observation: The current wrapper is tightly coupled to another repository's build logic.
  Evidence: `tools/spector-index` changes into the target project, runs `./gradlew tasks --all`, and demands a custom task before indexing.

## Decision Log

- Decision: Make external Gradle indexing the second priority after workflow stabilization.
  Rationale: The project's core value is indexing dependencies of other JVM projects. Requiring users to patch each target build file is a major adoption barrier, but it is easier to tackle safely once the current workflow and docs are accurate.
  Date/Author: 2026-03-17 / Codex
- Decision: Keep the existing class scanner and SQLite storage unchanged as much as possible.
  Rationale: The weak point is classpath discovery, not bytecode scanning. Reusing the stable pipeline reduces risk.
  Date/Author: 2026-03-17 / Codex

## Outcomes & Retrospective

This draft targets the largest usability gap in cross-project indexing. Success means that a user can run one command against a stock Gradle project and obtain a database without editing that project or adding temporary tasks. The outcome should preserve today's direct classpath indexing commands for users who already have raw paths.

## Context and Orientation

The current wrapper entry point is `tools/spector-index`. The actual indexing logic lives in `src/main/kotlin/dev/toliner/spector/indexer/ClasspathIndexer.kt` and `src/main/kotlin/dev/toliner/spector/indexer/JavaStdLibIndexer.kt`, which already accept a list of filesystem entries and store results through `src/main/kotlin/dev/toliner/spector/storage/TypeIndexer.kt`. The missing piece is classpath discovery for an external build.

The "Gradle Tooling API" is Gradle's programmatic interface for querying another Gradle build. In plain terms, it lets one JVM program ask a Gradle project for information such as models or task results without editing that project's source files. In this repository, using it means Spector can discover `runtimeClasspath` from the target build itself instead of asking the user to define a helper task manually.

## Plan of Work

Add a new Kotlin module or package under `src/main/kotlin/dev/toliner/spector/indexer` or a nearby package dedicated to Gradle integration. This component should accept a target project directory and return the runtime classpath entries as `File` objects. Keep the public API narrow, for example one function that resolves a named Gradle project and configuration into a list of files.

Expose this capability through the existing user flow rather than leaving it as a hidden library. The simplest path is to teach `Main.kt` a new command such as `index-gradle <db-path> <project-dir>`, then update `tools/spector-index gradle` to call that command instead of running `./gradlew -q printRuntimeCp` in the target project. If a direct Kotlin command is not practical, the wrapper may still invoke Gradle Tooling API through the main application process, but the logic should live in Kotlin so it is testable.

Create at least one fixture Gradle project under `src/test/resources` or a dedicated test fixture directory. The fixture should be minimal, use standard plugins, define no custom classpath tasks, and have at least one external dependency so the test proves that both compiled classes and resolved jars are discovered. Add an integration-style test that invokes the new adapter, indexes the resulting classpath into a temporary SQLite file, and verifies that a known dependency class can be queried.

## Concrete Steps

Run these commands from `/home/toliner/projects/spector`.

```bash
./gradlew test
./gradlew integrationTest
./tools/spector-index gradle /tmp/fixture.db src/test/fixtures/gradle-simple
./tools/spector-server start /tmp/fixture.db
./tools/spector-api packages com.example
./tools/spector-server stop
```

Expected evidence, abbreviated:

```text
Resolved runtime classpath for src/test/fixtures/gradle-simple
Indexing complete
{
  "ok": true,
  "data": {
    "packageName": "com.example",
    "classes": [...]
  }
}
```

If the final command shape changes, rewrite this section immediately so the plan stays directly runnable.

## Validation and Acceptance

Acceptance requires a target Gradle project that does not define `printRuntimeCp`. The new flow must still resolve its runtime classpath and produce an index. Tests must cover both the positive path and an understandable failure mode, such as a non-Gradle directory or a Gradle project that cannot resolve dependencies. The user-visible wrapper command must no longer instruct the user to edit the target build script.

## Idempotence and Recovery

Using a fixture project and a temporary SQLite path makes this safe to repeat. If dependency resolution fails because the fixture needs internet access during development, document that prerequisite explicitly and keep the failure message actionable. If a partial database is created, it is safe to delete the temporary `.db` file and rerun the command.

## Artifacts and Notes

Keep one concise transcript showing the old failure mode disappearing. A useful artifact is a before/after example of `tools/spector-index gradle` against a fixture project with no custom helper task.

## Interfaces and Dependencies

This plan adds a direct dependency on Gradle's Tooling API. The new Kotlin interface should return plain `List<File>` values so existing `ClasspathIndexer.indexClasspath` can be reused unchanged. At completion, the public command surface must include one supported path for indexing an arbitrary Gradle project without modifying that project.

Revision note: Draft created on 2026-03-17 after confirming that the current Gradle indexing flow depends on a custom task in the target project.
