# AGENTS.md

このリポジトリで作業するAIエージェント向けのガイド。

## このリポジトリについて

DBに接続し、Markdown形式のテーブル定義書・ER図を出力するJava(21)製CLIツール。
同じ取得結果から機械可読なスキーマのスナップショット（JSON Lines）も出力し、`--check`モードではDBとの差分を検知する。
機能仕様（出力されるドキュメントの種類、ER図の分割・多重度判定ルール、対応DBMS等）は
[README.md](./README.md) に詳しい。実装に手を入れる前に該当箇所を確認すること。

## アーキテクチャドキュメント

Javaのパッケージ構成・レイヤー構成・DI・実行フロー・ドメインモデルは以下にまとめてある。
コードを読み進める前に目を通すとコンテキスト消費を抑えられる。

- [docs/architecture/overview.md](./docs/architecture/overview.md) — レイヤー構成、実行フロー、DB切り替え、
  メモリ効率のための分割取得、サイドカーYAML等、アーキテクチャ全体の設計意図
- [docs/architecture/domain-model.md](./docs/architecture/domain-model.md) — ドメインの概念（テーブル・関連・
  サイドカー・出力対象等）同士の関係図、主なルールを持つ場所、用語集（README・コード・会話で使う呼び方の対応）
- [docs/architecture/package-structure.md](./docs/architecture/package-structure.md) — 全パッケージ・主要クラスの
  役割一覧（リファレンス）

## 作業時の注意

- レイヤーの依存方向は `presentation → application → domain ← infrastructure` を厳守する。
  `domain` パッケージから `infrastructure`／`presentation` への依存を追加しない。
  `domain.model` 配下の概念ごとのパッケージ（`table`・`relation`・`sidecar`・`target` 等）の間も、
  [domain-model.md](./docs/architecture/domain-model.md) の依存の向きに従い循環させない。
- ドメインの概念（`domain.model` のクラス）を追加・改名・削除した場合や、ルールを持つ場所を移した場合は、
  `docs/architecture/domain-model.md` の図・ルール表・用語集も同じ変更で更新する。
  新しいクラス・メソッドの名前は用語集の用語（READMEで使っている呼び方）に揃え、同じものに別の名前を付けない。
- DB種別（Oracle/PostgreSQL）固有のSQLは
  `src/main/resources/mapper/{oracle,postgresql}/tableDefinitionMapper.xml` に分離されている。
  両DBで挙動を揃える変更は両方のmapperを確認・修正すること。
- 新しいリポジトリ実装やドメインサービスを追加した場合は、
  `config/module/ExportTableDefinitionModule.java` にGuiceの束縛を追加する。
  コンストラクタには`com.google.inject.Inject`ではなく`jakarta.inject.Inject`を付ける（ドメイン層をGuiceに依存させない）。
  束縛漏れは`ExportTableDefinitionModuleTest`（実際にDIコンテナを組み立てるテスト）で検知できる。
- ファイルI/O（読み書き・一覧取得・一時ディレクトリ作成／削除等）は必ず`domain.repository.FileRepository`経由で行い、
  `application`/`domain`層で`java.nio.file.Files`を直接呼ばない。出力先パスの組み立てやデフォルト値解決
  （未指定時のフォールバック等）は必ず`domain.service.path.OutputPathResolver`経由で行い、`application`層で
  パス文字列を直接組み立てない。Markdownのファイル名やドキュメント間の相対リンクも、テンプレート・Writerで
  `String.format`等により直接組み立てず、`domain.service.path.DocumentLocations`の規則を使う
  （スナップショットの配置は`domain.service.path.SnapshotLocations`）。既存の抽象化を素通りする実装が増えるとテストが実ディスクI/Oに依存し始め、
  レイヤーの意図も崩れるため、新規ロジックを追加する前にまずこの2つのIFで足りないか確認すること。
- 例外の扱いは [overview.md の「例外の扱いと終了コード」](./docs/architecture/overview.md#例外の扱いと終了コード) に従う。
  - 利用者が設定・入力・実行環境を見直せば解消する失敗は`domain.UserCorrectableException`（設定ファイルの誤りは派生の
    `config.InvalidConfigurationException`）で表し、何を直せばよいかをメッセージに書いて投げる。それ以外（I/O・SQL・不具合）は
    非検査例外のまま伝える。ドメイン層に検査例外は使わず、呼び出し側に判断を委ねたい結果は値で返す。
  - 捕捉は`presentation.FailureHandler`の1箇所だけ。コントローラー・ユースケース等の途中の層でcatchしてよいのは、検査例外を包む・
    利用者が直せる誤りへ置き換える・フォールバックする場合だけ。包むときは`cause`を渡し、tryの範囲は置き換えたい呼び出しだけに絞る
    （`catch (Exception e)`で広く包むと、別の失敗まで同じ文言になり原因も表示から消える）。catchしてログを出してから再スローしない。
- 入力（設定ファイル・CLI引数・DB接続情報・サイドカーYAML）の検証は [overview.md の「入力の検証」](./docs/architecture/overview.md#入力の検証) に従い、
  仕様はREADMEの各節に記載する。設定項目・引数を追加・変更した場合は、READMEの仕様の表と、検証する場所
  （`ExportTableDefinitionProperties`・`CliArguments`等）を同じ変更で更新する。
  - 未指定（キーの省略・空）は既定値。未知のキー・引数や解釈できない値は、既定値へ黙って置き換えずに失敗にする。
  - サイドカーYAMLの個々の記述の誤りは、読み飛ばして警告する（WARNログはコンソールにも出る）。
- `tableDefinitionMapper.xml` やドメイン層（エンティティ・テンプレート・ER図生成ロジック等）を変更した後は、
  `verify` スキル（`.claude/skills/verify/SKILL.md`）に従って実際に出力結果を確認すること。
- 新規ロジックを書く前・既存クラスに数行足す前に、以下のような「小さな責務の混在」が
  再発していないか確認する（過去に実際に見つかった逸脱パターン）。
  - 同じ値をループのたびに再構築していないか。`TableTargetFilter.of(...)`のような値オブジェクトを
    ストリームの`.filter()`内やループ内で毎回組み立てていたら、呼び出し前に1回だけ組み立てて
    使い回す値オブジェクトへ切り出す（例: `domain.model.target.TableTargetScope`）。
  - 同じ引数群が3層以上（エントリーポイント→コントローラー→ユースケース等）をそのまま
    分解・再構築されながら渡っていないか。渡す先で使われない引数が混ざっていないかも見る。
    該当する場合はrequestレコードにまとめる（例: `application.ExportRequest`/`CheckDiffRequest`）。
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
    出力していないか。判定結果は値として返し（例: `domain.model.target.ConsistencyFinding`）、どこへどう出力するかは
    呼び出し側が決める（書き込みの進捗を示すデバッグログは対象外）。
  - 実行のたびに変わる値（日付等）をDBから取得したり、`LocalDate.now()`等で直接取得したりしていないか。
    DIで受け取る`java.time.Clock`から求める（テストで固定できるようにするため）。
  - Writer（書き込み担当）クラスがMarkdown文字列を自前で組み立てていないか。行・セクションの
    組み立ては必ず`domain.service.writer.template`配下のテンプレートクラスに委ね、Writerは
    「何を・どの順で・どのファイルに書くか」の段取りに専念する。
