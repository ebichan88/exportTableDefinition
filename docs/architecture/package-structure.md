# パッケージ構成リファレンス

`com.export_table_definition` 配下の全パッケージと主要クラスの一覧。
役割の全体像は先に [overview.md](./overview.md) を参照。

## presentation層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `presentation` | `ExportTableDefinitionController` | エントリーポイントから呼ばれ、ユースケースの実行と例外の`ResultDto`/`DiffCheckResultDto`変換を行う。`checkDiff`の差分メッセージ組み立てでは、`ContentDiff`のunified diffを1オブジェクトあたり・全体それぞれ行数の上限付きで含める |
| `presentation.dto` | `ResultDto` | 通常実行（`execute`）の処理結果（成否・メッセージ）を表すrecord |
| | `DiffCheckResultDto` | `--check`モード（`checkDiff`）の処理結果（成否・メッセージ・差分有無）を表すrecord |
| `presentation.type` | `ProcessResult` | 処理結果種別（成功/失敗）のenum |

## application層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `application` | `ExportTableDefinitionUsecase` | テーブル定義出力ユースケースのインターフェース（通常出力`exportTableDefinition`／差分検知`checkDocumentDiff`） |
| `application.impl` | `ExportTableDefinitionUsecaseImpl` | 出力処理全体のフロー制御（取得→マージ→書き込みの司令塔）。取得（`fetchTargets`）と出力（`export`）を分け、出力形式（Markdown/スナップショット）を切り替えられる。`checkDocumentDiff`は`SNAPSHOT`形式のみで一時ディレクトリへ出力し、`SnapshotDiffDomainService`で`outputPath`配下の`snapshot/`と比較する |

## domain層

### domain.model

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `domain.model` | `TableDefinitionContent` | 1テーブル分の定義書出力に必要な情報を束ねるrecord（`assemble()`で組み立て） |
| `domain.model.entity` | `BaseInfoEntity`, `TableEntity`, `ColumnEntity`, `ConstraintEntity`, `ForeignKeyEntity`, `IndexEntity`, `TriggerEntity`, `FunctionEntity`, `SequenceEntity`, `TypeEntity` | DBから取得したメタ情報を表すrecord群。`SchemaTableKeyed`はスキーマ名・テーブル名を持つ共通IF |
| `domain.model.collection` | `Columns`, `Constraints`, `ForeignKeys`, `Indexes`, `Triggers`, `AbstractEntities`, `ForeignKeyGroups` | エンティティのリストをラップし、テーブル単位の絞り込み等を提供するコレクションクラス群。`ForeignKeys`は物理外部キーと論理リレーションを同一集合として保持し、`physicalOf`/`logicalOf`で由来ごとに取り出せる。`ForeignKeyGroups`は外部キーの連結成分（ER図の分割単位）を算出する |
| `domain.model.type` | `TableType`, `Cardinality`, `RelationType`, `OutputObjectType` | テーブル種別、外部キー多重度（1対1／1対多等）、関連の由来（物理＝FK制約／論理＝サイドカー宣言）、PostgreSQL固有出力対象種別のenum |
| `domain.model.value` | `TableKey` | スキーマ名+テーブル名の値オブジェクト（付帯情報とテーブル実体の突合キー） |
| `domain.model.annotation` | `Sidecar` | サイドカーYAMLの読み込み結果全体（手動付帯情報＋論理リレーション）を束ねるrecord |
| | `Annotations`, `TableAnnotation` | サイドカーYAML由来の手動付帯情報（テーブル単位の集合とその1件分） |
| `domain.model` | `DiffResult` | 生成したスキーマのスナップショットとコミット済みのものの比較結果（追加/削除/内容不一致の対象一覧）を表すrecord。対象はオブジェクト（例: `table sample.employee`）またはファイル（例: `database.json`）の識別名 |
| | `ContentDiff` | 内容が一致しないオブジェクト（またはファイル）1件分の差分（対象の表示名 + unified diff形式の行リスト）を表すrecord |
| `domain.model.snapshot` | `DatabaseSnapshot`, `TableSnapshot`, `FunctionSnapshot`, `SequenceSnapshot`, `TypeSnapshot` | スキーマのスナップショット（JSON Lines）の1行分を表すrecord群。エンティティからの変換時にMarkdownの表示都合の値（`○`マーカー・連結文字列等）を真偽値・リストへ正規化する |
| | `SnapshotKind` | スキーマ単位のJSON Linesファイルに出力するオブジェクト種別（テーブル/関数/シーケンス/型）のenum |

### domain.repository（インターフェースのみ。実装はinfrastructure層）

| クラス | 役割 |
|---|---|
| `TableDefinitionRepository` | テーブル・カラム・制約・外部キー・トリガー・関数・シーケンス・型のDB取得IF（DB種別ごとに実装が分かれる） |
| `AnnotationRepository` | サイドカーYAML（手動付帯情報・論理リレーション）読み込みIF |
| `FileRepository` | ファイル操作IF（`writeFile`/`appendFile`/`createDirectory`に加え、差分検知用の`listFiles`/`readFile`、一時ディレクトリ操作用の`createTempDirectory`/`deleteDirectory`を持つ） |

