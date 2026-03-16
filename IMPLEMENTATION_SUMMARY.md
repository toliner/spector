# Spector PoC 実装サマリー

## 実装概要

この PoC は、AI エージェント向けに Java/Kotlin の依存ライブラリ型情報を提供する HTTP API サーバーの基礎実装です。型情報の抽出、SQLite への保存、HTTP 経由の問い合わせ、継承関係の探索までを実装しています。

## 実装したコンポーネント

### 1. データモデル層 (`model/`)

- `TypeRef.kt` - 型参照の表現
- `ClassInfo.kt` - クラス、インターフェース、enum、object の表現
- `MemberInfo.kt` - フィールド、メソッド、プロパティの表現

### 2. スキャナー層 (`scanner/`)

- `ClassScanner.kt` - ASM ベースのバイトコード解析
- `KotlinMetadataEnricher.kt` - Kotlin 特有情報の抽出

### 3. ストレージ層 (`storage/`)

- `TypeIndexer.kt` - SQLite ベースの型情報ストレージ

この層はクラス情報だけでなくメンバー情報も保持し、以下を提供します。
- クラス検索
- メンバー検索
- 継承メンバー探索
- サブクラス探索
- インターフェース実装クラス探索

### 4. インデクサー層 (`indexer/`)

- `ClasspathIndexer.kt` - JAR とディレクトリの走査
- `JavaStdLibIndexer.kt` - Java 標準ライブラリの取り込み

### 5. API 層 (`api/`)

- `ApiModels.kt` - リクエスト、レスポンスモデル
- `ApiConfiguration.kt` - HTTP ルーティング定義
- `ApiServer.kt` - Ktor サーバー起動

実装済みエンドポイント:

1. `GET /v1/packages/{packageName}/classes`
2. `GET /v1/packages/{packageName}/subpackages`
3. `GET /v1/classes/{fqcn}/members`
4. `POST /v1/members/detail`
5. `GET /v1/classes/{fqcn}/hierarchy`
6. `GET /v1/classes/{fqcn}/subclasses`
7. `GET /v1/interfaces/{fqcn}/implementations`
8. `GET /health`
9. `POST /shutdown`

### 6. エントリーポイント

- `Main.kt` - `index`, `serve`, `index-and-serve`, `help`

### 7. テスト

代表的なテスト:
- `src/test/kotlin/dev/toliner/spector/scanner/ClassScannerTest.kt`
- `src/test/kotlin/dev/toliner/spector/storage/TypeIndexerTest.kt`
- `src/test/kotlin/dev/toliner/spector/api/ApiServerTest.kt`
- `src/test/kotlin/dev/toliner/spector/integration/IntegrationTest.kt`

`ApiServerTest` と `TypeIndexerTest` には継承階層、サブクラス、実装クラス、継承メンバーに対する検証が含まれます。

## 技術的特徴

### 静的解析

- クラスをロードせず ASM で直接走査
- `ClassReader.SKIP_CODE | SKIP_DEBUG` を使った高速化

### Kotlin メタデータ解析

- nullability
- suspend 関数
- 拡張レシーバー
- data/sealed/value/object などの属性
- プロパティと accessor の関連

### ストレージ

- `types` テーブルと `members` テーブルの 2 テーブル構成
- JSON シリアライズで保存
- パッケージ名と owner FQCN にインデックス

## 現在の制約

未実装または限定的な項目:

1. ジェネリクスの完全サポート
2. JavaDoc/KDoc 抽出
3. 増分更新
4. MCP 統合
5. Gradle Tooling API
6. Android/KMP 対応

## 使用例

```bash
./tools/spector-index project types.db
./tools/spector-server start types.db
./tools/spector-api packages dev.toliner.spector.storage
./tools/spector-api members dev.toliner.spector.storage.TypeIndexer --kind METHOD
./tools/spector-api hierarchy dev.toliner.spector.storage.TypeIndexer
./tools/spector-server stop
```

## まとめ

現時点でこの PoC は以下を提供します。

- クラスとメンバーのインデックス化
- HTTP API による検索
- 継承階層、サブクラス、実装クラスの探索
- テスト済みの Kotlin/Java 解析パイプライン
