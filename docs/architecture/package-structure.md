# パッケージ構成リファレンス

`com.export_table_definition` 配下の全パッケージと主要クラスの一覧。
役割の全体像は先に [overview.md](./overview.md)、ドメインの概念同士の関係・用語は [domain-model.md](./domain-model.md) を参照。

## エントリーポイント（`com.export_table_definition`直下）

| クラス | 役割 |
|---|---|
| `ExportTableDefinition` | `main()`。処理全体（入力の検証・DBへの接続・DIコンテナの組み立てを含む）を1つのtry-catchで囲んで例外を1箇所で捕捉し、`FailureReporter`で報告したうえで、終了状態を終了コードへ変換する |
| `CliArguments`（パッケージプライベート） | CLI引数・環境変数の解析（`--check`・`--rm-dist`、DB接続情報・実行時設定の上書き値）。実行時設定のCLI引数名・環境変数名は設定ファイルのキーから導く。解釈できない引数・`ETD_`で始まる未知の環境変数（書き誤り等）は`requireKnownArguments()`で誤りとする |
| `OutputDirectoryValidator`（パッケージプライベート） | 出力先（`outputPath`）をDBへ接続する前に検証する。既存のファイル（ディレクトリではないもの）を指す場合と、`--rm-dist`指定時に削除してはならないディレクトリ（`OutputPathResolver.isRemovableOutputDir`）を指す場合は`UserCorrectableException`を投げる。DB種別に依存しない部品のDIコンテナから取得する |
| `ExportTableDefinitionProperties`（パッケージプライベート） | `conf/ExportTableDefinition.properties`の設定項目の仕様（キー・既定値・値の形式）と検証を1箇所に持つ（ファイルの読み込みは`PropertyLoader`に委ねる）。CLI引数・環境変数による上書き値で上書きしてから検証する。キーの省略＝未指定、未知のキー・整数として読めない値・出力対象の条件の誤りは、まとめて`InvalidConfigurationException`で報告する |

## presentation層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `presentation` | `ExportTableDefinitionController` | エントリーポイントから呼ばれ、ユースケースを実行して結果を`ResultDto`/`DiffCheckResultDto`へ変換する。例外は捕捉せずエントリーポイントまで伝える |
| | `FailureReporter` | エントリーポイントが捕捉した例外を受け取り、利用者が直せる誤り（`UserCorrectableException`）か想定外の失敗かに応じて報告する（表示に含まれない原因の併記、想定外の失敗はスタックトレースをログへ） |
| | `DiffReportFormatter`（パッケージプライベート） | `checkDiff`の差分メッセージ組み立て。`ContentDiff`のunified diffを1オブジェクトあたり・全体それぞれ行数の上限付きで含める |
| `presentation.dto` | `ResultDto` | 通常実行（`execute`）の処理結果（成功時のメッセージ）を表すrecord。失敗時の報告は`FailureReporter`が組み立てる |
| | `DiffCheckResultDto` | `--check`モード（`checkDiff`）の処理結果（差分の報告・差分の有無）を表すrecord。差分の有無から終了状態を返す |
| `presentation.type` | `ProcessResult` | 処理結果種別（成功/失敗）のenum。コンソールに出す`[result]:`の行を組み立てる |
| | `ExitStatus` | 終了状態と終了コード（0＝成功・差分なし／1＝`--check`で差分あり／2＝失敗）のenum |