### domain.service

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `domain.service` | `UnifiedDiffGenerator` | 2つの行リストからunified diff形式の差分を生成する。Myers法による自前実装（外部ライブラリに依存しない） |
| `domain.service.path` | `OutputPathResolver` | テーブル定義・一覧・スナップショットの出力パス生成戦略IF |
| `domain.service.snapshot` | `SchemaSnapshotWriterDomainService` | スキーマのスナップショット（JSON Lines）の書き込み。テーブルはスキーマ単位のファイルへ1行ずつ追記する |
| | `SnapshotDiffDomainService` | 生成したスナップショットとコミット済みスナップショットを、オブジェクト単位（追加/削除/内容不一致）で比較する（`--check`モードで使用）。内容が一致しないものは、`SnapshotSerializer.formatForDiff`で整形した上で`UnifiedDiffGenerator`によりunified diffを付ける |
| | `SnapshotSerializer` | スナップショットのrecordとJSON文字列の変換IF（実装はインフラ層）。差分表示用に1項目1行へ整形する`formatForDiff`も持つ |
| `domain.service.writer` | `TableDefinitionWriterDomainService` | テーブル一覧・テーブル定義書のMarkdown書き込み |
| | `ErDiagramWriterDomainService` | スキーマ別ER図（全体ER図）とその索引の書き込み。連結成分ごとのグループ分割を含む |
| | `ObjectListWriterDomainService` | トリガー・関数/プロシージャ・シーケンス・ユーザー定義型の一覧および個別定義の書き込み |
| | `PagedSectionWriter` | 行数の多い表をページ分割して出力する共通処理 |
| `domain.service.writer.template` | `TableDefinitionTemplates`, `TableDefinitionListTemplates`, `ErDiagramTemplates`, `ObjectListTemplates`, `ObjectDefinitionTemplates`, `PagedSectionTemplates` | 各Writerが使うMarkdownテンプレート（文字列組み立て）クラス群 |
| | `MarkdownTemplateSupport`, `MermaidSupport` | テンプレート共通部品、Mermaid記法変換ユーティリティ |

## infrastructure層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `infrastructure.db` | `MyBatisSqlSessionFactory` | MyBatisの`SqlSessionFactory`生成・DB接続情報の上書き管理 |
| `infrastructure.db.type` | `DatabaseType` | DB種別（postgresql/oracle）とリポジトリ実装クラスの対応enum |
| `infrastructure.db.repository` | `AbstractTableDefinitionRepository` | Oracle/Postgres共通のリポジトリ基底クラス |
| | `OracleTableDefinitionRepository`, `PostgresTableDefinitionRepository` | `TableDefinitionRepository`のDB別実装。対応するSQLは`src/main/resources/mapper/{oracle,postgresql}/tableDefinitionMapper.xml` |
| `infrastructure.db.repository.dto` | `TableDto`, `ColumnDto`, `ConstraintDto`, `ForeignKeyDto`, `IndexDto`, `TriggerDto`, `FunctionDto`, `SequenceDto`, `TypeDto`, `BaseInfoDto` | MyBatisのResultMap受け皿となるDTO（`domain.model.entity`へ変換される） |
| `infrastructure.file` | `TableDefinitionBufferedWriter` | テーブル定義書き込み用`BufferedWriter`のラッパー |
| `infrastructure.file.repository` | `TableDefinitionFileRepository` | `FileRepository`実装（実ファイル書き込み） |
| | `AnnotationYamlRepository` | `AnnotationRepository`実装（サイドカーYAML読み込み、SnakeYAML使用）。`tables`（付帯情報）と`relations`（論理リレーション）の双方を解釈する |
| `infrastructure.path` | `DefaultOutputPathResolver` | `OutputPathResolver`のデフォルト実装 |
| `infrastructure.snapshot` | `JacksonSnapshotSerializer` | `SnapshotSerializer`のJackson実装 |

## config層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `config` | `PropertyLoader` | `conf/*.properties`読み込みユーティリティ |
| `config.module` | `ExportTableDefinitionModule` | Guiceの束縛定義（IF→実装クラスの対応）。新規リポジトリ/ドメインサービス追加時はここに束縛を追加する |

## リソース（Java外）

| パス | 役割 |
|---|---|
| `src/main/resources/conf/ExportTableDefinition.properties` | 出力対象スキーマ/テーブル、出力先、chunkSize等のアプリ設定 |
| `src/main/resources/conf/mybatis.properties.template` | DB接続情報テンプレート（実ファイルは`mybatis.properties`としてgitignore対象） |
| `src/main/resources/mybatis-config.xml` | MyBatisのメイン設定（DB種別ごとのmapper読み込み等） |
| `src/main/resources/mapper/oracle/tableDefinitionMapper.xml` | Oracle向けSQL定義 |
| `src/main/resources/mapper/postgresql/tableDefinitionMapper.xml` | PostgreSQL向けSQL定義 |
| `src/main/resources/log4j2.xml` | ログ設定 |

## テスト

`src/test/java/com/export_table_definition` 配下は本体パッケージとほぼ1:1で対応する構成
（`application`, `config`, `domain`, `infrastructure`, `presentation`, `testsupport`）。
`testsupport`にはテスト用のビルダー・フィクスチャ等の共通部品を置く
（`MarkdownAssert`、`ForeignKeyFixtures`など）。
