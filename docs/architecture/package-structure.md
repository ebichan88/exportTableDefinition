# パッケージ構成リファレンス

`com.export_table_definition` 配下の全パッケージと主要クラスの一覧。
役割の全体像は先に [overview.md](./overview.md)、ドメインの概念同士の関係・用語は [domain-model.md](./domain-model.md) を参照。

## presentation層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `presentation` | `ExportTableDefinitionController` | エントリーポイントから呼ばれ、ユースケースの実行と例外の`ResultDto`/`DiffCheckResultDto`変換を行う |
| | `DiffReportFormatter`（パッケージプライベート） | `checkDiff`の差分メッセージ組み立て。`ContentDiff`のunified diffを1オブジェクトあたり・全体それぞれ行数の上限付きで含める |
| `presentation.dto` | `ResultDto` | 通常実行（`execute`）の処理結果（成否・メッセージ）を表すrecord |
| | `DiffCheckResultDto` | `--check`モード（`checkDiff`）の処理結果（成否・メッセージ・差分有無）を表すrecord |
| `presentation.type` | `ProcessResult` | 処理結果種別（成功/失敗）のenum |

## application層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `application` | `ExportTableDefinitionUsecase` | テーブル定義出力（通常実行）のユースケースのインターフェース（`exportTableDefinition`） |
| | `CheckDocumentDiffUsecase` | DB vs ドキュメントの差分検知（`--check`モード）のユースケースのインターフェース（`checkDocumentDiff`） |
| | `ExportRequest`, `CheckDiffRequest` | 各ユースケースメソッドへの入力をまとめたrecord。エントリーポイント→コントローラー→ユースケースを分解・再構築せず通過する。`CheckDiffRequest`はMarkdownの描画・ER図の生成を行わないため`erDiagramMaxNodes`・`rmDist`を持たない |
| | `TargetSelection` | 両requestが持つ出力対象の絞り込み条件（`TableTargetScope`・`OutputObjectType`の集合・サイドカーYAMLのパス）のrecord。`of()`で設定値の文字列を入口で型へ変換・検証する |
| `application.impl` | `ExportTableDefinitionUsecaseImpl` | 通常実行のユースケース実装。MarkdownとスナップショットのExportSinkを渡して`SchemaExporter`に取得・書き出しさせる。`--rm-dist`は削除してよい出力先かを取得前に判定し、削除は取得の成功後に行う |
| | `CheckDocumentDiffUsecaseImpl` | 差分検知のユースケース実装。スナップショットの`ExportSink`のみで一時ディレクトリへ出力し、`SnapshotDiffDomainService`で`outputPath`配下の`snapshot/`と比較する |
| | `SchemaExporter`（パッケージプライベート） | 両ユースケースが共有する、DBからの取得（一括取得・スキーマ単位・チャンク単位）と書き出しの段取り。取得（`fetchTargets`）と出力（`export`）を分け、書き出しは出力形式ごとの`ExportSink`に、取得した情報同士の突き合わせは`ExportTargetConsistencyDomainService`に委ね、返された指摘（`ConsistencyFinding`）を重要度に応じてログへ出力する。ドキュメントの生成日は`Clock`から与える |

## domain層

### domain.model

