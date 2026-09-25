# アーキテクチャ概要

本プロジェクトは、DBのメタ情報（テーブル・カラム・制約・外部キー・トリガー・関数・シーケンス・ユーザー定義型等）を
取得し、Markdown形式のテーブル定義書・ER図を出力するCLIアプリケーションである。
Java 21 / Gradle製で、DIコンテナにGoogle Guiceを、O/RマッパーにMyBatisを使用する。

## レイヤー構成

`com.export_table_definition` 配下は4層構成（プレゼンテーション / アプリケーション / ドメイン / インフラ）で、
依存の方向は常に「外側 → 内側」（インフラ・プレゼンテーションはドメインに依存するが、逆はない）。

```
presentation    … CLIからの呼び出しを受けるコントローラー層
    ↓
application     … ユースケース（処理フロー）を組み立てる層
    ↓
domain          … モデル・リポジトリインターフェース・ドメインサービス（DBに依存しない中核ロジック）
    ↑
infrastructure  … MyBatis／ファイルI/Oなど、ドメインのインターフェースを実装する層
```

| 層 | パッケージ | 役割 |
|---|---|---|
| presentation | `presentation`, `presentation.dto`, `presentation.type` | エントリーポイントからの呼び出しを受け、ユースケースを実行して結果を返す |
| application | `application`, `application.impl` | ユースケース（テーブル定義出力処理全体のフロー制御）を実装する |
| domain | `domain.model`, `domain.repository`, `domain.service` | エンティティ・値オブジェクト・リポジトリIF・書き込み処理（ドメインサービス）を持つ、DB種別に依存しない中核 |
| infrastructure | `infrastructure.db`, `infrastructure.file`, `infrastructure.path` | MyBatisによるDBアクセス、ファイル書き込み、出力パス解決などドメインIFの実装を提供する |
| config | `config`, `config.module` | プロパティ読み込み、Guiceによる依存関係の束縛（DI設定） |

詳しいパッケージ・クラス単位の役割は [package-structure.md](./package-structure.md) を参照。

## 実行フロー

1. `ExportTableDefinition.main()` がCLI引数／環境変数からDB接続情報の上書き値を解決し、
   `MyBatisSqlSessionFactory` に設定する。
2. Guiceが `ExportTableDefinitionModule` の束縛定義に従いDIコンテナを構築し、
   `ExportTableDefinitionController` を取得して `run()` を呼び出す。
3. `ExportTableDefinition.run()` が `conf/ExportTableDefinition.properties` の設定値
   （出力対象スキーマ／テーブル、出力先パス、chunkSize、erDiagramMaxNodes、outputObjects、annotationPath）を読み込み、
   `ExportTableDefinitionController.execute()` を呼び出す。
4. コントローラーは `ExportTableDefinitionUsecaseImpl.exportTableDefinition()` を呼び出し、例外を捕捉して
   `ResultDto`（成功/失敗）に変換する。
5. ユースケース実装が以下を順に行う（詳細は
   [ExportTableDefinitionUsecaseImpl.java](../../src/main/java/com/export_table_definition/application/impl/ExportTableDefinitionUsecaseImpl.java) 参照）。
   - `TableDefinitionRepository` からテーブル一覧・外部キー・トリガー等をMyBatis経由で取得
   - `AnnotationRepository` でサイドカーYAML（手動付帯情報・論理リレーション）を読み込みマージ
   - `TableDefinitionWriterDomainService` / `ErDiagramWriterDomainService` / `ObjectListWriterDomainService`
     （いずれも `domain.service.writer` 配下）がMarkdownを組み立てて `FileRepository` 経由で出力

## DB種別の切り替え（Oracle / PostgreSQL）

`infrastructure.db.type.DatabaseType` （enum）がDB種別名と対応する
`infrastructure.db.repository.*TableDefinitionRepository` 実装クラスを紐づけている。
`ExportTableDefinitionModule.configure()` で
`MyBatisSqlSessionFactory.getConnectionDbName().getRepositoryClass()` を通じて `TableDefinitionRepository` の
実装クラスをDBごとに動的に束縛する。DB固有のSQLは
[src/main/resources/mapper/oracle/tableDefinitionMapper.xml](../../src/main/resources/mapper/oracle/tableDefinitionMapper.xml) と
[src/main/resources/mapper/postgresql/tableDefinitionMapper.xml](../../src/main/resources/mapper/postgresql/tableDefinitionMapper.xml) に分離されている。
両リポジトリは共通処理を `AbstractTableDefinitionRepository` に持つ。

PostgreSQL固有オブジェクト（トリガー／関数・プロシージャ／シーケンス／ユーザー定義型）の出力は
`domain.model.type.OutputObjectType` で種別ごとに絞り込み可能。Oracle接続時はこれらの取得・出力自体が行われない。

なお論理リレーション（サイドカーYAML由来）はDBに依存しないため、PostgreSQL／Oracleの双方で利用できる。

