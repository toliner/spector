# Spector PoC 実装サマリー

## 実装完了日
2025-11-06

## 実装概要

このPoCは、AIエージェント向けにJava/Kotlinの依存ライブラリの型情報を提供するMCPサーバーの基礎実装です。
TDD（テスト駆動開発）アプローチに従い、段階的に実装しました。

## 実装したコンポーネント

### 1. データモデル層 (`model/`)

**ファイル:**
- `TypeRef.kt` - 型参照の表現（クラス、配列、プリミティブ、型変数、ワイルドカード）
- `ClassInfo.kt` - クラス/インターフェース/enum/objectの正規化表現
- `MemberInfo.kt` - フィールド/メソッド/プロパティの情報

**特徴:**
- kotlinx-serializationでシリアライズ可能
- Java/Kotlin両対応
- ジェネリクスとnullabilityのサポート

### 2. スキャナー層 (`scanner/`)

**ファイル:**
- `ClassScanner.kt` - ASMベースのバイトコード解析
- `KotlinMetadataEnricher.kt` - kotlinx-metadata-jvmでKotlin特有情報を抽出

**特徴:**
- クラスをロードせず静的解析（副作用なし）
- `ClassReader.SKIP_CODE | SKIP_DEBUG`で高速化
- Kotlinメタデータから以下を抽出:
  - nullability
  - suspend関数
  - 拡張レシーバー
  - デフォルト引数
  - data/sealed/value/object/companion属性
  - プロパティとgetter/setterの関連

### 3. ストレージ層 (`storage/`)

**ファイル:**
- `TypeIndexer.kt` - SQLiteベースの型情報ストレージ

**特徴:**
- 2テーブル設計: `types`（クラス情報）、`members`（メンバー情報）
- JSONシリアライゼーションでデータ格納
- パッケージ名とowner_fqcnにインデックス
- 柔軟なクエリAPI（kinds, visibility, synthetic filterなど）

**テーブルスキーマ:**
```sql
CREATE TABLE types (
    fqcn TEXT PRIMARY KEY,
    package_name TEXT NOT NULL,
    kind TEXT NOT NULL,
    modifiers TEXT NOT NULL,
    super_class TEXT,
    interfaces TEXT,
    type_parameters TEXT,
    annotations TEXT,
    kotlin_info TEXT,
    data TEXT NOT NULL
);

CREATE TABLE members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_fqcn TEXT NOT NULL,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    jvm_desc TEXT,
    visibility TEXT NOT NULL,
    static INTEGER NOT NULL,
    data TEXT NOT NULL,
    UNIQUE(owner_fqcn, name, jvm_desc)
);
```

### 4. インデクサー層 (`indexer/`)

**ファイル:**
- `ClasspathIndexer.kt` - クラスパス全体のスキャンオーケストレーター

**特徴:**
- JAR/ディレクトリの両方をサポート
- 並列処理対応（CPUコア数に応じたスレッドプール）
- 進捗ログ（100クラスごとに表示）
- エラーハンドリング（個別クラスの失敗が全体に影響しない）

### 5. API層 (`api/`)

**ファイル:**
- `ApiModels.kt` - リクエスト/レスポンスモデル
- `ApiServer.kt` - Ktor HTTPサーバー

**エンドポイント:**

1. **`GET /v1/packages/{packageName}/classes`**
   - パッケージ内のクラス一覧を取得
   - クエリパラメータ: `recursive`, `kinds`, `publicOnly`, `limit`, `offset`

2. **`GET /v1/classes/{fqcn}/members`**
   - クラスのメンバー一覧を取得
   - クエリパラメータ: `kinds`, `visibility`, `includeSynthetic`

3. **`POST /v1/members/detail`**
   - 特定メンバーの詳細情報を取得
   - ボディ: `ownerFqcn`, `name`, `jvmDesc`

**レスポンス形式:**
```json
{
  "ok": true,
  "data": { ... }
}
```
または
```json
{
  "ok": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "..."
  }
}
```

### 6. メインエントリーポイント

**ファイル:**
- `Main.kt` - コマンドラインインターフェース

**コマンド:**
- `index <db-path> <classpath-entries...>` - インデックス化
- `serve <db-path> [port]` - サーバー起動
- `index-and-serve <db-path> <classpath-entries...>` - ワンコマンド実行
- `help` - ヘルプ表示

### 7. Gradleタスク

**build.gradle.kts に追加:**
- `printRuntimeCp` - runtimeClasspathを出力
- `printTestRuntimeCp` - testRuntimeClasspathを出力

**使用例:**
```bash
./gradlew run --args="index types.db $(./gradlew -q printRuntimeCp | tail -1 | tr ':' ' ')"
```

