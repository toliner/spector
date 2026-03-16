# Add Robust Gradle Project Indexing

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with [`.agent/PLANS.md`](/home/toliner/projects/spector/.agent/PLANS.md).

## Purpose / Big Picture

After this change, a user can point Spector at an arbitrary Gradle project and build an index without modifying that target project's build scripts. Today `tools/spector-index gradle` only works if the target project already defines a custom `printRuntimeCp` task, which defeats the purpose of a reusable indexer. This plan replaces that fragile requirement with direct Gradle Tooling API access, so Spector can discover runtime classpaths from outside the target project and then feed those paths into the existing indexer.

## Progress

- [x] (2026-03-17 03:00 JST) Repository survey completed. Confirmed that current `tools/spector-index gradle` shells into another project and aborts unless `printRuntimeCp` already exists.
- [x] (2026-03-17 03:12 JST) Designed `GradleRuntimeClasspathResolver` as a narrow adapter that connects to a target build with Gradle Tooling API, injects an init script, and extracts `sourceSets.main.runtimeClasspath` without editing the target repository.
- [x] (2026-03-17 03:16 JST) Added `index-gradle` to `src/main/kotlin/dev/toliner/spector/Main.kt` and rewired `tools/spector-index gradle` to use that command as the default flow.
- [x] (2026-03-17 03:20 JST) Added `src/test/fixtures/gradle-simple` plus `src/test/kotlin/dev/toliner/spector/integration/GradleProjectIndexingIntegrationTest.kt` to prove indexing works for a stock Gradle project with no `printRuntimeCp` task.
- [x] (2026-03-17 03:22 JST) Updated `README.md` and `docs/tools-usage.md` with the supported Gradle assumptions and the new wrapper-based workflow.
- [x] (2026-03-17 03:24 JST) Verified `./gradlew test` and `./gradlew integrationTest` after the implementation.

## Surprises & Discoveries

- Observation: The README architecture diagram already mentions "Tooling API / Gradle task", but the implementation only supports the "Gradle task" half.
  Evidence: `README.md` contains that diagram; `tools/spector-index` checks for `printRuntimeCp` and prints manual instructions if it is missing.
- Observation: The current wrapper is tightly coupled to another repository's build logic.
  Evidence: `tools/spector-index` changes into the target project, runs `./gradlew tasks --all`, and demands a custom task before indexing.
- Observation: Pulling `org.gradle:gradle-tooling-api` from Maven Central does not work in this repository because that artifact is not published there in the expected coordinates.
  Evidence: `./gradlew test --tests dev.toliner.spector.integration.GradleProjectIndexingIntegrationTest` failed until the dependency was switched to `gradleApi()`.
- Observation: In an init script, top-level `tasks` resolves against the `Gradle` object rather than a `Project`.
  Evidence: The first resolver attempt failed with `MissingPropertyException: Could not get unknown property 'tasks' for build of type org.gradle.invocation.DefaultGradle.` and was fixed by registering the task via `gradle.rootProject { rootProject -> ... }`.

## Decision Log

- Decision: Make external Gradle indexing the second priority after workflow stabilization.
  Rationale: The project's core value is indexing dependencies of other JVM projects. Requiring users to patch each target build file is a major adoption barrier, but it is easier to tackle safely once the current workflow and docs are accurate.
  Date/Author: 2026-03-17 / Codex
- Decision: Keep the existing class scanner and SQLite storage unchanged as much as possible.
  Rationale: The weak point is classpath discovery, not bytecode scanning. Reusing the stable pipeline reduces risk.
  Date/Author: 2026-03-17 / Codex
- Decision: Use Tooling API plus a temporary init script instead of trying to derive runtime classpaths from public IDE models.
  Rationale: Public Tooling API models do not expose `sourceSets.main.runtimeClasspath` directly in a stable, scanner-ready form. Injecting a task through an init script keeps the target project unmodified while still letting Spector ask Gradle for the exact files it should index.
  Date/Author: 2026-03-17 / Codex
- Decision: Support project roots that expose either `sourceSets.main.runtimeClasspath` or `runtimeClasspath`, and run `classes` first when available.
  Rationale: This captures the common JVM Gradle case and ensures the index contains both dependency JARs and the target project's compiled classes.
  Date/Author: 2026-03-17 / Codex

## Outcomes & Retrospective

This work closed the largest usability gap in cross-project indexing. A user can now run one command against a stock Gradle JVM project and obtain a database without editing that project or adding temporary tasks. The existing raw-classpath commands remain unchanged, while `tools/spector-index gradle` now resolves the target runtime classpath internally through Gradle Tooling API and indexes both compiled project classes and dependency jars.

## Context and Orientation

The current wrapper entry point is `tools/spector-index`. The actual indexing logic lives in `src/main/kotlin/dev/toliner/spector/indexer/ClasspathIndexer.kt` and `src/main/kotlin/dev/toliner/spector/indexer/JavaStdLibIndexer.kt`, which already accept a list of filesystem entries and store results through `src/main/kotlin/dev/toliner/spector/storage/TypeIndexer.kt`. The missing piece is classpath discovery for an external build.