概念ごとにサブパッケージへ分けている（依存関係は [domain-model.md](./domain-model.md#概念のまとまりパッケージ) を参照）。

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `domain.model.table` | `TableEntity`, `ColumnEntity`, `IndexEntity`, `ConstraintEntity`, `TriggerEntity` | DBから取得したテーブルとそれに属するメタ情報のrecord群。`TableEntity`の区分は`TableType`（enum）で持つ |
| | `TableKey` | スキーマ名+テーブル名の値オブジェクト（テーブルの識別子。付帯情報・関連とテーブル実体の突合キー）。`parse()`で「スキーマ.テーブル」形式の文字列を解析できる |
| | `SchemaTableKeyed` | スキーマ名・テーブル名を持つエンティティの共通IF。所属テーブルの`TableKey`を`tableKey()`で返す |
| | `Tables` | 出力対象のテーブル一覧のファーストクラスコレクション（テーブルキーでの存在判定・検索、スキーマ単位の分割） |
| | `TableDetail` | 1テーブル分の詳細情報（カラム・インデックス・制約）のrecord。`assembleAll()`で複数テーブル分の取得結果をテーブルごとに振り分ける |
| | `Triggers`, `AbstractEntities` | エンティティのリストをテーブルキーで引けるようにしたコレクションとその基底クラス（`Columns`・`Indexes`・`Constraints`は`TableDetail`の組み立て専用のためパッケージプライベート） |
| `domain.model.relation` | `ForeignKeyEntity` | 関連（DBの外部キー制約＝物理、サイドカーで宣言した論理リレーション＝論理）のrecord。参照先の`referenceTableKey()`、論理リレーションの関連名の自動生成（`resolveLogicalRelationName`）を持つ |
| | `ForeignKeys` | 物理外部キーと論理リレーションを同一集合として保持するコレクション。`physicalOf`/`logicalOf`で由来ごとに、`incomingOf`で被参照側を取り出せ、`crossSchema`でスキーマ跨ぎの関連を抽出する |
| | `ForeignKeyGroup`, `ForeignKeyGroups` | ER図1枚分の関連のまとまり（ノード算出・上限超過の判定・主なテーブル）と、その分割（連結成分の算出・1枚に収まる範囲でのまとめ直し。`compose()`がページ構成`PageComposition`を決める） |
| | `Cardinality`, `RelationType` | 多重度（1対1／1対多等。判定と、論理リレーションの既定値を持つ）、関連の由来（物理／論理）のenum |
| `domain.model.schemaobject` | `FunctionEntity`, `SequenceEntity`, `TypeEntity` | テーブルに属さないスキーマ直下のオブジェクト（関数・プロシージャ／シーケンス／ユーザー定義型）のrecord。`FunctionEntity`は同名関数（オーバーロード）内の番号を持つ |
| `domain.model.database` | `DatabaseEntity` | DBのカタログから取得するデータベースの情報（DB名・DBMS種別）のrecord |
| | `BaseInfoEntity` | 各ドキュメントに掲載する基本情報（`DatabaseEntity`の情報＋生成日）のrecord |
| `domain.model.sidecar` | `Sidecar` | サイドカーYAMLの読み込み結果全体（手動付帯情報＋論理リレーション）を束ねるrecord |
| | `Annotations`, `TableAnnotation` | サイドカーYAML由来の手動付帯情報（テーブルキーごとの集合とその1件分） |
| `domain.model.target` | `TableTargetScope` | テーブル定義出力対象の範囲（スキーマ名リスト＋テーブル名パターン）を表す値オブジェクト。実行設定から1回だけ生成し、`matches(TableEntity)`で各テーブルを判定する（パターンの判定は、パッケージプライベートの`TableTargetFilter`が行う） |
| | `OutputObjectType` | PostgreSQL固有の出力対象オブジェクト種別のenum。`parse()`で設定値を解釈する（未指定なら全種別、未知の種別名は例外） |
| | `ExportTargets` | 一括取得する軽量な出力対象の情報（基本情報・テーブル一覧・関連・トリガー・関数/シーケンス/型の一覧・手動付帯情報）の組 |
| | `TableDefinitionContent` | 1テーブル分の出力内容を束ねるrecord（`assemble()`で`TableDetail`と一括取得分から組み立て）。出力先は持たない |
| | `ConsistencyFinding` | 出力対象のテーブルと関連・付帯情報を突き合わせた指摘1件分の値オブジェクト（種類・メッセージ。重要度は種類が決める） |
| `domain.model.snapshot` | `DatabaseSnapshot`, `TableSnapshot`, `FunctionSnapshot`, `SequenceSnapshot`, `TypeSnapshot` | スキーマのスナップショット（JSON Lines）の1行分を表すrecord群。エンティティからの変換時に、値が無いこと（空文字）をnullへ正規化する（パッケージプライベートの`SnapshotValues`） |
| | `SnapshotKind` | スキーマ単位のJSON Linesファイルに出力するオブジェクト種別（テーブル/関数/シーケンス/型）のenum。行をオブジェクトとして識別する名前（`identify`）を持つ |
| | `DiffResult` | 生成したスキーマのスナップショットとコミット済みのものの比較結果（追加/削除/内容不一致の対象一覧）を表すrecord。対象はオブジェクト（例: `table sample.employee`）またはファイル（例: `database.json`）の識別名 |
| | `ContentDiff` | 内容が一致しないオブジェクト（またはファイル）1件分の差分（対象の表示名 + unified diff形式の行リスト）を表すrecord |
| `domain.model.document` | `ListDocumentType` | 一覧ドキュメント（テーブル／ER図／関数・プロシージャ／シーケンス／ユーザー定義型／トリガー）の種別のenum。一覧ファイル名・個別定義ディレクトリ名の接頭辞とタイトルを持つ |

### domain.repository（インターフェースのみ。実装はinfrastructure層）

| クラス | 役割 |
|---|---|
| `TableDefinitionRepository` | データベースの情報（`selectDatabase`）・テーブル一覧・外部キー・トリガー・関数・シーケンス・型のDB取得IF（DB種別ごとに実装が分かれる）。カラム・インデックス・制約は、指定したテーブル分をテーブルごとの`TableDetail`に組み立てて返す（`selectTableDetails`） |
| `SidecarRepository` | サイドカーYAML（手動付帯情報・論理リレーション）読み込みIF |
| `FileRepository` | ファイル操作IF（`writeFile`/`appendFile`/`createDirectory`に加え、差分検知用の`listFiles`/`readFile`、一時ディレクトリ操作用の`createTempDirectory`/`deleteDirectory`を持つ） |

### domain.service

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `domain.service` | `UnifiedDiffGenerator` | 2つの行リストからunified diff形式の差分を生成する。Myers法による自前実装（外部ライブラリに依存しない） |
| `domain.service.export` | `ExportSink` | 取得したスキーマ情報を1つの出力形式で書き出すIF（一括取得分・関数定義・テーブル定義の書き出し） |
| | `MarkdownExportSinkFactory`, `SnapshotExportSinkFactory` | 出力先（とER図のノード上限）を受け取り、Markdown／スナップショットの`ExportSink`を生成する |
| `domain.service.target` | `ExportTargetConsistencyDomainService` | 出力対象のテーブルと、外部キー・サイドカー（論理リレーション／付帯情報）を突き合わせる。片側が出力対象外の外部キー・論理リレーションの除外と、実在しないテーブル・カラムへの付帯情報（孤児付帯情報）の検出を行う。結果は指摘（`ConsistencyFinding`）として返し、ログへの出力は呼び出し側が行う |
| `domain.service.path` | `OutputPathResolver` | テーブル定義・一覧・スナップショットの出力パス生成戦略IF。分割ページのパスは本体ページのパスから`resolvePageFile`で求める。`--rm-dist`で削除してよい出力先かの判定（`isRemovableOutputDir`）も持つ |
| | `DocumentLocations` | Markdownドキュメントのファイル名と出力ベースディレクトリからの相対パス、ドキュメント間の相対リンクの規則を一元的に定める（関数・プロシージャのオーバーロードのファイル名を含む）。`OutputPathResolver`の実装とテンプレートの双方がこの規則を参照する |
| | `SnapshotLocations` | スナップショットのディレクトリ名・ファイル名と相対パスの規則を一元的に定める。`OutputPathResolver`の実装と、比較時のファイル種別の判定の双方がこの規則を参照する |
| | `OutputRoot` | 出力先ベースディレクトリと基本情報の組を表す値オブジェクト（Writer・`OutputPathResolver`へそのまま渡す） |
| `domain.service.snapshot` | `SchemaSnapshotWriterDomainService` | スキーマのスナップショット（JSON Lines）の書き込み。テーブルはスキーマ単位のファイルへ1行ずつ追記する |
| | `SnapshotDiffDomainService` | 生成したスナップショットとコミット済みスナップショットを、オブジェクト単位（追加/削除/内容不一致）で比較する（`--check`モードで使用）。内容が一致しないものは、`SnapshotSerializer.formatForDiff`で整形した上で`UnifiedDiffGenerator`によりunified diffを付ける |
| | `SnapshotSerializer` | スナップショットのrecordとJSON文字列の変換IF（実装はインフラ層）。差分表示用に1項目1行へ整形する`formatForDiff`も持つ |
| `domain.service.writer` | `TableDefinitionWriterDomainService` | テーブル一覧・テーブル定義書のMarkdown書き込み |
| | `ErDiagramWriterDomainService` | スキーマ別ER図（全体ER図）とその索引の書き込み。連結成分ごとのグループ分割を含む |
| | `ObjectListWriterDomainService` | トリガー・関数/プロシージャ・シーケンス・ユーザー定義型の一覧および個別定義の書き込み |
| | `PagedSectionWriter` | 行数の多い表をページ分割して出力する共通処理。分割ページは本体ページと同じディレクトリに置き、ページ間のリンクはファイル名から導く |
| `domain.service.writer.template` | `TableDefinitionTemplates`, `TableDefinitionListTemplates`, `ErDiagramTemplates`, `ObjectListTemplates`, `ObjectDefinitionTemplates`, `PagedSectionTemplates` | 各Writerが使うMarkdownテンプレート（文字列組み立て）クラス群。表の行を含むMarkdownの描画はすべてここで行い、Writerは描画せず、テンプレートは絞り込み・グラフ計算などのロジックを持たない |
| | `MarkdownTemplateSupport`, `MermaidSupport` | テンプレート共通部品、Mermaid記法変換ユーティリティ |

## infrastructure層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `infrastructure.db` | `MyBatisSqlSessionFactory` | MyBatisの`SqlSessionFactory`生成・DB接続情報の上書き管理 |
| `infrastructure.db.type` | `DatabaseType` | DB種別（postgresql/oracle）とリポジトリ実装クラスの対応enum |
| `infrastructure.db.repository` | `AbstractTableDefinitionRepository` | Oracle/Postgres共通のリポジトリ基底クラス |
| | `OracleTableDefinitionRepository`, `PostgresTableDefinitionRepository` | `TableDefinitionRepository`のDB別実装。対応するSQLは`src/main/resources/mapper/{oracle,postgresql}/tableDefinitionMapper.xml` |
| `infrastructure.db.repository.dto` | `DatabaseDto`, `TableDto`, `ColumnDto`, `ConstraintDto`, `ForeignKeyDto`, `IndexDto`, `TriggerDto`, `FunctionDto`, `SequenceDto`, `TypeDto` | MyBatisのResultMap受け皿となるDTO（`toEntity()`で`domain.model`配下のエンティティへ変換される） |
| | `DtoValues`（パッケージプライベート） | DTOからエンティティへの変換時の値の正規化（値が無いことを空文字へ揃える・区切り文字で連結された値をリストへ分解する） |
| `infrastructure.file.repository` | `LocalFileRepository` | `FileRepository`実装（ローカルファイルシステムへの読み書き） |
| | `SidecarYamlRepository` | `SidecarRepository`実装（サイドカーYAML読み込み、SnakeYAML使用）。`tables`（付帯情報）と`relations`（論理リレーション）の双方を解釈する |
| `infrastructure.path` | `DefaultOutputPathResolver` | `OutputPathResolver`のデフォルト実装 |
| `infrastructure.snapshot` | `JacksonSnapshotSerializer` | `SnapshotSerializer`のJackson実装 |

## config層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `config` | `PropertyLoader` | `conf/*.properties`読み込みユーティリティ（カンマ区切りの値は各要素の前後の空白を除去し、空要素を除く） |
| `config.module` | `ExportTableDefinitionModule` | Guiceの束縛定義（IF→実装クラスの対応）。接続先の`DatabaseType`をコンストラクタで受け取り、`TableDefinitionRepository`の実装を選ぶ。新規リポジトリ/ドメインサービス追加時はここに束縛を追加する |

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
（`MarkdownAssert`、`EntityFixtures`、`ForeignKeyFixtures`など）。
