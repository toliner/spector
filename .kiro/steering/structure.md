# Project Structure

## Organization Philosophy

**Layered by concern**: Code is organized by functional layer (model, scanner, storage, api) rather than by feature. Each package has a single responsibility and clear dependencies flow downward.

## Directory Patterns

### Source Code (`src/main/kotlin/dev/toliner/spector/`)

**Location**: `model/`
**Purpose**: Data classes representing type system entities
**Pattern**: Immutable data classes with `@Serializable`, sealed hierarchies for variants
**Examples**: `ClassInfo`, `MemberInfo` (sealed), `TypeRef`, `FieldInfo`, `MethodInfo`

**Location**: `scanner/`
**Purpose**: Bytecode analysis and metadata extraction
**Pattern**: Stateless classes using ASM ClassVisitor pattern
**Examples**: `ClassScanner` (ASM), `KotlinMetadataEnricher` (kotlinx-metadata)

**Location**: `storage/`
**Purpose**: Persistence and query interface
**Pattern**: Repository-style class wrapping SQLite, implements `AutoCloseable`
**Example**: `TypeIndexer`

**Location**: `indexer/`
**Purpose**: Orchestration of scanning pipelines
**Pattern**: High-level coordinators that combine scanner + storage
**Examples**: `ClasspathIndexer`, `JavaStdLibIndexer`

**Location**: `api/`
**Purpose**: HTTP server and API configuration
**Pattern**: Ktor modules with extension functions for routing
**Examples**: `ApiServer`, `ApiConfiguration`, `ApiModels`

**Location**: `Main.kt`
**Purpose**: CLI entry point with subcommands
**Pattern**: `when` dispatch on command argument (index, serve, index-and-serve, help)

### Test Code (`src/test/kotlin/`)

**Pattern**: Mirror source structure with `Test` suffix
**Style**: Kotest FunSpec with `context`/`test` blocks
**Tagging**: Integration tests tagged with `Integration` for separate execution

### Supporting Files

**Location**: `tools/`
**Purpose**: Shell scripts for common operations
**Examples**: `spector`, `spector-index`, `spector-server`, `spector-api`

**Location**: `docs/`
**Purpose**: User documentation
**Example**: `usage.md`

## Naming Conventions

- **Files**: PascalCase for classes (e.g., `ClassScanner.kt`, `TypeIndexer.kt`)
- **Classes**: PascalCase, suffix indicates role (`*Info`, `*Indexer`, `*Scanner`)
- **Functions**: camelCase, verb-first (e.g., `scanClass`, `findMembersByOwner`)
- **Test classes**: Source class name + `Test` suffix
- **Enums**: PascalCase for type, SCREAMING_SNAKE for values

## Import Organization

```kotlin
// Standard library
import java.io.File
import java.sql.Connection

// Third-party libraries
import org.objectweb.asm.*
import io.ktor.server.application.*
import kotlinx.serialization.Serializable

// Project imports
import dev.toliner.spector.model.*
import dev.toliner.spector.scanner.ClassScanner
```

**Path Aliases**: None (standard Kotlin package imports)

## Code Organization Principles

### Dependency Direction

```
Main.kt -> indexer -> scanner -> model
                   -> storage -> model
         -> api    -> storage
```

- `model` has no internal dependencies (pure data)
- `scanner` depends only on `model` and ASM
- `storage` depends on `model` and SQLite
- `indexer` orchestrates scanner + storage
- `api` depends on storage for queries
- `Main.kt` wires everything together

### Sealed Hierarchies

When representing type variants, use sealed classes/interfaces:
- `MemberInfo` sealed with `FieldInfo`, `MethodInfo`, `PropertyInfo`
- `ClassKind` enum for CLASS, INTERFACE, ENUM, ANNOTATION, KOTLIN_OBJECT
- `TypeRef` sealed with ClassType, ArrayType, PrimitiveType, etc.

### Extension Points

New indexer types follow the pattern:
1. Create `*Indexer` class in `indexer/`
2. Accept `TypeIndexer` in constructor
3. Implement scanning logic using `ClassScanner` and `KotlinMetadataEnricher`
4. Call `typeIndexer.indexClass()` and `typeIndexer.indexMember()`

---
_Document patterns, not file trees. New files following patterns should not require updates_