## メモリ効率のための分割取得

対象スキーマ全体を一度にメモリへ載せないよう、`ExportTableDefinitionUsecaseImpl` は以下の方針で処理する。

- テーブル一覧・外部キー・トリガーは軽量なため対象範囲全体を一括取得する
  （ER図でスキーマ・チャンクを跨いだ参照関係を解決するために全件が必要なため）
- カラム・インデックス・制約はテーブル数に比例して重くなるため、スキーマ単位かつ `chunkSize` 件ごとに
  取得・出力・破棄する（`exportSchemaTableDefinitions` / `exportTableDefinitionChunk`）
- 関数・プロシージャの定義本体はスキーマ単位で取得・出力・破棄する（`exportSchemaFunctionDefinitions`）

## ER図生成

ER図生成のアルゴリズム（連結成分によるグループ分割、多重度判定ロジックなど）はREADME
（[../../README.md](../../README.md) の「ER図」節）に詳しい。実装は
`ErDiagramWriterDomainService` と `domain.model.collection.ForeignKeyGroups`（連結成分の算出）、
`domain.model.type.Cardinality`（多重度判定）が中心。

## DB vs ドキュメントの差分検知（`--check`モード）

`ExportTableDefinition.main()`にCLI引数`--check`を渡すと、通常のドキュメント出力の代わりに
`ExportTableDefinitionController.checkDiff()` → `ExportTableDefinitionUsecaseImpl.checkDocumentDiff()`を呼び出す。

`checkDocumentDiff()`は、`outputPath`（比較先）には手を入れず、一時ディレクトリへ向けて
`exportTableDefinition()`をそのまま呼び出した上で、生成結果と`outputPath`配下を
`DocumentDiffDomainService.compare()`でファイル単位（追加/削除/内容不一致）に比較する。
Writer層・SQL層は出力先パスに一切依存しないため無改修で再利用できる。一時ディレクトリの作成・削除は
（他のファイル操作と同様に）`FileRepository.createTempDirectory()`/`deleteDirectory()`を介して行い、
`try-finally`で必ず削除される。

差分が1件でもある場合、または比較処理自体が例外で失敗した場合は`System.exit(1)`、差分なしの場合は
`System.exit(0)`で終了するため、CI上でジョブの成否として扱える。

## サイドカーYAML（手動付帯情報・論理リレーション）

DBのメタ情報だけでは表現できない情報を、サイドカーYAML（`annotationPath` で指定）としてマージできる。
読み込みは `AnnotationRepository`（実装: `infrastructure.file.repository.AnnotationYamlRepository`）が一括で行い、
`domain.model.annotation.Sidecar` として返す。`Sidecar` は性質の異なる2種類の情報を束ねる。

| 種別 | YAMLキー | モデル | 反映先 |
|---|---|---|---|
| 手動付帯情報 | `tables` | `Annotations` / `TableAnnotation` | テーブル定義書の各セル（説明・備考・カラム備考） |
| 論理リレーション | `relations` | `ForeignKeyEntity`（`RelationType.LOGICAL`） | 「論理リレーション情報」セクション + ER図 |

実在しないテーブル・カラムに対する付帯情報（リネーム・削除の見落とし）は警告ログで検知する。

### 論理リレーションの合流

外部キー制約を張らないDBではカタログから読み取れる関連だけではER図がほとんど空になるため、
サイドカーで宣言した関連を補う。設計上の要点は「**物理外部キーと同じ集合へ合流させる**」こと。

- `ExportTableDefinitionUsecaseImpl` が、DBから取得した外部キーとサイドカー由来の論理リレーションを
  結合して `ForeignKeys.of()` に渡す（`resolveLogicalRelations`）。参照元・参照先の双方が出力対象に
  存在しない関連は、ER図に片側だけのノードが現れるのを避けるため警告ログを出して除外する
- 合流させることで、ER図のグループ分割（`ForeignKeyGroups` の連結成分算出）、スキーマ跨ぎ関連の抽出
  （`ForeignKeys.crossSchema()`）、被参照側の解決（`incomingOf`）にも**追加実装なしで反映される**
- 読み手が「DBに制約がある」と誤読しないよう、2箇所で区別する
  - テーブル定義書：`ForeignKeys.physicalOf()` / `logicalOf()` で由来ごとに取り出し、別セクションへ掲載
  - ER図：`RelationType` が持つ線種を `Cardinality.getNotation(RelationType)` が組み立て、
    物理は実線（`||--o{`）、論理は破線（`||..o{`）で描画する

## 設定・DI

- `config.PropertyLoader`: `conf/ExportTableDefinition.properties` 等のプロパティ読み込みユーティリティ
- `config.module.ExportTableDefinitionModule`: Guiceの束縛定義（インターフェース→実装クラスの対応表）

新しいリポジトリ実装やドメインサービスを追加する場合は、ここに束縛を追加する。
