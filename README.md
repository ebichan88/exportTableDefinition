# exportTableDefinition

## Overview

DBからMarkdown形式のテーブル定義書を作成するリポジトリ

## Description

対象のDBに接続し、テーブル一覧と各TBLのテーブル定義をMarkdown形式で出力します。

PostgreSQLの場合は、テーブル定義に加えて以下のオブジェクトも出力します。

| 対象 | 取得元カタログ | 出力 |
|---|---|---|
| トリガー | `pg_trigger` + `pg_get_triggerdef` | 各テーブル定義書内の「トリガー情報」セクション + `triggerList_{DB名}.md` |
| 関数・プロシージャ | `pg_proc` + `pg_get_functiondef`（plpgsql/sql/C 等） | `functionList_{DB名}.md` + `{DB名}/{スキーマ名}/function/{関数名}.md` |
| シーケンス | `pg_sequences`（増分・最小値・最大値・キャッシュ・開始値・循環・所有カラム） | `sequenceList_{DB名}.md` + `{DB名}/{スキーマ名}/sequence/{シーケンス名}.md` |
| ユーザー定義型（ENUM等） | `pg_type` + `pg_enum` | `typeList_{DB名}.md` + `{DB名}/{スキーマ名}/type/{型名}.md` |

各一覧（`functionList`／`sequenceList`／`typeList`／`triggerList`）への導線は、`tableList_{DB名}.md` の
「関連ドキュメント」セクションに集約しています（対象が存在するカテゴリのみリンクを表示します）。

なお、これらの追加オブジェクトの出力はPostgreSQL専用です。Oracle接続時は出力されません。
また、関数・プロシージャ・シーケンス・ユーザー定義型はスキーマ単位のオブジェクトのため、
`table`（出力対象テーブル）による絞り込みの対象外です（`schema`による絞り込みのみ適用されます）。

### sample

[テーブル一覧](./sample/tableList_testdb.md)

### 対象DBMS
* PostgreSQL
* Oracle(一部制限あり)
    * Oracleの場合は、以下の項目の出力が不可
        * デフォルト値
        * view／materialized_viewのソース
        * Check制約の定義
        * トリガー／関数・プロシージャ／シーケンス／ユーザー定義型（ENUM等）

### 主なディレクトリ構成

```
exportTableDefinition
│  build
│  └─libs
│      ├─conf  ・・・ 設定ファイルが格納されているフォルダ
│      │  ├─ExportTableDefinition.properties
│      │  └─mybatis.properties
│      ├─output
│      └─exportTableDefinition-1.0-SNAPSHOT.jar ・・・ 実行可能形式Jarファイル
├─docs           ・・・ JavaDoc等のドキュメントが格納されているフォルダ
│  └─javadoc
├─gradle
│  └─wrapper
└─src
    └─ main     ・・・ javaソースコードが格納されているフォルダ
        └─ java
             └─ com
                 └─ export_table_definition
```

## Releasesからダウンロードして使う（ビルド不要・Windows向け）

開発環境を用意しなくても、[Releases](../../releases/tag/latest)から実行可能な形式一式をダウンロードしてすぐに使えます。mainブランチが更新される度に`latest`リリースの中身が自動的に最新化されます。

### 入手方法

1. [Releases](../../releases/tag/latest)から`exportTableDefinition-windows.zip`をダウンロードする
2. 好きな場所に展開する

展開すると以下の構成になっています。

```
exportTableDefinition-windows
│  run.bat                                     ・・・ ダブルクリックで実行する起動ファイル
│  exportTableDefinition-1.0-SNAPSHOT.jar      ・・・ 実行可能形式Jarファイル
├─runtime                                       ・・・ 同梱のJava実行環境（別途Javaのインストール不要）
└─conf
   ├─ExportTableDefinition.properties
   └─mybatis.properties.template
```

Java実行環境（runtimeフォルダ）を同梱しているため、PCにJavaをインストールしていなくてもそのまま実行できます。

