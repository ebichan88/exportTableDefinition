# アーキテクチャ概要

本プロジェクトは、DBのメタ情報（テーブル・カラム・制約・外部キー・トリガー・関数・シーケンス・ユーザー定義型等）を
取得し、Markdown形式のテーブル定義書・ER図を出力するCLIアプリケーションである。あわせて、同じ取得結果から
機械可読なスキーマのスナップショット（JSON Lines）を出力し、`--check`モードではDBとコミット済みのスナップショットの差分を検知する。
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
| application | `application`, `application.impl` | ユースケース（テーブル定義出力・差分検知それぞれのフロー制御）を実装する |
| domain | `domain.model.*`, `domain.repository`, `domain.service.*` | エンティティ・値オブジェクト・リポジトリIF・書き込み処理（ドメインサービス）を持つ、DB種別に依存しない中核 |
| infrastructure | `infrastructure.db`, `infrastructure.file`, `infrastructure.path`, `infrastructure.snapshot` | MyBatisによるDBアクセス、ファイル入出力、出力パス解決、JSON変換などドメインIFの実装を提供する |
| config | `config`, `config.module` | プロパティ読み込み、Guiceによる依存関係の束縛（DI設定） |
| shared | `shared.exception` | 層をまたいで失敗の分類を伝える例外（`UserCorrectableException`） |

`config`と`shared`は4層の外に置く。`shared.exception`は、どの層からも依存してよい唯一のパッケージで、自身はJDK以外に依存しない。
置くのは失敗の分類を伝える例外だけとし、`shared`の直下や、例外以外の共通部品の置き場所にはしない
（範囲を広げると、層に属さない何でも置き場になり、依存の向きのルールが形骸化するため）。

`domain.model` は概念ごとのサブパッケージ（`table`・`relation`・`sidecar`・`target` 等）に分かれている。
ドメインの概念・用語・主なルールの置き場所は [domain-model.md](./domain-model.md)、
パッケージ・クラス単位の役割は [package-structure.md](./package-structure.md) を参照。

## 実行フロー

