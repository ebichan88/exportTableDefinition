# AGENTS.md

このリポジトリで作業するAIエージェント向けのガイド。

## このリポジトリについて

DBに接続し、Markdown形式のテーブル定義書・ER図を出力するJava(21)製CLIツール。
同じ取得結果から機械可読なスキーマのスナップショット（JSON Lines）も出力し、`--check`モードではDBとの差分を検知する。
スナップショットをAIからMCPのツールで検索できるようにするMCPサーバーも同じリポジトリにある。
機能仕様（出力されるドキュメントの種類、ER図の分割・多重度判定ルール、対応DBMS等）は
[README.md](./README.md) に詳しい。実装に手を入れる前に該当箇所を確認すること。

## ディレクトリ構成

Gradleのマルチプロジェクト構成。

- `cli/` — このツール本体（`cli/src/{main,test,integrationTest}`）
- `mcp-server/` — スナップショットを検索するMCPサーバー。cliのコードには依存せず、接点はスナップショットの形式だけ。
  設計は[docs/architecture/mcp-server.md](./docs/architecture/mcp-server.md)を参照

ルートの`build.gradle`にはサブプロジェクト共通の設定（プラグインのバージョン・Javaのバージョン・Spotless・doclint等）だけを置き、
固有の設定は各サブプロジェクトの`build.gradle`に書く。`docs/`・`scripts/`・`.github/`はリポジトリルートにある。
以降の文書で`src/...`と書いたパスは、特に断りが無ければ`cli/src/...`を指す。

## アーキテクチャドキュメント

Javaのパッケージ構成・レイヤー構成・DI・実行フロー・ドメインモデルは以下にまとめてある。
コードを読み進める前に目を通すとコンテキスト消費を抑えられる。

- [docs/architecture/overview.md](./docs/architecture/overview.md) — レイヤー構成、実行フロー、DB切り替え、
  メモリ効率のための分割取得、サイドカーYAML等、アーキテクチャ全体の設計意図
- [docs/architecture/domain-model.md](./docs/architecture/domain-model.md) — ドメインの概念（テーブル・関連・
  サイドカー・出力対象等）同士の関係図、主なルールを持つ場所、用語集（README・コード・会話で使う呼び方の対応）
- [docs/architecture/package-structure.md](./docs/architecture/package-structure.md) — 全パッケージ・主要クラスの
  役割一覧（リファレンス）
- [docs/architecture/mcp-server.md](./docs/architecture/mcp-server.md) — MCPサーバー（`mcp-server/`）の構成・ツール・
  スナップショット形式との互換の守り方

## 作業時の注意

