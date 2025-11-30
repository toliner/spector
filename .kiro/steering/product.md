# Product Overview

Spector is a JVM type indexer designed to provide type information for AI agents working with Java/Kotlin projects. It extracts, stores, and serves type metadata from compiled bytecode without loading classes (side-effect free static analysis).

## Core Capabilities

- **Bytecode Analysis**: Scan JAR files and class directories using ASM to extract type information without executing code
- **Kotlin Metadata Support**: Parse Kotlin-specific metadata (nullability, suspend functions, extension receivers, data/sealed classes)
- **Type Index Storage**: Persist extracted type information to SQLite for fast querying
- **HTTP API**: Serve type information via RESTful endpoints for consumption by AI agents and tools

## Target Use Cases

- **AI Code Assistants**: Provide accurate type information to AI agents generating or analyzing JVM code
- **IDE-like Intelligence**: Enable type lookups, member queries, and inheritance resolution without a full IDE
- **MCP Server (Future)**: Serve as a Model Context Protocol server for AI tool integration

## Value Proposition

- **Safe Analysis**: No class loading means no static initializers, no side effects, no security risks
- **Kotlin-aware**: Full support for Kotlin-specific constructs that are invisible in raw bytecode
- **Lightweight**: SQLite-based storage enables portable, file-based type indexes
- **Query-optimized**: Indexed for common queries (by package, by class, by member signature)

---
_Focus on patterns and purpose, not exhaustive feature lists_
