# Spector Usage Guide

Spector is a JVM Type Indexer that scans Java/Kotlin bytecode and provides a searchable index of classes and their members.

## Quick Start

### 1. Index Your Project

Index the Spector project itself as an example:

```bash
./tools/spector-index project types.db
```

This will:
- Build the project
- Extract all runtime dependencies
- Index classes and members into `types.db`

### 2. Start the API Server

```bash
./tools/spector-server start types.db
```

The server will start on port 8080. You can now query the indexed types.

### 3. Query the API

```bash
# Check server health
./tools/spector-api health

# List classes in a package
./tools/spector-api packages dev.toliner.spector

# List subpackages
./tools/spector-api subpackages dev.toliner

# Get class members
./tools/spector-api members dev.toliner.spector.storage.TypeIndexer
```

### 4. Stop the Server

```bash
./tools/spector-server stop
```

## Tools Overview

### spector

Direct CLI interface to the Spector application.

```bash
./tools/spector <command> [args...]
```

Available commands:
- `index <db-path> <classpath-entries...>` - Index classpath entries
- `serve <db-path> [port]` - Start API server
- `index-and-serve <db-path> <classpath-entries...>` - Index and serve
- `help` - Show help message

Example:
```bash
./tools/spector index types.db build/classes/kotlin/main lib/*.jar
./tools/spector serve types.db 8080
```

### spector-index

High-level indexing tool with convenient presets.

```bash
./tools/spector-index <command> <db-path> [options]
```

Commands:
- `project <db-path>` - Index this Spector project
- `gradle <db-path> <project-dir>` - Index a Gradle project
- `jars <db-path> <jar-files...>` - Index specific JAR files
- `dirs <db-path> <directories...>` - Index class directories

Examples:
```bash
# Index Spector project
./tools/spector-index project types.db

# Index specific JARs
./tools/spector-index jars types.db lib/*.jar

# Index compiled classes
./tools/spector-index dirs types.db build/classes/kotlin/main
```

### spector-server

Server management tool for starting, stopping, and monitoring the API server.

```bash
./tools/spector-server <command> [options]
```

Commands:
- `start <db-path>` - Start the server (port 8080)
- `stop` - Stop the server
- `restart <db-path>` - Restart the server
- `status` - Check server status
- `logs` - Show server logs (follow mode)

Examples:
```bash
# Start server on port 8080
./tools/spector-server start types.db

# Check status
./tools/spector-server status

# View logs
./tools/spector-server logs
```

Server management features:
- Background process with PID file tracking (stored in `.local/`)
- Graceful shutdown via API
- Log file output (stored in `.local/spector-server.log`)
- Status checking

Note: PID files and logs are stored in `.local/` directory (automatically created, excluded from git)

### spector-api

API client wrapper for querying the indexed types.

```bash
./tools/spector-api <command> [options]
```

