# Spector - JVM Type Indexer PoC

AIエージェント向けにJava/Kotlinの依存ライブラリの型情報を提供するMCPサーバーのPoC実装です。

## 概要

SpectorはGradleプロジェクトのクラスパスから型情報を抽出し、SQLiteデータベースにインデックス化して、HTTP API経由でクエリ可能にします。

### 主な機能

- **ASMベースのクラススキャン**: バイトコードを解析して型情報を抽出（クラスをロードせず副作用なし）
- **Kotlinメタデータサポート**: kotlinx-metadata-jvmを使用してKotlin特有の情報（nullability、suspend、拡張関数など）を抽出
- **SQLiteストレージ**: 高速な型情報の永続化とクエリ
- **HTTP API**: RESTful APIで型情報にアクセス

### アーキテクチャ

```
[Gradle Project]
   ↓ (Tooling API / Gradle task)
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

### 1. クラスパスの抽出

```bash
# プロジェクトのruntimeClasspathを取得
./gradlew -q printRuntimeCp

# testRuntimeClasspathを取得
./gradlew -q printTestRuntimeCp
```

### 2. 型情報のインデックス化

```bash
# クラスパスをインデックス化
./gradlew run --args="index types.db $(./gradlew -q printRuntimeCp | tail -1 | tr ':' ' ')"
```

### 3. APIサーバーの起動

```bash
# 既存のデータベースでサーバー起動
./gradlew run --args="serve types.db 8080"
```

### 4. ワンコマンドでインデックス化＆サーバー起動

```bash
./gradlew run --args="index-and-serve types.db $(./gradlew -q printRuntimeCp | tail -1 | tr ':' ' ')"
```

## API エンドポイント

### 1. パッケージ内のクラス一覧取得

```bash
GET /v1/packages/{packageName}/classes?recursive=true&publicOnly=true&kinds=CLASS,INTERFACE
```

例:
```bash
curl "http://localhost:8080/v1/packages/java.util/classes?recursive=false"
```

### 2. クラスのメンバー一覧取得

```bash
GET /v1/classes/{fqcn}/members?kinds=METHOD,FIELD&includeSynthetic=false
```

例:
```bash
curl "http://localhost:8080/v1/classes/java.util.ArrayList/members"
```

### 3. メンバーの詳細情報取得

```bash
POST /v1/members/detail
Content-Type: application/json

{
  "ownerFqcn": "java.util.ArrayList",
  "name": "add",
  "jvmDesc": "(Ljava/lang/Object;)Z"
}
```

例:
```bash
curl -X POST http://localhost:8080/v1/members/detail \
  -H "Content-Type: application/json" \
  -d '{"ownerFqcn":"java.util.ArrayList","name":"add","jvmDesc":"(Ljava/lang/Object;)Z"}'
```

## プロジェクト構造

```
src/main/kotlin/dev/toliner/spector/
├── model/              # データモデル（TypeRef, ClassInfo, MemberInfo）
├── scanner/            # ASMスキャナーとKotlinエンリッチャー
├── storage/            # SQLiteベースのTypeIndexer
├── indexer/            # クラスパス全体のインデックス化オーケストレーター
├── api/                # Ktor HTTPサーバー
└── Main.kt             # エントリーポイント
```

## テスト

```bash
./gradlew test
```

統合テストは実際のJava標準ライブラリとKotlin標準ライブラリをスキャンして、正しく型情報を抽出できることを検証します。

## 技術スタック

- **Kotlin 2.2.0** / **Java 21**
- **ASM 9.7** - バイトコード解析
- **kotlinx-metadata-jvm 0.9.0** - Kotlinメタデータ解析
- **SQLite JDBC 3.46.1.0** - 永続化
- **Ktor 2.3.12** - HTTPサーバー
- **Kotest 6.0** - テストフレームワーク

## 実装の特徴

### 副作用なしの静的解析

- クラスをロードせず、ASMでバイトコードを直接解析
- 初期化処理やstatic blockが実行されない安全な解析

### Kotlin完全サポート

- nullability情報
- suspend関数
- 拡張関数・レシーバー
- デフォルト引数
- data/sealed/value/object クラス
- プロパティとgetter/setterの関連付け

### 並列処理

- 複数スレッドでJARファイルを並列スキャン
- CPUコア数に応じた最適なスレッド数

## TODO（将来の拡張）

- [ ] JavaDoc/KDocの抽出
- [ ] 継承階層の解決
- [ ] メンバーのインデックス化（現在はクラスのみ）
- [ ] 増分インデックス更新（クラスパスのハッシュベース）
- [ ] MCP (Model Context Protocol) サーバーとしての実装
- [ ] Gradle Tooling API統合
- [ ] Android/KMP対応

## ライセンス

MIT License

## 作者

toliner
