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
- `tableDefinitionMapper.xml` やドメイン層（エンティティ・テンプレート・ER図生成ロジック等）を変更した後は、
  `verify` スキル（`.claude/skills/verify/SKILL.md`）に従って実際に出力結果を確認すること。
