# Technology Stack

## Architecture

Layered architecture with clear separation of concerns:

```
[CLI Entry] -> [Indexers] -> [Scanner/Enricher] -> [Storage] -> [API Server]
```

- **Scanner Layer**: Reads bytecode, extracts raw type information
- **Enricher Layer**: Enhances with Kotlin metadata
- **Storage Layer**: Persists to SQLite, provides query interface
- **API Layer**: Exposes HTTP endpoints via Ktor

## Core Technologies

- **Language**: Kotlin 2.2.0
- **Runtime**: JVM 21 (Adoptium)
- **Build System**: Gradle with Kotlin DSL

## Key Libraries

| Library | Purpose |
|---------|---------|
| ASM 9.7 | Bytecode analysis (ClassReader, ClassVisitor pattern) |
| kotlinx-metadata-jvm | Kotlin metadata parsing from @Metadata annotations |
| SQLite JDBC | Persistent storage and indexing |
| Ktor (Netty) | HTTP API server |
| kotlinx-serialization | JSON serialization for API and storage |

## Development Standards

### Type Safety

- Kotlin strict mode enabled
- Sealed hierarchies for type variants (ClassKind, MemberInfo)
- Data classes with explicit nullability for all model types

### Serialization

- All model classes annotated with `@Serializable`
- JSON format for both API responses and SQLite storage
- Custom class discriminator (`@class`) to avoid property conflicts

### Testing

- **Framework**: Kotest 6.0 with FunSpec style
- **Pattern**: Context-based test organization (`context { test { } }`)
- **Integration tests**: Tagged with `Integration`, excluded from default test run
- **Assertions**: Kotest matchers (`shouldBe`, `shouldContain`, `shouldNotBe`)

## Development Environment

### Required Tools

- JDK 21 (Adoptium recommended)
- Gradle 8.x (wrapper included)

### Common Commands

```bash
# Build
./gradlew build

# Run tests (excludes integration)
./gradlew test

# Run integration tests
./gradlew integrationTest

# Run application
./gradlew run --args="<command> <args...>"

# Get runtime classpath
./gradlew -q printRuntimeCp
```

## Key Technical Decisions

### ASM ClassVisitor Pattern

Using ASM's visitor pattern with `ClassReader.SKIP_CODE | SKIP_DEBUG` flags for efficient scanning - we only need type signatures, not method bodies.

### SQLite over In-Memory

Chose SQLite for persistence to enable:
- Pre-built indexes that survive restarts
- Sharing indexes between processes
- Incremental updates (future)

### Sealed Class for MemberInfo

`MemberInfo` is a sealed class with `FieldInfo`, `MethodInfo`, `PropertyInfo` variants. This ensures exhaustive handling in when-expressions and type-safe serialization.

### Kotlin Metadata Enrichment

Kotlin classes are first scanned for Java-level information, then enriched with Kotlin metadata. This two-pass approach keeps the scanner simple while preserving full Kotlin semantics.

---
_Document standards and patterns, not every dependency_
