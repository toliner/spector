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



# AI-DLC and Spec-Driven Development

Kiro-style Spec Driven Development implementation on AI-DLC (AI Development Life Cycle)

## Project Context

### Paths
- Steering: `.kiro/steering/`
- Specs: `.kiro/specs/`

### Steering vs Specification

**Steering** (`.kiro/steering/`) - Guide AI with project-wide rules and context
**Specs** (`.kiro/specs/`) - Formalize development process for individual features

### Active Specifications
- Check `.kiro/specs/` for active specifications
- Use `/kiro:spec-status [feature-name]` to check progress

## Development Guidelines
- Think in English, generate responses in Japanese. All Markdown content written to project files (e.g., requirements.md, design.md, tasks.md, research.md, validation reports) MUST be written in the target language configured for this specification (see spec.json.language).

## Minimal Workflow
- Phase 0 (optional): `/kiro:steering`, `/kiro:steering-custom`
- Phase 1 (Specification):
  - `/kiro:spec-init "description"`
  - `/kiro:spec-requirements {feature}`
  - `/kiro:validate-gap {feature}` (optional: for existing codebase)
  - `/kiro:spec-design {feature} [-y]`
  - `/kiro:validate-design {feature}` (optional: design review)
  - `/kiro:spec-tasks {feature} [-y]`
- Phase 2 (Implementation): `/kiro:spec-impl {feature} [tasks]`
  - `/kiro:validate-impl {feature}` (optional: after implementation)
- Progress check: `/kiro:spec-status {feature}` (use anytime)

## Development Rules
- 3-phase approval workflow: Requirements → Design → Tasks → Implementation
- Human review required each phase; use `-y` only for intentional fast-track
- Keep steering current and verify alignment with `/kiro:spec-status`
- Follow the user's instructions precisely, and within that scope act autonomously: gather the necessary context and complete the requested work end-to-end in this run, asking questions only when essential information is missing or the instructions are critically ambiguous.

## Steering Configuration
- Load entire `.kiro/steering/` as project memory
- Default files: `product.md`, `tech.md`, `structure.md`
- Custom files are supported (managed via `/kiro:steering-custom`)
