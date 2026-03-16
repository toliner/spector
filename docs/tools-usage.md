# Spector Usage Guide

Spector は Java/Kotlin バイトコードを走査し、クラスとメンバーを検索可能なインデックスとして提供する JVM Type Indexer です。

## Quick Start

### 1. Index Your Project

Spector 自身を例としてインデックス化します。

```bash
./tools/spector-index project types.db
```

このコマンドは以下を行います。
- プロジェクトをビルドする
- runtime classpath を取得する
- クラスとメンバーを `types.db` に格納する

### 2. Start the API Server

```bash
./tools/spector-server start types.db
```

サーバーは 8080 番ポートで起動します。

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

# Get hierarchy
./tools/spector-api hierarchy dev.toliner.spector.storage.TypeIndexer
```

### 4. Stop the Server

```bash
./tools/spector-server stop
```

## Tools Overview

### spector

Spector アプリケーションへの直接 CLI です。

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

便利なプリセットを持つ高水準のインデックス作成ツールです。

```bash
./tools/spector-index <command> <db-path> [options]
```

Commands:
- `project <db-path>` - Index this Spector project
- `gradle <db-path> <project-dir>` - Index a Gradle project
- `jars <db-path> <jar-files...>` - Index specific JAR files
- `dirs <db-path> <directories...>` - Index class directories
- `stdlib <db-path>` - Show stdlib indexing guidance

Examples:
```bash
./tools/spector-index project types.db
./tools/spector-index jars types.db lib/*.jar
./tools/spector-index dirs types.db build/classes/kotlin/main
```

### spector-server

API サーバーの起動、停止、確認を行う管理ツールです。

```bash
./tools/spector-server <command> [options]
```

Commands:
- `start <db-path>` - Start the server on port 8080
- `stop` - Stop the server
- `restart <db-path>` - Restart the server
- `status` - Check server status
- `logs` - Follow the server log

ログと PID ファイルは `.local/` に保存されます。

### spector-api

インデックス済みの型情報を問い合わせる API ラッパーです。

```bash
./tools/spector-api <command> [options]
```

Environment variables:
- `SPECTOR_API_BASE` - API base URL (default: `http://localhost:8080`)

Commands:
- `health` - Check server health
- `packages <package> [--recursive] [--kind <kind>]` - List classes in package
- `subpackages <package>` - List subpackages
- `members <fqcn> [options]` - List class members
- `hierarchy <fqcn> [--include-subclasses]` - Get class hierarchy
- `subclasses <fqcn> [--recursive]` - List subclasses
- `implementations <fqcn>` - List interface implementations
- `member-detail <json>` - Get detailed member information

Examples:
```bash
./tools/spector-api health
./tools/spector-api packages kotlin.collections --recursive
./tools/spector-api packages kotlin.collections --kind INTERFACE
./tools/spector-api members java.lang.String --kind METHOD --visibility PUBLIC
./tools/spector-api members java.lang.String --kind METHOD --inherited
./tools/spector-api hierarchy java.lang.String --include-subclasses
./tools/spector-api subclasses java.lang.Number --recursive
./tools/spector-api implementations java.util.List
./tools/spector-api member-detail '{"ownerFqcn":"java.lang.String","name":"length","jvmDesc":"()I"}'
```

Options for `packages`:
- `--recursive, -r` - Include subpackages
- `--kind, -k <kind>` - Filter by class kind: `CLASS`, `INTERFACE`, `ENUM`, `OBJECT`, `ANNOTATION`
- `--public` - Only public classes
- `--all` - Include non-public classes

Options for `members`:
- `--kind, -k <kind>` - Filter by kind: `FIELD`, `METHOD`, `CONSTRUCTOR`, `PROPERTY`
- `--visibility, -v <vis>` - Filter by visibility: `PUBLIC`, `PROTECTED`, `PRIVATE`, `PACKAGE`
- `--synthetic` - Include synthetic members
- `--inherited` - Include inherited members

## Complete Workflow Example

```bash
./tools/spector-index project spector.db
./tools/spector-server start spector.db
./tools/spector-api packages dev.toliner --recursive
./tools/spector-api packages dev.toliner.spector.storage
./tools/spector-api members dev.toliner.spector.storage.TypeIndexer --kind METHOD --visibility PUBLIC
./tools/spector-api hierarchy dev.toliner.spector.storage.TypeIndexer
./tools/spector-api implementations java.util.Collection
./tools/spector-server stop
```

## API Endpoints

### Health Check

```text
GET /health
```

Returns server status.

### List Classes

```text
GET /v1/packages/{packageName}/classes?recursive=false&publicOnly=true&kinds=CLASS,INTERFACE
```

Parameters:
- `recursive` - Include subpackages
- `publicOnly` - Only return public classes
- `kinds` - Comma-separated class kinds

### List Subpackages

```text
GET /v1/packages/{packageName}/subpackages
```

Returns direct subpackages.

### List Members

```text
GET /v1/classes/{fqcn}/members?kinds=METHOD&visibility=PUBLIC&includeSynthetic=false&inherited=false
```

Parameters:
- `kinds` - Comma-separated member kinds
- `visibility` - Comma-separated visibility filters
- `includeSynthetic` - Include synthetic members
- `inherited` - Include inherited members

### Get Member Details

```text
POST /v1/members/detail
```

Request body:

```json
{
  "ownerFqcn": "java.lang.String",
  "name": "length",
  "jvmDesc": "()I"
}
```

### Get Class Hierarchy

```text
GET /v1/classes/{fqcn}/hierarchy?includeSubclasses=false
```

Parameters:
- `includeSubclasses` - Include direct subclasses

### Get Subclasses

```text
GET /v1/classes/{fqcn}/subclasses?recursive=false
```

Parameters:
- `recursive` - Include transitive subclasses

### Get Interface Implementations

```text
GET /v1/interfaces/{fqcn}/implementations
```

Returns implementing classes for the given interface.

### Server Shutdown

```text
POST /shutdown
```

Gracefully shuts down the server from localhost only.

## Troubleshooting

### Server won't start

```bash
./tools/spector-server logs
```

Common issues:
- Port 8080 is already in use
- Database file cannot be read
- A stale PID file remains in `.local/`

### API returns empty results

- Confirm indexing completed successfully
- Confirm the server is using the expected database
- Check package and class names carefully because they are case-sensitive

### jq not found warning

`jq` があると `tools/spector-api` の出力が整形されます。

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

### Executable Workflow Smoke Test

```bash
./tools/smoke-user-workflow /tmp/spector-workflow.db
```