## application層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `application` | `ExportTableDefinitionUsecase` | テーブル定義出力（通常実行）のユースケースのインターフェース（`exportTableDefinition`） |
| | `CheckDocumentDiffUsecase` | DB vs ドキュメントの差分検知（`--check`モード）のユースケースのインターフェース（`checkDocumentDiff`） |
| | `ExportRequest`, `CheckDiffRequest` | 各ユースケースメソッドへの入力をまとめたrecord。エントリーポイント→コントローラー→ユースケースを分解・再構築せず通過する。`CheckDiffRequest`はMarkdownの描画・ER図の生成を行わないため`erDiagramMaxNodes`・`rmDist`を持たない |
| | `TargetSelection` | 両requestが持つ出力対象の絞り込み条件（`TableTargetScope`・`OutputObjectType`の集合・サイドカーYAMLのパス）のrecord。`of()`で設定値の文字列を入口で型へ変換・検証する（テーブル名パターンと出力対象オブジェクト種別の誤りはまとめて報告する） |
| `application.impl` | `ExportTableDefinitionUsecaseImpl` | 通常実行のユースケース実装。MarkdownとスナップショットのExportSinkを渡して`SchemaExporter`に取得・書き出しさせる。`--rm-dist`の削除は取得の成功後に行う（削除してよい出力先かは、入口の`OutputDirectoryValidator`が検証済み） |
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
| `domain.model.target` | `TableTargetScope` | テーブル定義出力対象の範囲（スキーマ名リスト＋テーブル名パターン）を表す値オブジェクト。実行設定から1回だけ生成し、`matches(TableEntity)`で各テーブルを判定する（パターンの判定は、パッケージプライベートの`TableTargetFilter`が行う。テーブル名・スキーマ名の部分が空のパターンは誤り） |
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
| `FileRepository` | ファイル操作IF（`writeFile`/`appendFile`/`createDirectory`、パスの状態の問い合わせ用の`exists`/`isDirectory`に加え、差分検知用の`listFiles`/`readFile`、一時ディレクトリ操作用の`createTempDirectory`/`deleteDirectory`を持つ） |

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
| `infrastructure.db` | `ConnectionSettings` | 検証済みのDB接続情報。`conf/mybatis.properties`の値をCLI引数・環境変数の値で上書きし、組み立てる時に検証する（`driver`・`url`は必須、未知のキーは誤り） |
| | `MyBatisSqlSessionFactories` | `ConnectionSettings`からMyBatisの`SqlSessionFactory`を生成する（状態を持たない。生成したものはDIコンテナで使い回す） |
| | `DatabaseTypeDetector` | DBへ接続して接続先のDB種別を判定する。DBに接続できない場合・非対応のDBの場合は`UserCorrectableException`を投げる |
| `infrastructure.db.type` | `DatabaseType` | DB種別（postgresql/oracle）とリポジトリ実装クラスの対応enum |
| `infrastructure.db.repository` | `AbstractTableDefinitionRepository` | Oracle/Postgres共通のリポジトリ基底クラス。`SqlSessionFactory`をコンストラクタで受け取る。SQLの失敗は、どのSQLかを添えて包む（DBが返したエラーは原因として保持する） |
| | `OracleTableDefinitionRepository`, `PostgresTableDefinitionRepository` | `TableDefinitionRepository`のDB別実装。対応するSQLは`src/main/resources/mapper/{oracle,postgresql}/tableDefinitionMapper.xml` |
| `infrastructure.db.repository.dto` | `DatabaseDto`, `TableDto`, `ColumnDto`, `ConstraintDto`, `ForeignKeyDto`, `IndexDto`, `TriggerDto`, `FunctionDto`, `SequenceDto`, `TypeDto` | MyBatisのResultMap受け皿となるDTO（`toEntity()`で`domain.model`配下のエンティティへ変換される） |
| | `DtoValues`（パッケージプライベート） | DTOからエンティティへの変換時の値の正規化（値が無いことを空文字へ揃える・区切り文字で連結された値をリストへ分解する） |
| `infrastructure.file.repository` | `LocalFileRepository` | `FileRepository`実装（ローカルファイルシステムへの読み書き） |
| | `SidecarYamlRepository` | `SidecarRepository`実装（サイドカーYAML読み込み、SnakeYAML使用）。`tables`（付帯情報）と`relations`（論理リレーション）の双方を解釈する。ファイルが無い・YAMLとして解釈できない場合は`UserCorrectableException`を投げ、個々の記述の誤り（未知のキー等）は読み飛ばして警告する |
| `infrastructure.path` | `DefaultOutputPathResolver` | `OutputPathResolver`のデフォルト実装 |
| `infrastructure.snapshot` | `JacksonSnapshotSerializer` | `SnapshotSerializer`のJackson実装 |

## config層

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `config` | `PropertyLoader` | `conf`ディレクトリのプロパティファイルを探して読み込み、キーと値の組として返す（ファイルの探索・読み込みのみを担い、設定項目の仕様と検証は読み込む側が持つ）。`conf`ディレクトリ・設定ファイルが見つからない場合は`InvalidConfigurationException`をスローする |
| | `InvalidConfigurationException` | 設定の誤り（設定ファイルが見つからない、未知のキー、値が不正等）を表す例外（`UserCorrectableException`の派生）。`PropertyLoader`・`ExportTableDefinitionProperties`・`ConnectionSettings`が投げる |
| `config.module` | `ExportTableDefinitionModule` | Guiceの束縛定義（IF→実装クラスの対応）のうち、DB種別に依存しないもの。DBへ接続する前に組み立て、入力の検証にも使う。新規リポジトリ/ドメインサービス追加時はここに束縛を追加する |
| | `DatabaseDependentModule` | DB種別が決まってから、`ExportTableDefinitionModule`のコンテナの子として束縛するもの。接続先の`DatabaseType`と`SqlSessionFactory`をコンストラクタで受け取り、`SqlSessionFactory`を束縛して`TableDefinitionRepository`の実装を選ぶ。それに依存するユースケースも束縛する |

## shared（レイヤーの外）

レイヤーの外に置き、どの層からも依存してよい。自身はJDK以外に依存せず、失敗の分類を伝える例外だけを置く。

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `shared.exception` | `UserCorrectableException` | 利用者が設定・入力・実行環境を見直せば解消する誤り（設定の誤り・サイドカーYAMLの構文誤り・DBに接続できない等）を表す例外。投げるのは入口（CLI引数・設定・出力先の検証）と、利用者の入力・実行環境に触れるインフラ（DBへの接続、サイドカーYAMLの読み込み）だけで、ドメイン層・アプリケーション層では投げない。`FailureReporter`はこの例外かそれ以外かで報告を切り替える |

## リソース（Java外）

| パス | 役割 |
|---|---|
| `src/main/resources/conf/ExportTableDefinition.properties` | 出力対象スキーマ/テーブル、出力先、chunkSize等のアプリ設定 |
| `src/main/resources/conf/mybatis.properties.template` | DB接続情報テンプレート（実ファイルは`mybatis.properties`としてgitignore対象） |
| `src/main/resources/mybatis-config.xml` | MyBatisのメイン設定（DB種別ごとのmapper読み込み等） |
| `src/main/resources/mapper/oracle/tableDefinitionMapper.xml` | Oracle向けSQL定義 |
| `src/main/resources/mapper/postgresql/tableDefinitionMapper.xml` | PostgreSQL向けSQL定義 |
| `src/main/resources/log4j2.xml` | ログ設定（ログファイルへの出力に加え、このツールのWARNログを`[warn]:`付きで標準エラー出力へ出す） |

## テスト

`src/test/java/com/export_table_definition` 配下は本体パッケージとほぼ1:1で対応する構成
（`application`, `config`, `domain`, `infrastructure`, `presentation`, `testsupport`）。
`testsupport`にはテスト用のビルダー・フィクスチャ等の共通部品を置く
（`MarkdownAssert`、`EntityFixtures`、`ForeignKeyFixtures`など）。
