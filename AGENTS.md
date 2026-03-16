# Repository Guidelines

## Project Structure & Module Organization
Core Kotlin sources live under `src/main/kotlin/dev/toliner/spector`. Key areas are `model` for shared data types, `scanner` for ASM and Kotlin metadata parsing, `storage` for SQLite indexing, `indexer` for classpath orchestration, and `api` for the Ktor server. Tests mirror that layout in `src/test/kotlin/dev/toliner/spector`, with `integration` reserved for slower end-to-end indexing checks. Runtime config is in `src/main/resources`, and helper scripts for indexing, serving, and API calls live in `tools/`.

## Build, Test, and Development Commands
Use the Gradle wrapper:

- `./gradlew build` compiles the app and runs the default test suite.
- `./gradlew test` runs fast tests only; the build excludes `Integration`-tagged tests by default.
- `./gradlew integrationTest` runs the slower Kotest integration suite.
- `./gradlew run --args="serve types.db 8080"` starts the HTTP API locally.
- `./tools/spector-index project types.db` indexes the current project with the provided wrapper script.

## Coding Style & Naming Conventions
Follow idiomatic Kotlin with 4-space indentation and one top-level class per file when practical. Keep package names under `dev.toliner.spector.*`. Use `PascalCase` for classes and test specs, `camelCase` for functions and properties, and descriptive names such as `ClasspathIndexer` or `KotlinMetadataEnricher`. Prefer small, focused classes over utility catch-alls. There is no dedicated formatter config in the repo, so match surrounding style and keep imports tidy.

## Testing Guidelines
Tests use Kotest `FunSpec` with JUnit 5. Name test files `*Test.kt` and write behavior-focused cases such as `test("should index and query Java standard library classes")`. Keep unit tests near the relevant package, and place full indexing flows in `src/test/kotlin/dev/toliner/spector/integration`. Use tags for slow coverage instead of expanding the default `test` task.

## Commit & Pull Request Guidelines
Recent history favors short, imperative commit subjects such as `Fix spector-server to use fixed port 8080` and `Add Java standard library indexing to index commands`. Keep commits scoped to one change. PRs should explain the behavior change, note affected commands or endpoints, link related issues, and include sample output or API examples when user-visible behavior changes.

## Security & Configuration Tips
Do not commit generated SQLite databases, PID files, or local runtime artifacts. Prefer wrapper scripts and `./gradlew` over machine-specific tooling, and validate any new API surface with tests before merging.

## Language
Japanese for documentation and response to user.

