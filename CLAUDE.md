# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spector is a Kotlin JVM project (version 0.1.0) developed by toliner. The project uses Kotlin 2.2.0 and targets Java 21 (Adoptium distribution).

## Build System

This project uses Gradle with Kotlin DSL. The Gradle wrapper is included in the repository.

### Common Commands

**Build the project:**
```bash
./gradlew build
```

**Run tests:**
```bash
./gradlew test
```

**Clean build artifacts:**
```bash
./gradlew clean
```

**Assemble JAR without running tests:**
```bash
./gradlew assemble
```

**Run all checks (includes tests):**
```bash
./gradlew check
```

**Run a single test class:**
```bash
./gradlew test --tests "dev.toliner.spector.YourTestClass"
```

**Run a single test method:**
```bash
./gradlew test --tests "dev.toliner.spector.YourTestClass.testMethodName"
```

## Code Structure

- **Package:** `dev.toliner.spector`
- **Source code:** `src/main/kotlin/dev/toliner/spector/`
- **Tests:** `src/test/kotlin/`
- **Resources:** `src/main/resources/` and `src/test/resources/`

## Technical Configuration

- **Kotlin Version:** 2.2.0
- **JVM Toolchain:** Java 21 (Adoptium)
- **Code Style:** Official Kotlin code style
- **Test Framework:** Kotest 6.0

## Testing
- Use FunSpec.

