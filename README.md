# Spector - JVM Type Indexer PoC

AI エージェント向けに Java/Kotlin の依存ライブラリ型情報を提供する HTTP API サーバーの PoC 実装です。

## 概要

Spector は Gradle プロジェクトのクラスパスから型情報を抽出し、SQLite データベースにインデックス化して、HTTP API 経由でクエリ可能にします。

### 主な機能

- ASM ベースのクラススキャン
- Kotlin メタデータサポート
- SQLite ストレージ
- HTTP API
- 継承階層、サブクラス、実装クラスの問い合わせ

### アーキテクチャ

```text
[Gradle Project]
   ↓
[runtimeClasspath の JAR/ディレクトリ]
   ↓
[ClassScanner (ASM)] → [KotlinMetadataEnricher] → [TypeIndexer (SQLite)]
   ↓
[HTTP API Server (Ktor)]
```

## ビルド

```bash
./gradlew build
```

## 使用方法

詳細な使い方は [docs/tools-usage.md](docs/tools-usage.md) を参照してください。

### クイックスタート

```bash
./tools/spector-index project types.db
./tools/spector-server start types.db
./tools/spector-api health
./tools/spector-api packages dev.toliner.spector
./tools/spector-api members dev.toliner.spector.storage.TypeIndexer --kind METHOD
./tools/spector-api hierarchy dev.toliner.spector.storage.TypeIndexer
./tools/spector-server stop
```

### Gradle プロジェクトを直接インデックスする方法

```bash
./tools/spector-index gradle types.db /path/to/gradle-project
./gradlew run --args="serve types.db 8080"
```

`tools/spector-index gradle` は Gradle Tooling API を使うため、対象プロジェクト側に `printRuntimeCp` のような補助タスクを追加する必要はありません。対象は `sourceSets.main.runtimeClasspath` か `runtimeClasspath` を持つ JVM Gradle プロジェクトです。

## ツールスクリプト

- `tools/spector` - 直接 CLI
- `tools/spector-index` - インデックス作成用ラッパー
- `tools/spector-server` - サーバー管理
- `tools/spector-api` - API 呼び出しラッパー
- `tools/smoke-user-workflow` - 文書化されたワークフローのスモークテスト

## API エンドポイント

### 1. パッケージ内のクラス一覧

```text
GET /v1/packages/{packageName}/classes?recursive=true&publicOnly=true&kinds=CLASS,INTERFACE
```

### 2. クラスのメンバー一覧

```text
GET /v1/classes/{fqcn}/members?kinds=METHOD,FIELD&visibility=PUBLIC&includeSynthetic=false&inherited=false
```

### 3. メンバー詳細

```text
POST /v1/members/detail
```

### 4. クラス階層

```text
GET /v1/classes/{fqcn}/hierarchy?includeSubclasses=true
```

### 5. サブクラス一覧

```text
GET /v1/classes/{fqcn}/subclasses?recursive=true
```

### 6. インターフェース実装クラス一覧

```text
GET /v1/interfaces/{fqcn}/implementations
```

## テスト

```bash
./gradlew test
./tools/smoke-user-workflow /tmp/spector-workflow.db
```

## 技術スタック

- Kotlin 2.2.0 / Java 21
- ASM 9.7
- kotlinx-metadata-jvm 0.9.0
- SQLite JDBC 3.46.1.0
- Ktor 2.3.12
- Kotest 6.0

## TODO

- [ ] JavaDoc/KDoc の抽出
- [x] 継承階層の解決
- [x] メンバーのインデックス化
- [ ] 増分インデックス更新
- [ ] MCP サーバーとしての実装
- [x] Gradle Tooling API 統合
- [ ] Android/KMP 対応

## ライセンス

Apache License 2.0