### 8. テスト

**ファイル:**
- `ClassScannerTest.kt` - ASMスキャナーのユニットテスト
- `IntegrationTest.kt` - エンドツーエンド統合テスト

**テスト内容:**
- Java標準ライブラリのスキャン（String, ArrayList, HashMap等）
- Kotlin標準ライブラリのスキャン
- クラス階層の検出（superClass, interfaces）
- Kotlinメタデータの検出

## 技術的特徴

### TDDアプローチ

1. **データモデル優先**: まず型定義から開始
2. **テストファースト**: ClassScannerTestを先に記述
3. **段階的実装**: 各層を独立してテスト可能に設計

### ASMによる静的解析

- `ClassVisitor`パターンで効率的にバイトコード走査
- `Signature`属性からジェネリクス情報を復元
- `ACC_SYNTHETIC`, `ACC_BRIDGE`で合成メソッドを識別

### Kotlinメタデータ解析

- `@kotlin.Metadata`アノテーションを解析
- `KotlinClassMetadata.readStrict()`でメタデータ復元
- `KmClass`, `KmPackage`から以下を抽出:
  - プロパティ情報（getter/setter名、backing field）
  - 関数情報（suspend、拡張レシーバー）
  - クラスフラグ（data、sealed、value等）

### 並列処理

- `Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())`
- JAR単位で並列スキャン
- I/Oバウンドな処理を効率化

## 依存関係

```kotlin
// Core
- Kotlin 2.2.0
- Java 21 (Adoptium)

// Bytecode Analysis
- ASM 9.7
- kotlinx-metadata-jvm 0.9.0

// Storage
- sqlite-jdbc 3.46.1.0

// Web Server
- Ktor 2.3.12 (server-core, server-netty, content-negotiation)
- kotlinx-serialization-json 1.7.3

// Testing
- Kotest 6.0.0
- okhttp 4.12.0 (test dependency)
- jackson-databind 2.17.2 (test dependency)

// Logging
- logback-classic 1.5.8
```

## 未実装機能（将来拡張）

PoCとして実装しなかったが、仕様書に記載されている機能:

1. **メンバー情報のインデックス化**
   - 現在はクラス情報のみインデックス化
   - メンバー（フィールド/メソッド/プロパティ）は未実装

2. **継承解決**
   - `listMembers(inherited=true)`の実装
   - 親クラス/インターフェースからのメンバー継承

3. **ジェネリクスの完全サポート**
   - `Signature`属性の完全解析
   - 型境界の詳細な表現

4. **JavaDoc/KDoc抽出**
   - ドキュメンテーションコメントの抽出

5. **増分更新**
   - クラスパスハッシュに基づく差分更新
   - 変更されたJARのみ再スキャン

6. **MCP統合**
   - Model Context Protocolサーバーとしての実装
   - 現在はHTTP APIのみ

7. **Gradle Tooling API**
   - 現在は1-shotタスクのみ
   - Tooling APIでより安定したクラスパス取得

8. **Android/KMP対応**
   - variantごとのクラスパス管理

## 使用方法

### ビルド
```bash
./gradlew build
```

### クラスパスのインデックス化
```bash
./gradlew run --args="index types.db $(./gradlew -q printRuntimeCp | tail -1 | tr ':' ' ')"
```

### サーバー起動
```bash
./gradlew run --args="serve types.db 8080"
```

### APIクエリ例
```bash
# パッケージ内のクラス一覧
curl "http://localhost:8080/v1/packages/java.util/classes?recursive=false"

# クラスのメンバー一覧
curl "http://localhost:8080/v1/classes/java.util.ArrayList/members"

# メンバー詳細
curl -X POST http://localhost:8080/v1/members/detail \
  -H "Content-Type: application/json" \
  -d '{"ownerFqcn":"java.util.ArrayList","name":"add","jvmDesc":"(Ljava/lang/Object;)Z"}'
```

## まとめ

このPoCは、仕様書の主要機能を実装し、実用可能なプロトタイプとなっています:

✅ **実装済み:**
- ASMベースのクラススキャン
- Kotlinメタデータ解析
- SQLiteストレージ
- HTTP API (3エンドポイント)
- 並列処理
- TDDベースのテスト

🔄 **部分実装:**
- メンバー情報（データモデルは完成、インデックス化は未実装）
- ジェネリクス（基本対応、完全解析は未実装）

❌ **未実装:**
- 継承解決
- JavaDoc/KDoc
- 増分更新
- MCP統合
- Gradle Tooling API

このPoCをベースに、段階的に機能を追加していくことができます。