- cliの構造の規約のうち機械的に判定できるものは、`ArchitectureTest`（`cli/src/test`、ArchUnit）で検査している。
  対象は、レイヤーの依存方向（`presentation → application → domain ← infrastructure`）、`shared.exception`の制約、
  `domain.model`配下のパッケージの循環（向きそのものは[domain-model.md](./docs/architecture/domain-model.md)に従う）、`java.nio.file.Files`・引数なしの`now()`・Guiceへの依存、接尾辞とパッケージの対応
  （[package-structure.md の「命名」](./docs/architecture/package-structure.md#命名)）。
  違反したら、テストを緩めずに依存や置き場所のほうを直す。規約の意図は[overview.md](./docs/architecture/overview.md#レイヤー構成)を参照。
- ドメインの概念（`domain.model` のクラス）を追加・改名・削除した場合や、ルールを持つ場所を移した場合は、
  `docs/architecture/domain-model.md` の図・ルール表・用語集も同じ変更で更新する。
  新しいクラス・メソッドの名前は用語集の用語（READMEで使っている呼び方）に揃え、同じものに別の名前を付けない。
- DB種別（Oracle/PostgreSQL）固有のSQLは
  `cli/src/main/resources/mapper/{oracle,postgresql}/tableDefinitionMapper.xml` に分離されている。
  両DBで挙動を揃える変更は両方のmapperを確認・修正すること。
- 新しいリポジトリ実装やドメインサービスを追加した場合は、
  `config/module/ExportTableDefinitionModule.java` にGuiceの束縛を追加する
  （接続先の`SqlSessionFactory`と、DB種別で実装が変わる`TableDefinitionRepository`だけは、DB接続後に組み立てる子のコンテナ用の
  `config/module/DatabaseDependentModule.java`に置く）。
  コンストラクタには`jakarta.inject.Inject`を付ける。
  インターフェースを持たない具象クラス（ユースケース等）は、`@Inject`付きコンストラクタがあればGuiceのジャストインタイム束縛で解決されるため束縛しない。
  束縛漏れは`ExportTableDefinitionModuleTest`（実際にDIコンテナを組み立てるテスト）で検知できる。
- ファイルI/O（読み書き・一覧取得・一時ディレクトリ作成／削除等）は必ず`domain.repository.FileRepository`経由で行う。出力先パスの組み立てやデフォルト値解決
  （未指定時のフォールバック等）は必ず`domain.service.path.OutputPathResolver`経由で行い、`application`層で
  パス文字列を直接組み立てない。Markdownのファイル名やドキュメント間の相対リンクも、テンプレート・Writerで
  `String.format`等により直接組み立てず、`domain.service.path.DocumentLocations`の規則を使う
  （スナップショットの配置は`domain.service.path.SnapshotLocations`）。既存の抽象化を素通りする実装が増えるとテストが実ディスクI/Oに依存し始め、
  レイヤーの意図も崩れるため、新規ロジックを追加する前にまずこの2つのIFで足りないか確認すること。
- 例外の扱いは [overview.md の「例外の扱いと終了コード」](./docs/architecture/overview.md#例外の扱いと終了コード) に従う。
  - 利用者が設定・入力・実行環境を見直せば解消する失敗は`shared.exception.UserCorrectableException`（設定ファイルの誤りは派生の
    `config.InvalidConfigurationException`）で表し、何を直せばよいかをメッセージに書いて投げる。それ以外（I/O・SQL・不具合）は
    非検査例外のまま伝える。ドメイン層に検査例外は使わず、呼び出し側に判断を委ねたい結果は値で返す。
  - `UserCorrectableException`を投げるのは、入口（CLI引数・設定・出力先の検証）と、利用者の入力・実行環境に触れるインフラ
    （DBへの接続、サイドカーYAMLの読み込み）だけ。ドメイン層・アプリケーション層では投げない。利用者が直せる入力の誤りは、
    ユースケースを呼ぶ前に入口で検証する（ユースケースの中で見つかる設定の誤りがあれば、その検証を入口へ移す）。
  - 捕捉はエントリーポイント（`ExportTableDefinition.main()`）の1箇所だけで、捕捉した例外の報告は`presentation.FailureReporter`が行う。
    コントローラー・ユースケース等の途中の層でcatchしてよいのは、検査例外を包む・
    利用者が直せる誤りへ置き換える・フォールバックする場合だけ。包むときは`cause`を渡し、tryの範囲は置き換えたい呼び出しだけに絞る
    （`catch (Exception e)`で広く包むと、別の失敗まで同じ文言になり原因も表示から消える）。catchしてログを出してから再スローしない。
- 入力（設定ファイル・CLI引数・DB接続情報・サイドカーYAML）の検証は [overview.md の「入力の検証」](./docs/architecture/overview.md#入力の検証) に従い、
  仕様はREADMEの各節に記載する。設定項目・引数を追加・変更した場合は、READMEの仕様の表と、検証する場所
  （`ExportTableDefinitionProperties`・`CliArguments`等）を同じ変更で更新する。
  - 未指定（キーの省略・空）は既定値。未知のキー・引数や解釈できない値は、既定値へ黙って置き換えずに失敗にする。
  - サイドカーYAMLの個々の記述の誤りは、読み飛ばして警告する（WARNログはコンソールにも出る）。
- `tableDefinitionMapper.xml` やドメイン層（エンティティ・テンプレート・ER図生成ロジック等）を変更した後は、
  `./gradlew integrationTest`（Docker上のPostgreSQLに対する結合テスト。`cli/src/integrationTest`）を実行すること。
  mapperのSQLを変えた場合は`PostgresTableDefinitionRepositoryIT`に、変えた取得結果を確かめるテストを足す。
  出力がベースライン（`docs/sample/postgres/output`）と変わる場合は、`verify` スキル（`.claude/skills/verify/SKILL.md`）に従って
  出力結果を確認し、意図した変更であればベースラインを出力し直す。
  Oracle用mapperを変えた場合や、ドメイン層の変更でOracleの出力が変わりうる場合は、`./gradlew oracleIntegrationTest`
  （Docker上のOracle Database Freeに対する結合テスト。メモリを2GB程度使う）も実行し、変えた取得結果を確かめるテストを
  `OracleTableDefinitionRepositoryIT`に足す。ベースラインは`docs/sample/oracle/output`（出力し直す手順は`verify`スキルのOracleの節）。
- スナップショットの形式（`domain.model.snapshot`のrecord）を変えた場合は、MCPサーバーの`./gradlew :mcp-server:test`も実行する
  （`SampleSnapshotContractTest`がベースラインを読む）。互換性の無い変更なら`DatabaseSnapshot.FORMAT_VERSION`を上げ、
  `SnapshotDirectoryReader.SUPPORTED_FORMAT_VERSION`を追従させる。
- 変更が[CONTRIBUTING.mdの「互換性を約束する範囲」](./CONTRIBUTING.md#互換性を約束する範囲)を壊す場合は、PRに`breaking`ラベルを付け、
  PRの本文に移行の手順を書く。`build.gradle`の`version`は、リリースのときにだけ上げる（手順はCONTRIBUTING.mdの「リリースの手順」）。
- MCPサーバーは標準出力をMCPのプロトコルに使う。`System.out`への出力や、標準出力へ出すログの設定を追加しない（表示は標準エラーへ）。
- 新規ロジックを書く前・既存クラスに数行足す前に、以下のような「小さな責務の混在」が
  再発していないか確認する（過去に実際に見つかった逸脱パターン）。
  - 同じ値をループのたびに再構築していないか。`TableNamePatterns.of(...)`のような値オブジェクトを
    ストリームの`.filter()`内やループ内で毎回組み立てていたら、呼び出し前に1回だけ組み立てて
    使い回す値オブジェクトへ切り出す（例: `domain.model.target.TableScope`）。
  - 同じ引数群が3層以上（エントリーポイント→コントローラー→ユースケース等）をそのまま
    分解・再構築されながら渡っていないか。渡す先で使われない引数が混ざっていないかも見る。
    該当する場合はrequestレコードにまとめる（例: `application.ExportTableDefinitionRequest`/`CheckDocumentDiffRequest`）。
  - 設定値（プロパティの文字列）を生のまま深い層へ渡していないか。空白の除去・型への変換・検証は入口で1回だけ行い、
    設定誤りはDBへの問い合わせや出力先の削除より前に検知する（例: `ExportTableDefinitionProperties`・`application.TargetSelection#of`）。
  - インフラ層（Repository実装）が、デフォルト値の決定・名前の自動生成・識別子の解析といった
    業務ルールを直接持っていないか。インフラは読み込みと型変換に専念し、ルールはドメイン層
    （エンティティ・値オブジェクト・enum）のメソッドに持たせる
    （例: `ForeignKeyEntity#resolveLogicalRelationName`、`Cardinality#DEFAULT_FOR_LOGICAL_RELATION`）。
  - エンティティ（DBメタ情報のrecord）が、スキーマ/テーブルの絞り込みリストのような実行時設定を
    メソッド引数として直接受け取っていないか。絞り込みは呼び出し側で値オブジェクトとして
    1回組み立て、エンティティへは`matches(entity)`のように問い合わせる。
  - エンティティが、SQLの都合の形（区切り文字で連結した文字列・NULLの代わりの空白等）で値を持っていないか。
    値の形はDTOからの変換時にドメインの表現（`List`・空文字・enum）へ揃え、表示用の連結・空欄の描画はテンプレートで行う。
    出力ファイル名のような配置の規則も、エンティティやSQLに持たせず`DocumentLocations`で決める。
  - 判定を行うドメインサービス（突き合わせ・検証等）が、利用者に知らせるべき判定結果（警告等）をログへ直接
    出力していないか。判定結果は値として返し（例: `domain.model.target.ConsistencyNotice`）、どこへどう出力するかは
    呼び出し側が決める（書き込みの進捗を示すデバッグログは対象外）。
  - 実行のたびに変わる値（日付等）をDBから取得していないか。DIで受け取る`java.time.Clock`から求める
    （テストで固定できるようにするため。引数なしの`now()`は`ArchitectureTest`が検出する）。
  - Writer（書き込み担当）クラスがMarkdown文字列を自前で組み立てていないか。行・セクションの
    組み立ては必ず`domain.service.writer`配下のテンプレートクラス（`*Templates`。種別ごとのサブパッケージにWriterと同居する）に委ね、Writerは
    「何を・どの順で・どのファイルに書くか」の段取りに専念する。

## セキュリティ

DBのメタ情報（DB名・スキーマ名・テーブル名・コメント・関数やビューの定義等）は、ツールを実行する人とは別の人
（DBでCREATE・COMMENTの権限を持つ人）が書ける、信頼できない入力として扱う。サイドカーYAMLは利用者自身が書くファイルのため、
説明文（`description`）はMarkdownとしてそのまま出力してよい。出力・ログ・ファイル操作に触れる変更では、次を確認する。

- **パス**：DB由来の名前をファイル名・ディレクトリ名に使うときは、`*Locations`（`domain.service.path`）の規則に
  `PathSegments.encode`を通して組み立て、絶対パスは`OutputPathResolver`の実装（`within`で出力先の外を指さないことを確かめる）で解決する。
  `Path.resolve`等をそれ以外の場所で呼ぶと`ArchitectureTest`が失敗する。`OutputPathResolver`にメソッドを足すと、
  `OutputPathContainmentTest`が危険な名前（`..`・絶対パス・`\`・`C:`等）で自動的に検査する
  （引数の型が新しい場合は、テストの`ARGUMENTS`に危険な値を足す）。
- **Markdown・Mermaid**：DB由来の文字列は、表の行なら`MarkdownTemplateSupport.row`（書式で組み立てた行は`escapeTableRow`）、
  見出し・リストの項目なら`escapeInline`、コードブロックは`codeFence`、コードスパンは`codeSpan`を通し、Mermaidの図は
  `MermaidSupport`のメソッドで組み立てる（生のHTMLやコードブロックを閉じる行が出力されないようにするため）。
  DB由来の文字列を受け取るテンプレートを足したら、`MarkdownInjectionTest`の`renderedDocuments`にも足す。
- **秘密情報**：パスワードは環境変数（または`--db-password`）でだけ受け取り、設定ファイルには書かせない（`url`への埋め込みも誤りにする）。
  例外のメッセージ・ログ・画面の表示に、CLI引数・設定値・接続URL等の**値**をそのまま含めない（書き誤りの引数にパスワードが入りうる。
  誤りの報告は`FailureReporter`がログファイルにも残す）。
- **削除**：ディレクトリの削除は`--rm-dist`の出力先だけで行い、削除してよいかは`OutputPathResolver.isRemovableOutputDir`で判定する。
  削除の対象を広げる変更をしない。
- **依存ライブラリ**：更新はDependabot（`.github/dependabot.yml`）が提案する。依存を足す・上げるときは、既知の脆弱性
  （GitHub Advisory Database等）が無い版を選ぶ。YAMLは`SafeConstructor`で読み、JSONのデシリアライズで型情報からクラスを生成する設定
  （デフォルトタイピング）を使わない。mapperのSQLは`#{}`でバインドし、`${}`で文字列を埋め込まない。
- 上記に触れる変更では、PRを作る前に`/security-review`を実行し、指摘を確認する。

## コメントスタイル

コードを書いた・変更した直後に、書いたコメント1つずつについて「これを消したら読み手は何を失うか」を自問する。
失うものが無ければ書かない・消す。書いてよいのは、コードを読んでも復元できない情報だけ。

- 書いてよい例
  - なぜこの値・この順序・このAPIなのか（判断の理由。処理内容の言い換えではない）
  - 変更すると何が壊れるか（前提条件・不変条件）
  - 外部ライブラリ・DBMS・処理系の非自明な仕様、直感に反する挙動
  - ファイル名・パスの命名規則の実例（`OutputPathResolver`/`DocumentLocations`のJavadocにある
    「例: `{base}/{DB名}/...`」等、命名規則そのものはコードから読み取れないため残す）
- 書かない。書きたくなったら該当箇所に置く
  - コードの実況（WHAT） → 名前で示す。コメントで繰り返さない
  - Javadocの`@param`/`@return`が型・メソッド名の言い換えになっているもの → 型・シグネチャから
    わからないこと（単位・境界値・nullや未指定時の扱い等）だけに削るか、無ければ`@param`/`@return`ごと書かない
    （この「言い換えを削る」対象は`@param`/`@return`単位の記述であり、クラス・インタフェース・レコード
    自体の先頭1行は対象外。型の先頭サマリは、名前とほぼ同義でも省略しない。同じ関心を持つ型が並ぶ場所
    （例: `domain.service.writer`配下のWriterクラス、`domain.repository`配下のRepositoryインタフェース）で、
    ある型にだけサマリが無いという状態を作らない）
  - `@since`/`@version`/`@author`タグ → 付けない（単著かつ外部公開APIではないため情報価値が無い。Git履歴で代替する）
  - 作業経緯・変更履歴・issue/PR番号 → コミットメッセージ・PR
  - 当たり前のこと、やらないことの無限列挙 → 削除（意図的に外した設計判断はコミットメッセージに書く）
  - 呼び出し元・呼び出し先や他クラスの実装の説明 → 削除（そのクラス・メソッド自身の責務ではない）
  - 「なぜこの実装が良いか」という設計の弁護 → コミットメッセージ
  - コメントアウトしたコード → 削除（必要ならGit履歴から戻す）
- Javadocを書く対象は宣言の種類で決める（`cli/src/main`・`mcp-server/src/main`。上の「書かない」はどの種類にも当てはまる）。
  可視性は外側の型で絞った後のもので判断する（privateなネストクラスのメソッドはprivate扱い）。

  | 対象 | 扱い | 書く内容 |
  |---|---|---|
  | 型（ネスト・privateを含む全型）、`package-info.java` | 必須 | 1行の要旨 |
  | インタフェース・抽象メソッド | 必須 | 契約（境界値、null・空の扱い、順序、投げる例外） |
  | 具象のpublic・protected・パッケージプライベートメソッド | 必須 | 要旨。`@param`/`@return`は型・名前から分からない情報があるものだけ |
  | `@Override`メソッド | 必須 | `/** {@inheritDoc} */`（契約を持つ親を辿れるようにする）。実装固有の補足があれば続けて書く |
  | コンストラクタ | 書かない | 検証・正規化・複製をするコンパクトコンストラクタと、引数に名前から分からない制約がある場合（例外クラス等）だけ書く。「コンストラクタ」とだけ書いた要旨は書かない |
  | privateメソッド、フィールド・定数、enum定数 | 情報があれば | 判断の理由・前提条件・値の由来や単位等、名前から分からないことがある場合だけ |
  | recordのコンポーネント（型のJavadocの`@param`） | 情報があれば | 意味・未指定時の扱い・単位等、名前から分からないものだけ。全コンポーネントを並べなくてよい |

  テストメソッドにはJavadocを書かず、`@DisplayName`で意図を書く。テストクラスには1行の要旨を書く。
  Javadocの書式の誤り（タグの誤り・参照先の無いリンク等）は`./gradlew javadoc`（doclint）で検知できる。
- コメントを書く前に、名前を直す・値に定数名を付ける・長い説明が要る塊を関数やクラスに分ける、
  のいずれかで説明自体を不要にできないか検討する。
- 残す場合は1文を短く区切り結論を先に書く。1コメント1話題とし、日本語の文は句点で終える。