1. `ExportTableDefinition.main()` が `CliArguments`（CLI引数の解析・環境変数からのDB接続情報の
   上書き値の解決・`--check`/`--rm-dist`フラグの判定）でモードを判定し、以降の処理全体を1つのtry-catchで囲んで実行する。
   例外の捕捉と終了コードへの変換はここで1箇所にまとめて行い、捕捉した例外は`presentation.FailureReporter`が報告する
   （[例外の扱いと終了コード](#例外の扱いと終了コード)を参照）。
2. `ExportTableDefinition.run()`（`--check`時は`runCheck()`）が、まず入力を検証する（[入力の検証](#入力の検証)を参照）。
   - `CliArguments.requireKnownArguments()`が、解釈できない引数（書き誤り等）が無いことを確かめる
   - `ExportTableDefinitionProperties.load()`が `conf/ExportTableDefinition.properties` の設定値（出力対象スキーマ／テーブル、
     出力先パス、chunkSize、erDiagramMaxNodes、outputObjects、annotationPath）を読み込み・検証し、
     `ExportRequest`（`--check`時は`erDiagramMaxNodes`を持たない`CheckDiffRequest`）へ変換する。
     出力対象の絞り込み条件（スキーマ・テーブル・outputObjects・サイドカーYAMLのパス）は、生の文字列のまま後続へ渡さず、
     `TargetSelection.of()`が型（`TableTargetScope`・`OutputObjectType`の集合）へ変換・検証する
   - 設定の誤りは`config.InvalidConfigurationException`1種類で、見つかった誤りをまとめて表す。DBへの接続や`--rm-dist`による
     削除より前に`[result]:FAIL`として報告されるため、エントリーポイントは読み込み処理の内部で起きる個々の例外を知らずに済む
   - requestはエントリーポイント→コントローラー→ユースケースの3層を、分解・再構築を繰り返さず同じrecordのまま通過する
   - DB種別に依存しない部品のDIコンテナ（`ExportTableDefinitionModule`）を組み立て、`OutputDirectoryValidator`が出力先
     （`outputPath`）を検証する。既存のファイルを指す場合と、`--rm-dist`で削除してはならないディレクトリ（ルート・ホーム
     ディレクトリ・カレントディレクトリ自体）を指す場合は、DBへ接続する前に`[result]:FAIL`として報告する
3. 入力の検証に成功した後、`MyBatisSqlSessionFactory` に接続情報を設定してDBへ接続し、接続先のDB種別を判定する。
   2.のDIコンテナの子として、DB種別に依存する部品（`DatabaseDependentModule`）を束縛したコンテナを組み立て、
   `ExportTableDefinitionController` を取得する（[設定・DI](#設定di)を参照）。
4. コントローラーは `ExportTableDefinitionUsecase.exportTableDefinition()`（`--check`時は
   `CheckDocumentDiffUsecase.checkDocumentDiff()`）を呼び出し、結果を `ResultDto`（`--check`時は差分の有無を持つ
   `DiffCheckResultDto`）に変換する。例外は捕捉せず、エントリーポイントまで伝える。
5. 通常実行のユースケース（`ExportTableDefinitionUsecaseImpl`）は、以下を順に行う。DBからの取得と出力形式ごとの書き出しの
   段取りは `SchemaExporter`（`application.impl`、パッケージプライベート）に委ね、差分検知のユースケースと共有する。
   - `SchemaExporter.fetchTargets()`：`TableDefinitionRepository` からテーブル一覧・外部キー・トリガー等をMyBatis経由で取得し、
     `SidecarRepository` でサイドカーYAML（手動付帯情報・論理リレーション）を読み込む。
     `ExportTargetConsistencyDomainService`（`domain.service.target`）が両者を出力対象のテーブルと突き合わせ、
     一括取得分を `ExportTargets` にまとめる
   - `--rm-dist`指定時は、ここまでの取得に成功してから出力先を削除する（取得に失敗した場合に既存の出力だけが消えないようにするため）。
     削除してよい出力先かは、ユースケースを呼ぶ前に入口（2.）で検証済みである
   - `SchemaExporter.export()`：取得した情報を、出力形式ごとの `ExportSink`（`domain.service.export`）へ渡して書き出す
     - Markdown（`MarkdownExportSinkFactory`）: `TableDefinitionWriterDomainService` / `ErDiagramWriterDomainService` /
       `ObjectListWriterDomainService`（いずれも `domain.service.writer`）がMarkdownを組み立てて `FileRepository` 経由で出力
     - スナップショット（`SnapshotExportSinkFactory`）: `SchemaSnapshotWriterDomainService`（`domain.service.snapshot`）が、
       同じ取得結果から常にスキーマのスナップショット（JSON Lines）を出力

## 例外の扱いと終了コード

失敗は次の3種類に分けて扱う。

| 種類 | 例 | 表し方 | 利用者への報告 |
|---|---|---|---|
| 利用者が直せる誤り | 設定ファイルの誤り、サイドカーYAMLの構文誤り、`--rm-dist`の出力先が危険、DBに接続できない、非対応のDB | `shared.exception.UserCorrectableException`（設定ファイルの誤りは派生の`config.InvalidConfigurationException`）。検知した箇所で、何を直せばよいかをメッセージに書いて投げる | `[result]:FAIL`＋メッセージ。ログにスタックトレースは残さない |
| 想定外の失敗 | I/Oの失敗、SQLの失敗、不具合（NPE等）、JVMのエラー | 非検査例外のまま伝える（検査例外は非検査例外で包む） | `[result]:FAIL`＋メッセージ＋ログの場所。ログにスタックトレースを残す |
| 業務上の結果 | `--check`の差分あり、孤児付帯情報、除外した関連 | 例外にせず値で返す（`DiffResult`・`ConsistencyFinding`） | 差分の報告・警告ログ |

- 利用者が直せる誤りを投げるのは、入口（CLI引数・設定・出力先の検証）と、利用者の入力・実行環境に触れるインフラ
  （DBへの接続、サイドカーYAMLの読み込み）だけで、ドメイン層・アプリケーション層では投げない。HTTPの400系のように、
  「利用者が直せる」という分類は入口側の関心であり、ドメインの概念ではないため。入力の誤りはユースケースを呼ぶ前に入口で検証し、
  インフラは外部に触れて初めて分かる誤り（DBに接続できない、YAMLとして読めない等）だけを投げる。
  入口とインフラの双方から投げるため、例外はどの層からも依存できるレイヤーの外（`shared.exception`）に置く
- 捕捉するのはエントリーポイント（`ExportTableDefinition.main()`）の1箇所だけ。設定の読み込み・DBへの接続・DIコンテナの
  組み立てを含む処理全体を1つのtry-catchで囲むため、捕捉漏れがない。コントローラー・ユースケースでは捕捉しない。
  捕捉した例外は`presentation.FailureReporter`へ渡し、種類に応じた報告（画面・ログ）を任せる
- 途中の層でcatchしてよいのは、(a) 検査例外を非検査例外で包む、(b) 下位の例外を利用者が直せる誤りへ置き換える、
  (c) フォールバックする（`conf/mybatis.properties`が無い場合に、CLI引数・環境変数の接続情報だけで続ける等）場合のみ。
  包むときは原因（`cause`）を必ず渡し、tryの範囲は置き換えたい呼び出しだけに絞る
  （例: `AbstractTableDefinitionRepository`はSQLの呼び出しだけを包み、DTO→エンティティの変換の失敗は包まない）。
  catchしてログを出してから再スローすることはしない（ログの出力も`FailureReporter`が行う）
- `FailureReporter`は例外の連鎖（原因）をたどり、表示に含まれていない情報を持つ原因を`[cause]`として併記する
  （包んだ箇所で、DBが返したエラー等の原因が失われないようにするため。原因のメッセージを繰り返しているだけのMyBatisの例外等は省く）
- ドメイン層に検査例外は使わない。呼び出し側に判断を委ねたい結果は、値（`Optional`・`ConsistencyFinding`・真偽値等）で返す
- 警告（処理は続けられるが利用者が確認すべき事柄。孤児付帯情報、サイドカーYAMLの記述の誤り等）は、WARNレベルのログとして出す。
  `log4j2.xml`が、このツールのWARNログをログファイルに加えてコンソール（標準エラー出力）へも`[warn]:`付きで出す
  （ログファイルにしか出ないと、「警告して続行」が実際には「黙って続行」になるため）。警告は終了コードに影響しない
- 終了コード（`presentation.type.ExitStatus`）は、0＝成功（`--check`で差分なしを含む）、1＝`--check`で差分あり、2以上＝失敗
  （現在は`FAILURE`＝2のみ）。失敗は1つの値ではなく「2以上」という範囲で定め（POSIXの`diff`・`cmp`が失敗を「>1」と定めるのと同じ）、
  利用者には2以上かで判定してもらう。失敗の種類を分ける場合は3以上の値を足せばよく、既存の判定を壊さない。
  「検査して見つかった」という結果は、将来ほかのモードで増えても1を共通で使う
- JVMのエラー（`Error`）も`main()`で捕捉するのは、捕捉しないとJVMが終了コード1で終わり、差分ありと区別できなくなるため。
  ただし、JVMが起動できない場合（jarが見つからない・Javaのバージョンが古い・JVMオプションの誤り等）や`main()`に入る前の失敗は、
  このツールが捕捉する前にJavaの仕様で1になる（READMEに注記している）。同梱の`run.sh`・`run.bat`自体のエラーは2で返す

## 入力の検証

入力ごとの仕様（必須・値の形式・未指定の場合・誤りとして扱う値）はREADMEの各節に記載し、以下の方針で扱う。

- 「必須」は値で決める。キーの省略と値が空は同じ「未指定」として扱い、既定値がある項目は未指定を許す。
  既定値が無いもの（DB接続情報の`driver`・`url`）だけを必須とする
- 実行のしかたを決める入力（設定ファイル・CLI引数・DB接続情報）は、未知のキー・引数、値の形式の違反、指定した参照先
  （`annotationPath`のファイル）が無いことを、すべて失敗にする。既定値へ黙って置き換えたり、警告で続行したりしない
  （書き誤りに気付けないまま、意図しない出力やモードで実行されるのを防ぐため）。なお、指定したスキーマがDBに存在するかは検証しない
- ドキュメントに載せる内容の入力（サイドカーYAML）は、ファイルとして読めない場合だけ失敗にし、個々の記述の誤り
  （キーの形式の誤り、必須項目の欠け、未知の多重度、未知のキー、マップであるべき箇所がマップでない等）は該当箇所を
  読み飛ばして警告する（付帯情報の一部の書き誤りで、定義書全体の再生成を止めないため）
- 検証は入口でまとめて行い、見つかった誤りを一度に報告する（1つ直して再実行するたびに次の誤りが見つかる、を繰り返させない）
- 実行のしかたを決める入力（CLI引数・設定ファイル・出力先）の誤りは、DBへ接続する前に報告する。接続した後に検証すると、
  DBに接続できない環境（接続情報の誤り・DBの停止中）では接続エラーだけが報告され、それを直して再実行するまで入力の誤りに気付けないため。
  出力先の検証のようにDIコンテナの部品を使う検証もこの時点で行えるよう、DIコンテナを2段階で組み立てている（[設定・DI](#設定di)を参照）

| 入力 | 検証する場所 | 検証のタイミング |
|---|---|---|
| CLI引数 | `CliArguments.requireKnownArguments` | 最初（DBへの接続前） |
| 設定ファイルの形式（キー・整数） | `ExportTableDefinitionProperties` | CLI引数の後（DBへの接続前） |
| 出力対象の条件（テーブル名パターン・出力対象オブジェクト種別） | `TableTargetFilter.of` / `OutputObjectType.parse`（`TargetSelection.of`が2つの誤りをまとめる） | 同上 |
| 出力先（`outputPath`が既存のファイルを指さないか、`--rm-dist`で削除してよいか） | `OutputDirectoryValidator` | 設定ファイルの後（DBへの接続前） |
| DB接続情報 | `MyBatisSqlSessionFactory.requireValidConnectionSettings` | DBへの接続の直前 |
| サイドカーYAML | `SidecarYamlRepository` | DBからの取得・`--rm-dist`の削除の前 |

値の意味に関するルール（出力対象オブジェクト種別の値・テーブル名パターンの書式）はドメインの型に、設定ファイルという形式に
関するルール（キーの有無・未知のキー・整数として読めるか）は入口側に持たせ、重複させない。

## DB種別の切り替え（Oracle / PostgreSQL）

`infrastructure.db.type.DatabaseType` （enum）がDB種別名と対応する
`infrastructure.db.repository.*TableDefinitionRepository` 実装クラスを紐づけている。
エントリーポイントが（入力の検証に成功した後に）`MyBatisSqlSessionFactory.getConnectionDbName()` で接続先のDB種別を判定して
`DatabaseDependentModule` のコンストラクタへ渡し、`configure()` が `DatabaseType.getRepositoryClass()` を通じて
`TableDefinitionRepository` の実装クラスをDBごとに動的に束縛する（束縛定義の中ではDBへ接続しない）。DB固有のSQLは
[src/main/resources/mapper/oracle/tableDefinitionMapper.xml](../../src/main/resources/mapper/oracle/tableDefinitionMapper.xml) と
[src/main/resources/mapper/postgresql/tableDefinitionMapper.xml](../../src/main/resources/mapper/postgresql/tableDefinitionMapper.xml) に分離されている。
両リポジトリは共通処理を `AbstractTableDefinitionRepository` に持つ。

PostgreSQL固有オブジェクト（トリガー／関数・プロシージャ／シーケンス／ユーザー定義型）の出力は
`domain.model.target.OutputObjectType` で種別ごとに絞り込み可能。Oracle接続時はこれらの取得・出力自体が行われない。

なお論理リレーション（サイドカーYAML由来）はDBに依存しないため、PostgreSQL／Oracleの双方で利用できる。

## メモリ効率のための分割取得

対象スキーマ全体を一度にメモリへ載せないよう、`SchemaExporter` は以下の方針で処理する。

- テーブル一覧・外部キー・トリガーは軽量なため対象範囲全体を一括取得する（`fetchTargets`）
  （ER図でスキーマ・チャンクを跨いだ参照関係を解決するために全件が必要なため）
- カラム・インデックス・制約はテーブル数に比例して重くなるため、スキーマ単位かつ `chunkSize` 件ごとに
  取得・出力・破棄する（`exportSchemaTableDefinitions` / `exportTableDefinitionChunk`）。
  `TableDefinitionRepository.selectTableDetails()` がチャンク分をまとめて取得し、テーブルごとの `TableDetail` に
  組み立てて返す。そこへ一括取得分の外部キー・トリガー・手動付帯情報を合わせ、1テーブル分の出力内容
  （`TableDefinitionContent`）として各 `ExportSink` へ渡す
- 関数・プロシージャの定義本体はスキーマ単位で取得・出力・破棄する（`exportSchemaFunctionDefinitions`）

## 基本情報と生成日

各ドキュメントの先頭に掲載する基本情報（`domain.model.database.BaseInfoEntity`）は、DBのカタログから取得する
データベースの情報（`DatabaseEntity`：DB名・DBMS種別。`TableDefinitionRepository.selectDatabase()`）に、
ドキュメントの生成日を加えたもの。生成日はDBではなく実行時に決まる値のため、`SchemaExporter` がDIで受け取る
`java.time.Clock` から与える（`ExportTableDefinitionModule` が実行環境のタイムゾーンの時計を束縛する。テストでは固定の時計を渡せる）。

## 出力対象の突き合わせ

外部キーはスキーマ全体から、サイドカーYAMLは出力対象に関係なく読み込むため、出力対象の絞り込みで除外したテーブルや、
リネーム・削除されたテーブル／カラムを参照していることがある。`ExportTargetConsistencyDomainService` がそれらを
出力から除外し、利用者が気付くべき事柄を指摘（`domain.model.target.ConsistencyFinding`）として値で返す。
ログへの出力は呼び出し側の `SchemaExporter` が指摘の重要度（`INFO`／`WARN`）に応じて行う
（ドメインサービスはログ出力の手段に依存しない）。

| 突き合わせ | メソッド | 指摘 |
|---|---|---|
| 関連（物理外部キー・論理リレーション）の参照元・参照先が出力対象に存在するか | `resolveForeignKeys` | 除外した関連（物理外部キーは出力対象の絞り込み時は指摘しない） |
| 付帯情報に対応するテーブルが実在するか | `findOrphanTableAnnotations` | 孤児付帯情報（出力対象の絞り込み時は検出しない） |
| カラム備考に対応するカラムが実在するか | `findOrphanColumnAnnotations` | 孤児付帯情報（チャンクごとに出力対象のテーブルについて検出） |

## ER図生成

ER図生成のアルゴリズム（連結成分によるグループ分割、多重度判定ロジックなど）はREADME
（[../../README.md](../../README.md) の「ER図」節）に詳しい。実装は
`ErDiagramWriterDomainService`（書き込みの段取り）と `domain.model.relation.ForeignKeyGroup`
（1枚の図のノード算出・上限超過の判定）、`ForeignKeyGroups`（連結成分の算出と、1枚に収まる範囲での
まとめ直し。`compose()`がページ構成（`PageComposition`）を1回で決める）、`domain.model.relation.Cardinality`（多重度判定）が中心。

## 出力ファイルの命名規則と相対リンク

Markdownドキュメントのファイル名・配置（一覧・ER図は出力ベースディレクトリ直下、テーブル定義書・関数等の個別定義書は
`{DB名}/{スキーマ名}/{区分}/`配下）は`domain.service.path.DocumentLocations`に一元化している。
出力先の絶対パス（`OutputPathResolver`の実装）と、ドキュメント間の相対リンク（`domain.service.writer.template`）の
双方がこの規則を参照するため、ファイル名を変更してもパスとリンクが食い違わない。一覧の種別ごとの接頭辞・タイトルは
`domain.model.document.ListDocumentType`が持つ。関数・プロシージャの個別定義は関数名をファイル名とし、同じスキーマに
同名のもの（オーバーロード）がある場合のみ`{関数名}_{番号}`とする（番号はSQLが関数名ごとに振る）。

どの一覧ドキュメントを出力するか（テーブル一覧は常に、それ以外は対象が1件以上ある場合のみ）は
`MarkdownExportSinkFactory`の`listDocuments()`が1箇所で決め、一覧の書き出しと、テーブル一覧に掲載する関連ドキュメントへの
リンクの双方がこの結果を用いる（空の一覧を出力しない・存在しない一覧へリンクしない）。

行数の多い表を分割した分割ページは、本体ページと同じディレクトリに`{本体ページのファイル名}_{ページ番号}.md`として
置く（`OutputPathResolver.resolvePageFile`）。`PagedSectionWriter`は本体ページのパスのみを受け取り、分割ページの
パスとページ間のリンクをそこから導く。

## スキーマのスナップショット（中間表現）

Markdownと同じ取得結果から、常にスキーマ情報を構造化したスナップショット（JSON Lines）を
`{outputPath}/snapshot/{DB名}/`配下へ出力する。Markdownは最終成果物（表示形式）であり機械処理に向かないため、
差分検知・将来のlint/coverage等の土台となる機械可読な中間表現を別に持つ位置づけ。

- モデルは`domain.model.snapshot`配下のrecord（`TableSnapshot`等）。エンティティから変換する際に、
  値が無いこと（エンティティでは空文字）をnullで表す（JSONでは項目ごと省略される）。
  実行のたびに変わる生成日は含めない
- 配置（`snapshot/{DB名}/database.json`、`snapshot/{DB名}/{スキーマ名}/{種別}.jsonl`）は
  `domain.service.path.SnapshotLocations`に一元化し、出力先の絶対パス（`OutputPathResolver`の実装）と
  比較時のファイル種別の判定の双方がこの規則を参照する
- JSONへの変換はドメイン層のIF（`SnapshotSerializer`）を介し、実装（`JacksonSnapshotSerializer`）はインフラ層に置く。
  Jacksonへの依存をドメイン層へ持ち込まないため
- 書き込みは`SchemaSnapshotWriterDomainService`が`FileRepository`・`OutputPathResolver`経由で行う
- メモリ効率のための分割取得の方針は変えない。テーブルは`exportTableDefinitionChunk`で`TableDefinitionContent`を
  組み立てた時点でMarkdownと並べて1行ずつスキーマ単位の`tables.jsonl`へ追記する（スキーマの処理開始時に
  `ExportSink.beginSchemaTables()`→`initTableFile`で空にしてから追記するため、前回実行時の内容へ追記されることはない）。
  関数は定義本体をスキーマ単位で取得した時点で`functions.jsonl`へ出力する

なお、SQLは構造化した値のみを返し、Markdown向けの表示用の組み立て・エスケープ（`|`→`\|`等）は
`domain.service.writer.template`配下で行う。SQL側でエスケープするとスナップショットにもMarkdown記法が混入するため。
SQLの取得結果（DTO）からエンティティへの変換時（`infrastructure.db.repository.dto`）に、値の形をドメインの表現へ揃える。
値が無いことは空文字で表し（Oracleでは空文字がNULLとして返るため、DBによらず揃える）、SQLが区切り文字で連結して返す
外部キーの列名（カンマ区切り）・トリガーの対象イベント（スラッシュ区切り）は`List<String>`へ分解する。
表示用の連結・空欄の描画はテンプレートが行う。
主キー・NOT NULL・一意性・循環などの真偽値もSQLは真偽値で返し（PostgreSQLは`boolean`、`boolean`型を持たない
Oracleは`1`/`0`）、エンティティも`boolean`で保持する。表のセルの「○」は`MarkdownTemplateSupport.marker()`で描画する。

## DB vs ドキュメントの差分検知（`--check`モード）

`ExportTableDefinition.main()`にCLI引数`--check`を渡すと、通常のドキュメント出力の代わりに
`ExportTableDefinitionController.checkDiff()` → `CheckDocumentDiffUsecaseImpl.checkDocumentDiff()`を呼び出す。

DBからの取得と出力は`SchemaExporter`が以下のように分けて持ち、通常実行（`ExportTableDefinitionUsecaseImpl`）と
`--check`（`CheckDocumentDiffUsecaseImpl`）の双方が利用する。両者は取得処理を共有し、書き出し先の出力形式
（`ExportSink`のリスト）だけを切り替える。

- `fetchTargets()`: 一括取得する軽量な情報（基本情報・テーブル一覧・外部キー・トリガー・関数/シーケンス/型の一覧・
  サイドカー）を取得し、`domain.model.target.ExportTargets`にまとめる
- `export()`: `ExportTargets`から出力できるもの（一覧・ER図等）を`ExportSink.writeOverview()`で書き出した後、
  関数の定義本体をスキーマ単位で、テーブルの詳細情報をスキーマ・チャンク単位で取得し、各`ExportSink`へ渡す。
  出力形式ごとの違い（何をどのファイルへ書くか）は`ExportSink`の実装が持ち、`SchemaExporter`は出力形式を意識しない

`checkDocumentDiff()`は、`outputPath`（比較先）には手を入れず、スナップショットの`ExportSink`のみで一時ディレクトリへ向けて
`export()`を呼び出した上で（Markdownの描画・ER図の生成は行わない）、生成結果と`outputPath`配下の`snapshot/`を
`SnapshotDiffDomainService.compare()`で比較する。JSON Linesの行をオブジェクト（`SnapshotKind.identify()`:
`スキーマ名.名前`、関数は引数を含む）で突き合わせ、追加/削除/内容不一致をオブジェクト単位で報告する。
`database.json`等それ以外のファイルはファイル単位で比較する。

スナップショットは生成日を含まないため、生成日と別の日に`--check`を実行しても差分にならない。その代わり、
Markdownのみに生じた差分（手作業での編集等）は検知しない。

内容が一致しないオブジェクトには、変更箇所を示すunified diffを付ける（`domain.model.snapshot.ContentDiff`）。
`SnapshotDiffDomainService`が、比較前に生成側・コミット側それぞれの行を`SnapshotSerializer.formatForDiff()`で
1項目1行・配列は1要素1行へ整形し（生の1行のJSONのままだと行単位のdiffが「丸ごと削除+丸ごと追加」にしか
ならないため）、`UnifiedDiffGenerator`（Myers法による自前実装。外部ライブラリに依存しない）へ渡してdiffを
生成する。整形後の行番号はファイル上の行番号とは対応しない。`presentation.DiffReportFormatter.format()`
が、1オブジェクトあたり・全体それぞれに行数の上限を設けてメッセージへ含める（超えた分は省略した旨のみ表示。
対象自体はサマリの一覧に全件掲載されるため見落としにはならない）。

Writer層・SQL層は出力先パスに一切依存しないため無改修で再利用できる。一時ディレクトリの作成・削除は
（他のファイル操作と同様に）`FileRepository.createTempDirectory()`/`deleteDirectory()`を介して行い、
`try-finally`で必ず削除される。

終了コードは、差分なしの場合は0、差分が1件でもある場合は1、比較処理自体が失敗した場合（設定の誤り・DBに接続できない等）は
2以上（現在は2）となる（`presentation.type.ExitStatus`、[例外の扱いと終了コード](#例外の扱いと終了コード)を参照）。CI上でジョブの成否として扱えるほか、「差分あり」と「比較自体の失敗」を区別できる。

## サイドカーYAML（手動付帯情報・論理リレーション）

DBのメタ情報だけでは表現できない情報を、サイドカーYAML（プロパティ`annotationPath`で指定。コード上は`sidecarPath`と呼ぶ）として
マージできる。読み込みは `SidecarRepository`（実装: `infrastructure.file.repository.SidecarYamlRepository`）が一括で行い、
`domain.model.sidecar.Sidecar` として返す。`Sidecar` は性質の異なる2種類の情報を束ねる。
指定したファイルが無い・YAMLとして読めない場合は`UserCorrectableException`とし、個々の記述の誤りは読み飛ばして警告する
（[入力の検証](#入力の検証)を参照）。

| 種別 | YAMLキー | モデル | 反映先 |
|---|---|---|---|
| 手動付帯情報 | `tables` | `Annotations` / `TableAnnotation` | テーブル定義書の各セル（説明・備考・カラム備考） |
| 論理リレーション | `relations` | `ForeignKeyEntity`（`RelationType.LOGICAL`） | 「論理リレーション情報」セクション + ER図 |

実在しないテーブル・カラムに対する付帯情報（リネーム・削除の見落とし）は、[出力対象の突き合わせ](#出力対象の突き合わせ)で
指摘として検出し、警告ログへ出力する。

`SidecarYamlRepository`はYAMLの読み込みと型変換に専念し、以下のドメインルールはドメイン層へ委ねる。

- 「スキーマ.テーブル」形式のキー文字列の解析: `domain.model.table.TableKey#parse`
- 論理リレーションの関連名が省略された場合の自動生成（「テーブル名_列名..._lrel」形式）:
  `domain.model.relation.ForeignKeyEntity#resolveLogicalRelationName`
- 論理リレーションの多重度の既定値（1対多）: `domain.model.relation.Cardinality#DEFAULT_FOR_LOGICAL_RELATION`

読み込み元のパス等、ログ出力に必要なコンテキストを持つ警告（未知の形式・未知の多重度ラベル等）のみ
`SidecarYamlRepository`側に残す。

### 論理リレーションの合流

外部キー制約を張らないDBではカタログから読み取れる関連だけではER図がほとんど空になるため、
サイドカーで宣言した関連を補う。設計上の要点は「**物理外部キーと同じ集合へ合流させる**」こと。

- `ExportTargetConsistencyDomainService.resolveForeignKeys()` が、DBから取得した外部キーとサイドカー由来の
  論理リレーションを結合して `ForeignKeys.of()` に渡す。参照元・参照先の一方でも出力対象に
  存在しない関連は、ER図に片側だけのノードが現れるのを避けるため除外し、除外したことを指摘として返す
- 合流させることで、ER図のグループ分割（`ForeignKeyGroups` の連結成分算出）、スキーマ跨ぎ関連の抽出
  （`ForeignKeys.crossSchema()`）、被参照側の解決（`incomingOf`）にも**追加実装なしで反映される**
- 読み手が「DBに制約がある」と誤読しないよう、2箇所で区別する
  - テーブル定義書：`ForeignKeys.physicalOf()` / `logicalOf()` で由来ごとに取り出し、別セクションへ掲載
  - ER図：`RelationType` が持つ線種を `Cardinality.getNotation(RelationType)` が組み立て、
    物理は実線（`||--o{`）、論理は破線（`||..o{`）で描画する

## 設定・DI

- `config.PropertyLoader`: `conf`ディレクトリのプロパティファイルを探して読み込み、キーと値の組として返す（ファイルの探索・読み込みのみを担う）
- `ExportTableDefinitionProperties`（エントリーポイントと同じパッケージ）: 読み込みは`PropertyLoader`に委ね、`conf/ExportTableDefinition.properties`の
  設定項目の仕様（キー・既定値・値の形式）と検証を1箇所に持つ（カンマ区切りの値は各要素の前後の空白を除去し、空要素を除く）
- `config.module.ExportTableDefinitionModule`: Guiceの束縛定義（インターフェース→実装クラスの対応表）のうち、DB種別に依存しないもの
- `config.module.DatabaseDependentModule`: DB種別が決まってから束縛するもの（`TableDefinitionRepository`と、それに依存するユースケース）

DIコンテナは2段階で組み立てる。出力先の検証等の入力の検証はDBへ接続する前に行う（理由は[入力の検証](#入力の検証)を参照）が、
DB種別は接続して初めて分かるため、まず`ExportTableDefinitionModule`だけでコンテナを組み立てて入力の検証に使い、DBへ接続した後に`DatabaseDependentModule`を束縛した
子のコンテナ（`Injector#createChildInjector`）を足して、コントローラーを取得する。
新しいリポジトリ実装やドメインサービスを追加する場合は、`ExportTableDefinitionModule`に束縛を追加する
（`TableDefinitionRepository`に依存するものだけは、親のコンテナでは解決できないため`DatabaseDependentModule`に置く）。
各クラスのコンストラクタには標準の`jakarta.inject.Inject`を付け、ドメイン層・アプリケーション層がGuiceのAPIに依存しないようにしている。
`application.impl.SchemaExporter`・`OutputDirectoryValidator`はパッケージプライベートのためモジュールでは束縛せず、Guiceのジャストインタイム束縛
（`@Inject`付きコンストラクタ）で生成する。束縛漏れ・`@Inject`の付け忘れは、エントリーポイントと同じ手順で実際にDIコンテナを組み立てる
`ExportTableDefinitionModuleTest`で検知する。