Environment variables:
- `SPECTOR_API_BASE` - API base URL (default: http://localhost:8080)

Commands:
- `health` - Check server health
- `packages <package> [--recursive]` - List classes in package
- `subpackages <package>` - List subpackages
- `members <fqcn> [options]` - List class members
- `member-detail <json>` - Get detailed member information

Examples:
```bash
# Health check
./tools/spector-api health

# List classes in package
./tools/spector-api packages kotlin.collections

# List classes recursively
./tools/spector-api packages kotlin.collections --recursive

# List subpackages
./tools/spector-api subpackages kotlin

# List all members
./tools/spector-api members java.lang.String

# List only public methods
./tools/spector-api members java.lang.String --kind METHOD --visibility PUBLIC

# Get specific member details
./tools/spector-api member-detail '{"ownerFqcn":"java.lang.String","name":"length","jvmDesc":"()I"}'
```

Options for `packages`:
- `--recursive, -r` - Include subpackages
- `--public` - Only public classes (default)
- `--all` - All visibility levels

Options for `members`:
- `--kind, -k <kind>` - Filter by kind (FIELD, METHOD, CONSTRUCTOR, PROPERTY)
- `--visibility, -v <vis>` - Filter by visibility (PUBLIC, PROTECTED, PRIVATE, PACKAGE)
- `--synthetic` - Include synthetic members

## Complete Workflow Example

Here's a complete example of indexing a project and querying it:

```bash
# 1. Index the Spector project
./tools/spector-index project spector.db

# 2. Start the API server
./tools/spector-server start spector.db

# 3. Query the API
# List all packages
./tools/spector-api packages dev.toliner --recursive

# Check what's in the storage package
./tools/spector-api packages dev.toliner.spector.storage

# Get members of TypeIndexer class
./tools/spector-api members dev.toliner.spector.storage.TypeIndexer

# Filter to only public methods
./tools/spector-api members dev.toliner.spector.storage.TypeIndexer \
  --kind METHOD --visibility PUBLIC

# 4. Stop the server when done
./tools/spector-server stop
```

## API Endpoints

The server exposes the following REST API endpoints:

### Health Check
```
GET /health
```

Returns server status.

### List Classes
```
GET /v1/packages/{packageName}/classes?recursive=false&publicOnly=true
```

Parameters:
- `recursive` (boolean) - Include subpackages
- `publicOnly` (boolean) - Only return public classes

Returns list of classes in the package.

### List Subpackages
```
GET /v1/packages/{packageName}/subpackages
```

Returns list of direct subpackages.

### List Members
```
GET /v1/classes/{fqcn}/members?kind=METHOD&visibility=PUBLIC&includeSynthetic=false
```

Parameters:
- `kind` (optional) - Filter by kind: FIELD, METHOD, CONSTRUCTOR, PROPERTY
- `visibility` (optional) - Filter by visibility: PUBLIC, PROTECTED, PRIVATE, PACKAGE
- `includeSynthetic` (boolean) - Include synthetic members

Returns class members grouped by kind.

### Get Member Details
```
POST /v1/members/detail
Content-Type: application/json

{
  "ownerFqcn": "java.lang.String",
  "name": "length",
  "jvmDesc": "()I"
}
```

Returns detailed information about a specific member.

### Server Shutdown
```
POST /shutdown
```

Gracefully shuts down the server (only from localhost).

## Performance Tips

1. **Use transactions for bulk indexing** - The indexer automatically uses transactions for better performance.

2. **Indexing is fast** - With the optimizations:
   - Kotlin stdlib (~300 classes): ~1 second
   - Java stdlib (~4000 classes): ~5-10 seconds

3. **Sequential vs Parallel** - Sequential mode (`parallel=false`) is faster due to transaction batching.

4. **Database location** - For best performance, use a local SSD. The SQLite database uses WAL mode for better concurrent access.

5. **Incremental indexing** - The indexer uses `INSERT OR REPLACE`, so you can re-index without clearing the database.

## Troubleshooting

### Server won't start
Check logs:
```bash
./tools/spector-server logs
```

Common issues:
- Port already in use
- Database file not readable
- Insufficient permissions

### API returns empty results
- Verify data was indexed: Check database file size
- Verify server is using correct database: `./tools/spector-server status`
- Check package name spelling: Package names are case-sensitive

### Indexing is slow
- Check disk I/O - indexing is I/O intensive
- Verify SQLite optimizations are working (WAL mode, transactions)
- Monitor with: `./tools/spector-index project types.db` and watch progress messages

### jq not found warning
Install `jq` for pretty-printed JSON output:
```bash
# Ubuntu/Debian
sudo apt-get install jq

# macOS
brew install jq

# Fedora
sudo dnf install jq
```

## Advanced Usage

### Custom API Base URL
```bash
export SPECTOR_API_BASE=http://remote-server:8080
./tools/spector-api health
```

### Direct curl usage
```bash
# Get classes
curl "http://localhost:8080/v1/packages/kotlin.collections/classes?recursive=true" | jq '.'

# Get members
curl "http://localhost:8080/v1/classes/java.lang.String/members" | jq '.'
```

### Programmatic usage
You can also use the Gradle application plugin directly:

```bash
./gradlew run --args="serve types.db 8080"
./gradlew run --args="index types.db build/classes/kotlin/main"
```

## Database Schema

The SQLite database contains two main tables:

### types table
- `fqcn` (PRIMARY KEY) - Fully qualified class name
- `package_name` - Package name (indexed)
- `kind` - Class kind (CLASS, INTERFACE, ENUM, etc.)
- `modifiers` - Class modifiers (PUBLIC, FINAL, etc.)
- `super_class` - Superclass FQCN
- `interfaces` - Implemented interfaces
- `annotations` - Class annotations
- `kotlin_info` - Kotlin-specific metadata
- `data` - Full class information as JSON

### members table
- `id` (PRIMARY KEY) - Auto-increment ID
- `owner_fqcn` - Owning class FQCN (indexed)
- `name` - Member name
- `kind` - Member kind (FIELD, METHOD, CONSTRUCTOR, PROPERTY)
- `jvm_desc` - JVM descriptor
- `visibility` - Visibility (PUBLIC, PROTECTED, PRIVATE, PACKAGE)
- `static` - Is static member
- `data` - Full member information as JSON

## Contributing

When adding new tools:
1. Create shell script in `tools/`
2. Make it executable: `chmod +x tools/<script-name>`
3. Add documentation to this file
4. Follow the existing naming convention: `spector-<purpose>`