### 設定

1. `conf\mybatis.properties.template`を`conf\mybatis.properties`にリネームし、接続先DBの情報を記載する（[mybatis.propertiesの記載内容](#mybatisproperties-の記載内容)を参照）
2. 必要に応じて`conf\ExportTableDefinition.properties`を編集する（[ExportTableDefinition.propertiesの記載内容](#exporttabledefinitionproperties-の記載内容)を参照。未編集でも全スキーマ・全テーブルが`.\output`配下に出力される）

### 実行

`run.bat`をダブルクリックする。コンソール画面が開いて処理が進み、完了すると`conf\ExportTableDefinition.properties`の`outputPath`（未指定の場合は実行フォルダ直下の`output`フォルダ）にMarkdown形式のテーブル定義書が出力される。

※Windows専用です。Windows以外の環境ではビルドして[Usage](#usage)の手順でjarファイルを直接実行してください。

## Usage

### build

以下のコマンドを実行することで、`exportTableDefinition/build/libs`フォルダ配下に`exportTableDefinition-1.0-SNAPSHOT.jar`が作成される

```
gradlew build
```

### Javadoc

以下のコマンドを実行することで、`exportTableDefinition/docs/javadoc`フォルダ配下にjavadocが作成される

```
gradlew javadoc
```

### 実行方法

`conf/ExportTableDefinition.properties`（※）及び`conf/mybatis.properties`に必要な設定値を記載した状態で以下のコマンドを実行する

```
java -jar .\exportTableDefinition-1.0-SNAPSHOT.jar
```

※`conf/mybatis.properties.template`を`conf/mybatis.properties`にリネームしてください

### DB接続情報のCLI引数・環境変数による上書き

`conf/mybatis.properties`を配置せず（あるいは一部項目のみ）、CLI引数や環境変数からDB接続情報を渡すこともできます。CI等、接続情報をファイルに残したくない場合に利用してください。

優先順位は `CLI引数 > 環境変数 > conf/mybatis.propertiesの値` です。

| 項目 | CLI引数 | 環境変数 |
|---|---|---|
| driver | `--db-driver=値` | `DB_DRIVER` |
| url | `--db-url=値` | `DB_URL` |
| username | `--db-username=値` | `DB_USERNAME` |
| password | `--db-password=値` | `DB_PASSWORD` |

```
java -jar exportTableDefinition-1.0-SNAPSHOT.jar --db-url=jdbc:postgresql://localhost:5432/testdb --db-username=user --db-password=pass
```

CLI引数・環境変数で `driver`/`url`/`username`/`password` の4項目すべてを指定する場合、`conf/mybatis.properties`自体が存在しなくても起動できます。

### ExportTableDefinition.properties の記載内容

```
schema={テーブル定義出力対象のスキーマ名（複数存在する場合はカンマ区切り）} ※空白の場合はinformation_schema／pg_catalogを除く全スキーマを対象
table={テーブル定義出力対象のテーブル名（複数存在する場合はカンマ区切り）} ※空白の場合は全テーブルを対象
outputPath={テーブル定義出力先のディレクトリパス}　※空白の場合は./outputにテーブル定義が出力されます
chunkSize={詳細情報をまとめて取得・出力するテーブル数の上限} ※空白の場合は3000。0以下を指定するとスキーマ単位で分割しない
```

`chunkSize`は、カラム・インデックス・制約・外部キーといった詳細情報を、スキーマ内でさらに指定件数ごとに分割して取得・出力・破棄するための設定です。テーブル数が非常に多い（特に1スキーマに集中している）場合に、同時にメモリ保持する情報量を抑えてメモリ使用量を安定させます。値を小さくするほどピークメモリは減りますが、DBへの問い合わせ回数は増えます。出力される定義書の内容は`chunkSize`の値によって変わりません。

### mybatis.properties の記載内容

```
driver=ドライバーの名称
url=データベース接続先のURL
username=ユーザ名
password=パスワード
```
