# exportTableDefinition

## Overview

DBからMarkdown形式のテーブル定義書を作成するリポジトリ

## Description

対象のDBに接続し、テーブル一覧と各TBLのテーブル定義をMarkdown形式で出力します。

### ER図

テーブル間の外部キー関係をMermaid記法のER図として出力します。テーブル単位とスキーマ単位の2つの粒度で出力し、
スキーマ単位のものには索引が付きます。

| 出力先 | 内容 |
|---|---|
| 各テーブル定義書内の「ER図」セクション | 自テーブルと、直接の参照先・参照元テーブルのみを描画（自テーブルのみカラム・PK付き） |
| `erDiagram_{DB名}_{スキーマ名}.md` | スキーマ単位の全体ER図。属性なしの箱と外部キーの関連線のみ |
| `erDiagram_{DB名}_{スキーマ名}_group{連番}.md` | スキーマが大きい場合に、テーブルのまとまりごとに分割したER図 |
| `erDiagramList_{DB名}.md` | 全体ER図の索引。スキーマ別ER図へのリンクと、スキーマ跨ぎの外部キー一覧 |

ER図のページに掲載する表が3000行を超える場合は、テーブル一覧と同様に別ファイルへ
分割し、前へ/次へのページ送りリンクを付けて出力します。件数が多くても情報が欠落することはありません。

全体ER図を1枚にまとめるとMermaidが描画できる規模を超えるため、スキーマ単位に分割して出力します。
スキーマ跨ぎの外部キーは参照元・参照先の双方の図に描画されるため、他スキーマのテーブルも箱として登場します。

図が読めなくなるのを避けるため、以下の制御を行っています。

* 外部キーによる関連を持たないテーブルは図に描画せず、各ページの「ER図に掲載しているテーブル」セクションに掲載します
* 1枚あたりのテーブル数が`erDiagramMaxNodes`（[後述](#exporttabledefinitionproperties-の記載内容)）を超える場合は、**外部キーで繋がったテーブルのまとまり（連結成分）ごとにグループへ分割**して出力します。この場合、スキーマのページはグループ一覧（規模と主なテーブルの一覧）になります
* グループに分けてもなお1枚に収まらない巨大なまとまり（大半のテーブルが1つに繋がっている場合など）は、そのグループのみER図の描画を省略し、代わりに外部キーの一覧表を出力します

1テーブルだけのグループが大量にできるのを避けるため、独立した小さなまとまりは上限に収まる範囲で同じ図にまとめます。
まとめられたテーブル同士は線で繋がっていないため、関連を誤読するおそれはありません。

図中のノードから各テーブル定義書への導線は、各ページの「テーブル一覧」セクションのリンクで辿れます
（Mermaidの`click`構文はGitHub上では無効化されるため、リンクは表側に持たせています）。

`erDiagramList_{DB名}.md`への導線は、`tableList_{DB名}.md`の「関連ドキュメント」セクションに配置しています。

ER図の出力はPostgreSQL／Oracleの双方に対応しています。

#### 多重度の判定

関連線の多重度は、参照元（子）テーブルの外部キー列に付与された制約から機械的に判定します。
外部キーそのものは「関連があること」しか示さないため、以下の2点を併せて見ています。

| 外部キー列が一意 | 外部キー列がすべてNOT NULL | 多重度 | 表記 |
|---|---|---|---|
| × | ○ | 1対多 | `A \|\|--o{ B` |
| × | × | 0..1対多 | `A \|o--o{ B` |
| ○ | ○ | 1対1 | `A \|\|--o\| B` |
| ○ | × | 0..1対1 | `A \|o--o\| B` |

判定結果は各テーブル定義書の「外部キー情報」セクションにも「多重度」列として掲載します。

* 一意性は列名の一致ではなく**包含関係**で判定します。`FK(a, b)` に対して `UNIQUE(a)` がある場合は
  外部キー全体も一意となるため1対1です。逆に `UNIQUE(a, b)` があっても外部キーが `(a)` のみなら1対1ではありません
* 一意制約だけでなく`CREATE UNIQUE INDEX`で作成した一意索引も判定対象に含めます
* PostgreSQLの部分インデックス（`WHERE`付き）は条件付きの一意性しか保証しないため、判定対象から除外します
* 外部キー列にNULLを許容する場合、参照が成立しない行が存在しうるため親側は「0または1」となります
* 「親1件につき子が1件以上存在すること」はテーブル定義では表現できないため、子側が「1以上」となることはありません

### PostgreSQL固有の出力対象

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

これらの追加オブジェクトは、`outputObjects`（[後述](#exporttabledefinitionproperties-の記載内容)）でオブジェクト種別ごとに
出力有無を絞り込めます。トリガーを対象外にした場合は、`triggerList_{DB名}.md`だけでなく各テーブル定義書内の
「トリガー情報」セクションも出力されなくなります。

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
erDiagramMaxNodes={スキーマ別ER図1枚に描画するテーブル数の上限} ※空白の場合は80。0以下を指定すると上限なし
outputObjects={出力対象とするPostgreSQL固有オブジェクト種別（複数存在する場合はカンマ区切り）} ※空白の場合は全種別を対象
```

`outputObjects`は、PostgreSQL固有の追加オブジェクト（トリガー・関数/プロシージャ・シーケンス・ユーザー定義型）のうち、
出力したい種別だけを指定するための設定です。指定できる値は以下のとおりで、`table`（テーブル定義書・ER図）は
常に出力されるため指定対象に含まれません。

| 値 | 出力対象 |
|---|---|
| `trigger` | トリガー（各テーブル定義書内の「トリガー情報」セクション + `triggerList_{DB名}.md`） |
| `function` | 関数・プロシージャ（`functionList_{DB名}.md` + 個別ファイル） |
| `sequence` | シーケンス（`sequenceList_{DB名}.md` + 個別ファイル） |
| `type` | ユーザー定義型（`typeList_{DB名}.md` + 個別ファイル） |

例えば、テーブル定義とER図のみが必要で関数・プロシージャは不要な場合は、`outputObjects=trigger,sequence,type`のように
不要な種別（この例では`function`）を除いて指定してください。空白のまま（未指定）の場合は、従来どおり全種別が出力されます。
Oracle接続時はこれらの追加オブジェクトがそもそも出力されないため、`outputObjects`の指定は無視されます。

`chunkSize`は、カラム・インデックス・制約・外部キーといった詳細情報を、スキーマ内でさらに指定件数ごとに分割して取得・出力・破棄するための設定です。テーブル数が非常に多い（特に1スキーマに集中している）場合に、同時にメモリ保持する情報量を抑えてメモリ使用量を安定させます。値を小さくするほどピークメモリは減りますが、DBへの問い合わせ回数は増えます。出力される定義書の内容は`chunkSize`の値によって変わりません。

`erDiagramMaxNodes`は、ER図1枚に描画するテーブル数の上限です。Mermaidはノード数が増えると描画に時間がかかり、ブラウザやGitHub上でレンダリングされなくなるため、この値を超えるスキーマでは外部キーで繋がったテーブルのまとまりごとにグループへ分割して出力します（分割してもなお収まらないまとまりは、そのグループのみ外部キーの一覧表になります）。読みやすい図が得られる値はDBの構造によって異なるため、出力結果を見ながら調整してください。なお上限の判定対象は「外部キーによる関連を持つテーブル数」であり、関連を持たないテーブルは図に描画されないためカウントされません。0以下を指定すると上限なしとなり、グループ分割も行いません。

### mybatis.properties の記載内容

```
driver=ドライバーの名称
url=データベース接続先のURL
username=ユーザ名
password=パスワード
```