The "Gradle Tooling API" is Gradle's programmatic interface for querying another Gradle build. In plain terms, it lets one JVM program ask a Gradle project for information such as models or task results without editing that project's source files. In this repository, using it means Spector can discover `runtimeClasspath` from the target build itself instead of asking the user to define a helper task manually.

## Plan of Work

Add a new Kotlin class at `src/main/kotlin/dev/toliner/spector/indexer/GradleRuntimeClasspathResolver.kt`. It accepts a target project directory, validates that the directory looks like a Gradle build root, connects through `org.gradle.tooling.GradleConnector`, injects a temporary init script, runs a synthetic `spectorPrintRuntimeClasspath` task, and parses the emitted file list into `List<File>`. The init script resolves `sourceSets.main.runtimeClasspath` when available, falls back to `runtimeClasspath` for simpler JVM builds, and depends on `classes` when that task exists so compiled classes are present before indexing.

Expose this capability through the existing user flow rather than leaving it as a hidden library. `src/main/kotlin/dev/toliner/spector/Main.kt` now provides `index-gradle <db-path> <project-dir>` and reuses the existing classpath indexing pipeline after resolving the Gradle runtime classpath. `tools/spector-index` now calls that command for `gradle`, which removes the old requirement to shell into the target project and look for a `printRuntimeCp` task.

Create a fixture project under `src/test/fixtures/gradle-simple` with the `java` plugin, no custom classpath tasks, and a file-based dependency at `libs/external.jar`. `src/test/kotlin/dev/toliner/spector/integration/GradleProjectIndexingIntegrationTest.kt` copies that fixture to a temporary directory, creates the dependency jar on the fly, resolves the fixture's runtime classpath through `GradleRuntimeClasspathResolver`, indexes it into a temporary SQLite database, and verifies that both `com.example.app.FixtureApp` and `com.example.external.ExternalDependency` are queryable. The same test file also covers the understandable failure mode for a non-Gradle directory.

## Concrete Steps

Run these commands from `/home/toliner/projects/spector`.

```bash
./gradlew test --tests dev.toliner.spector.integration.GradleProjectIndexingIntegrationTest
./gradlew test
./gradlew integrationTest
./tools/spector-index gradle /tmp/fixture.db src/test/fixtures/gradle-simple
./tools/spector-server start /tmp/fixture.db
./tools/spector-api packages com.example --recursive
./tools/spector-server stop
```

Expected evidence, abbreviated:

```text
Resolved runtime classpath for /tmp/.../gradle-simple
Indexing complete
{
  "ok": true,
  "data": {
    "packageName": "com.example",
    "classes": [...]
  }
}
```

During implementation the first three commands completed successfully. The fixture command remains the manual end-to-end check to run outside the test suite because the committed fixture expects `libs/external.jar` to be created by the test harness before indexing.

## Validation and Acceptance

Acceptance requires a target Gradle project that does not define `printRuntimeCp`. The new flow must still resolve its runtime classpath and produce an index. `GradleProjectIndexingIntegrationTest` now proves the positive path and the non-Gradle-directory failure mode. The user-visible wrapper command no longer instructs the user to edit the target build script, and the docs now define the supported scope as JVM Gradle project roots that expose either `sourceSets.main.runtimeClasspath` or `runtimeClasspath`.

## Idempotence and Recovery

Using a fixture project and a temporary SQLite path makes this safe to repeat. The committed fixture uses a file dependency that the test harness creates locally, so validation does not depend on internet access. If a partial database is created, it is safe to delete the temporary `.db` file and rerun the command. The temporary init script used by `GradleRuntimeClasspathResolver` is recreated on each run and deleted afterwards.

## Artifacts and Notes

Keep one concise transcript showing the old failure mode disappearing. The implementation also leaves behind a concrete fixture at `src/test/fixtures/gradle-simple` that demonstrates the intended target shape: standard `java` plugin, no helper tasks, and a resolvable runtime classpath.

## Interfaces and Dependencies

This plan adds a dependency on Gradle's runtime API through `gradleApi()`. `GradleRuntimeClasspathResolver.resolve(projectDir: File): List<File>` returns plain `List<File>` values so existing `ClasspathIndexer.indexClasspath` can be reused unchanged. The public command surface now includes `index-gradle <db-path> <gradle-project-dir>` and `tools/spector-index gradle <db-path> <gradle-project-dir>` for indexing a supported Gradle project without modifying that project.

Revision note: Draft created on 2026-03-17 after confirming that the current Gradle indexing flow depends on a custom task in the target project.
Revision note: Updated on 2026-03-17 after implementation to record the init-script-based Tooling API approach, the fixture-backed integration test, the documentation changes, and the successful `./gradlew test` plus `./gradlew integrationTest` verification.
