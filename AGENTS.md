# AGENTS.md

このリポジトリで作業するAIエージェント向けのガイド。

## このリポジトリについて

DBに接続し、Markdown形式のテーブル定義書・ER図を出力するJava(21)製CLIツール。
機能仕様（出力されるドキュメントの種類、ER図の分割・多重度判定ルール、対応DBMS等）は
[README.md](./README.md) に詳しい。実装に手を入れる前に該当箇所を確認すること。

## アーキテクチャドキュメント

Javaのパッケージ構成・レイヤー構成・DI・実行フローは以下にまとめてある。
コードを読み進める前に目を通すとコンテキスト消費を抑えられる。

- [docs/architecture/overview.md](./docs/architecture/overview.md) — レイヤー構成、実行フロー、DB切り替え、
  メモリ効率のための分割取得、サイドカーYAML等、アーキテクチャ全体の設計意図
- [docs/architecture/package-structure.md](./docs/architecture/package-structure.md) — 全パッケージ・主要クラスの
  役割一覧（リファレンス）

## 作業時の注意

- レイヤーの依存方向は `presentation → application → domain ← infrastructure` を厳守する。
  `domain` パッケージから `infrastructure`／`presentation` への依存を追加しない。
- DB種別（Oracle/PostgreSQL）固有のSQLは
  `src/main/resources/mapper/{oracle,postgresql}/tableDefinitionMapper.xml` に分離されている。
  両DBで挙動を揃える変更は両方のmapperを確認・修正すること。
- 新しいリポジトリ実装やドメインサービスを追加した場合は、
  `config/module/ExportTableDefinitionModule.java` にGuiceの束縛を追加する。
- ファイルI/O（読み書き・一覧取得・一時ディレクトリ作成／削除等）は必ず`domain.repository.FileRepository`経由で行い、
  `application`/`domain`層で`java.nio.file.Files`を直接呼ばない。出力先パスの組み立てやデフォルト値解決
  （未指定時のフォールバック等）は必ず`domain.service.path.OutputPathResolver`経由で行い、`application`層で
  パス文字列を直接組み立てない。Markdownのファイル名やドキュメント間の相対リンクも、テンプレート・Writerで
  `String.format`等により直接組み立てず、`domain.service.path.DocumentLocations`の規則を使う。既存の抽象化を素通りする実装が増えるとテストが実ディスクI/Oに依存し始め、
  レイヤーの意図も崩れるため、新規ロジックを追加する前にまずこの2つのIFで足りないか確認すること。
- `tableDefinitionMapper.xml` やドメイン層（エンティティ・テンプレート・ER図生成ロジック等）を変更した後は、
  `verify` スキル（`.claude/skills/verify/SKILL.md`）に従って実際に出力結果を確認すること。
- 新規ロジックを書く前・既存クラスに数行足す前に、以下のような「小さな責務の混在」が
  再発していないか確認する（過去に実際に見つかった逸脱パターン）。
  - 同じ値をループのたびに再構築していないか。`TableTargetFilter.of(...)`のような値オブジェクトを
    ストリームの`.filter()`内やループ内で毎回組み立てていたら、呼び出し前に1回だけ組み立てて
    使い回す値オブジェクトへ切り出す（例: `domain.model.value.TableTargetScope`）。
  - 同じ引数群が3層以上（エントリーポイント→コントローラー→ユースケース等）をそのまま
    分解・再構築されながら渡っていないか。渡す先で使われない引数が混ざっていないかも見る。
    該当する場合はrequestレコードにまとめる（例: `application.ExportRequest`/`CheckDiffRequest`）。
  - インフラ層（Repository実装）が、デフォルト値の決定・名前の自動生成・識別子の解析といった
    業務ルールを直接持っていないか。インフラは読み込みと型変換に専念し、ルールはドメイン層
    （エンティティ・値オブジェクト・enum）のメソッドに持たせる
    （例: `ForeignKeyEntity#resolveLogicalRelationName`、`Cardinality#DEFAULT_FOR_LOGICAL_RELATION`）。
  - エンティティ（DBメタ情報のrecord）が、スキーマ/テーブルの絞り込みリストのような実行時設定を
    メソッド引数として直接受け取っていないか。絞り込みは呼び出し側で値オブジェクトとして
    1回組み立て、エンティティへは`matches(entity)`のように問い合わせる。
  - Writer（書き込み担当）クラスがMarkdown文字列を自前で組み立てていないか。行・セクションの
    組み立ては必ず`domain.service.writer.template`配下のテンプレートクラスに委ね、Writerは
    「何を・どの順で・どのファイルに書くか」の段取りに専念する。
