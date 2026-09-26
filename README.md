# exportTableDefinition

## Overview

DBに接続し、テーブル一覧・各テーブルの定義書・ER図などをMarkdown形式で出力するツールです。

出力されるものの全体像は以下のとおりです（各項目の詳細は[出力される内容の詳細](#出力される内容の詳細)を参照）。

| 出力物 | 内容 |
|---|---|
| テーブル一覧（`tableList_{DB名}.md`） | 対象スキーマ・テーブルの一覧と、各テーブル定義書・関連ドキュメントへのリンク |
| 各テーブル定義書 | カラム・インデックス・制約・外部キー情報など（PostgreSQLの場合はトリガー情報も） |
| ER図 | テーブル間の外部キー関係を表すMermaid記法の図（テーブル単位・スキーマ単位の2種類） |
| 観点ごとのページ・観点一覧 | サイドカーYAMLで宣言した業務ドメイン別の観点（「受注管理」等）ごとのER図と所属テーブル。[詳細](#観点viewpoints) |
| PostgreSQL固有オブジェクトの一覧・個別ページ | 関数・プロシージャ、シーケンス、ユーザー定義型（ENUM等） |
| スキーマのスナップショット | 上記と同じ情報を機械可読なJSON Lines形式で構造化したもの。[詳細](#スキーマのスナップショットjson-lines) |

出力サンプル: [テーブル一覧](./docs/sample/postgres/output/tableList_testdb.md)

### 対象DBMS

* PostgreSQL
* Oracle（一部制限あり）
    * Oracleの場合は、以下の項目の出力が不可
        * デフォルト値
        * view／materialized_viewのソース
        * Check制約の定義
        * トリガー／関数・プロシージャ／シーケンス／ユーザー定義型（ENUM等）

## Getting Started（ビルド不要）

開発環境を用意しなくても、[Releases](../../releases/tag/latest)から実行可能な形式一式をダウンロードしてすぐに使えます。mainブランチが更新される度に`latest`リリースの中身が自動的に最新化されます。Windows／Linux／macOSそれぞれ向けのzipを用意しています。

### 入手方法

1. [Releases](../../releases/tag/latest)からOSに合ったzipをダウンロードする
    * Windows: `exportTableDefinition-windows.zip`
    * Linux: `exportTableDefinition-linux.zip`
    * macOS: `exportTableDefinition-macos.zip`
2. 好きな場所に展開する

展開すると以下の構成になっています（Windowsの例。Linux／macOSでは`run.bat`の代わりに`run.sh`が入っています）。

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

1. `conf/mybatis.properties.template`を`conf/mybatis.properties`にリネームし、接続先DBの情報を記載する（[mybatis.propertiesの記載内容](#mybatisproperties-の記載内容)を参照）
2. 必要に応じて`conf/ExportTableDefinition.properties`を編集する（[ExportTableDefinition.propertiesの記載内容](#exporttabledefinitionproperties-の記載内容)を参照。未編集でも全スキーマ・全テーブルが`./output`配下に出力される）

### 実行

* Windows: `run.bat`をダブルクリックする
* Linux／macOS: ターミナルから`./run.sh`を実行する（`chmod +x run.sh`が必要な場合があります）

コンソール画面が開いて処理が進み、完了すると`conf/ExportTableDefinition.properties`の`outputPath`（未指定の場合は実行フォルダ直下の`output`フォルダ）にMarkdown形式のテーブル定義書が出力される。

### 終了コード・失敗時の表示

処理結果はコンソールの`[result]:SUCCESS`／`[result]:FAIL`で確認できます。スクリプトやCIから実行する場合は、終了コードで判定できます。

| 終了コード | 意味 |
|---|---|
| `0` | 成功（`--check`モードでは差分なし） |
| `1` | `--check`モードで差分あり |
| `2`以上 | 失敗（設定の誤り、DBに接続できない、出力先に書き込めない等）。現在は`2`のみを返す |

* 失敗は「`2`以上」と定めています。今後、失敗の種類を分けて`3`以上を追加することがあるため、スクリプトで判定する場合は「`2`以上か」で判定してください（`2`と一致するかで判定すると、追加した値を見落とします）。
* jarが見つからない、Javaのバージョンが古い、JVMのオプションが誤っている等、JVMが起動できない場合は、このツールが処理を始める前にJavaの仕様で終了コード`1`になります（`--check`モードの「差分あり」と同じ値になるため、コンソールの表示で区別してください）。
* 同梱の`run.sh`・`run.bat`自体のエラー（同梱のJava実行環境やjarが見つからない等）は、終了コード`2`で終了します。

`[result]:FAIL`の場合は、`[errmsg]`に失敗の内容が、DBが返したエラー等の原因がある場合は`[cause]`にその内容が表示されます。
設定の誤りやDBに接続できない場合など、設定・入力・実行環境を見直せば解消する失敗は、表示された内容に従って見直してください。
それ以外の想定外の失敗の場合は、実行したフォルダの`var/log/exportTableDefinition.log`にスタックトレースが記録されます。

処理は続けられるものの確認してほしい事柄（実在しないテーブル・カラムに対する付帯情報、サイドカーYAMLの記述の誤り等）は、`[warn]:`で始まる行として標準エラー出力に表示します（ログファイルにも記録されます）。警告があっても終了コードは変わりません。

## 設定ファイル

### ExportTableDefinition.properties の記載内容

```
schema=sample
table=!flyway_schema_history
outputPath=./docs/db
annotationPath=./conf/annotations.yml
```

各項目の値と、未指定の場合・誤りとして扱う値は以下のとおりです。

| キー | 値 | 未指定の場合 | 誤りとして扱う値 |
|---|---|---|---|
| `schema` | 出力対象のスキーマ名（カンマ区切りで複数指定可） | information_schema／pg_catalogを除く全スキーマ | なし（存在しないスキーマは、テーブルが無いものとして扱う） |
| `table` | 出力対象のテーブル名のパターン（カンマ区切りで複数指定可。記法は後述） | 全テーブル | テーブル名の部分が空のもの（`!`のみ、`sample.`等）、スキーマ名の部分が空のもの（`.employee`等） |
| `outputPath` | テーブル定義の出力先ディレクトリのパス（存在しない場合は作成する） | `./output` | 既存のファイル（ディレクトリではないもの）を指すパス（`--check`でも誤りとする）。このほか、書き込めない場合は実行時に失敗する |
| `chunkSize` | 詳細情報をまとめて取得・出力するテーブル数の上限（整数。0以下を指定するとスキーマ単位で分割しない） | `3000` | 整数として解釈できない値 |
| `erDiagramMaxNodes` | スキーマ別ER図1枚に描画するテーブル数の上限（整数。0以下を指定すると上限なし） | `80` | 整数として解釈できない値 |
| `outputObjects` | 出力対象とするPostgreSQL固有オブジェクトの種別（`trigger`・`function`・`sequence`・`type`。カンマ区切りで複数指定可。後述） | 全種別 | 左記以外の値 |
| `annotationPath` | 手動付帯情報・論理リレーション・観点を記述したサイドカーYAMLのパス（後述） | マージしない | 存在しないファイル、YAMLとして読めないファイル |

* すべての項目は省略できます。キーを書かない場合と値を空白にした場合は、同じ「未指定」として扱います。
* すべての項目は、CLI引数で上書きできます（[CLI引数による上書き](#cli引数による上書き)を参照）。
* 上記以外のキー（キー名の書き誤り等）を書いた場合は誤りとして扱います。
* カンマ区切りで複数指定する項目（`schema`・`table`・`outputObjects`）は、各値の前後の空白を無視します（`schema=public, sample`のように記述できます）。
* 誤りがある場合は、見つかった誤りをまとめて表示し、`[result]:FAIL`（終了コード`2`）で終了します。設定ファイルの誤り（`conf`ディレクトリ・設定ファイル自体が見つからない場合を含む）と`outputPath`の誤りはDBへ接続する前に、`annotationPath`のファイルの誤りはDBからの取得・出力先の削除（`--rm-dist`）より前に検知します。

Markdownのドキュメントに加えて、同じ取得結果から構造化したスキーマのスナップショット（JSON Lines）を、常に
`outputPath`配下の`snapshot/`へ出力します。jq等での機械処理や、プルリクエストでのスキーマ変更のレビュー（git diff）、
`--check`モードでの差分検知に利用できます。形式は[スキーマのスナップショット（JSON Lines）](#スキーマのスナップショットjson-lines)を参照してください。

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

`table`には完全一致のテーブル名だけでなく、以下の記法も指定できます（`schema`はスキーマ名の完全一致のみ対応）。

| 記法 | 例 | 意味 |
|---|---|---|
| ワイルドカード（`*`は0文字以上の任意文字列） | `*_bk`, `*_20240101` | パターンに一致するテーブルすべてが対象 |
| 除外（先頭に`!`） | `!flyway_schema_history` | パターンに一致するテーブルを常に対象外にする（包含パターンより優先） |
| スキーマ修飾 | `sample.employee` | 指定したスキーマのテーブルのみを対象にする（スキーマ修飾がない場合は全スキーマが対象） |

例えば`table=!flyway_schema_history,!*_bk,!*_20240101`のように除外パターンのみを指定すると、マイグレーション管理テーブルやバックアップ用の一時テーブルを定義書・ER図から除外できます。除外パターンは包含パターンと併用でき、その場合は除外が常に優先されます。なお`table`による絞り込みはSQLではなくアプリケーション側で行われるため、`schema`のみで絞り込む場合と比べてDBへの問い合わせ量が増える点に留意してください。

`chunkSize`は、カラム・インデックス・制約・外部キーといった詳細情報を、スキーマ内でさらに指定件数ごとに分割して取得・出力・破棄するための設定です。テーブル数が非常に多い（特に1スキーマに集中している）場合に、同時にメモリ保持する情報量を抑えてメモリ使用量を安定させます。値を小さくするほどピークメモリは減りますが、DBへの問い合わせ回数は増えます。出力される定義書の内容は`chunkSize`の値によって変わりません。

`erDiagramMaxNodes`は、ER図1枚に描画するテーブル数の上限です。Mermaidはノード数が増えると描画に時間がかかり、ブラウザやGitHub上でレンダリングされなくなるため、この値を超えるスキーマでは外部キーで繋がったテーブルのまとまりごとにグループへ分割して出力します（分割してもなお収まらないまとまりは、そのグループのみ外部キーの一覧表になります）。読みやすい図が得られる値はDBの構造によって異なるため、出力結果を見ながら調整してください。なお上限の判定対象は「外部キーによる関連を持つテーブル数」であり、関連を持たないテーブルは図に描画されないためカウントされません。0以下を指定すると上限なしとなり、グループ分割も行いません。

`annotationPath`は、DBからは取得できない情報を、テーブル定義書の再生成時にマージするためのサイドカーファイルのパスです。テーブル定義書は毎回全上書きで生成されるため、生成物のMarkdownに直接書き込んだ情報は次回の生成で失われます。サイドカーYAMLに外出ししておくことで、DBの変更に追従して定義書を再生成しても手書き情報が保持されます。空白（未指定）の場合はマージを行いません。

指定したファイルが存在しない場合や、YAMLとして読めない場合（構文の誤り、UTF-8以外の文字コード）は、`[result]:FAIL`（終了コード`2`）で終了します。一方、個々の記述の誤り（キーの形式の誤り、必須項目の欠け、未知の多重度、未知のキー等）は、該当箇所を読み飛ばして警告を表示し、処理を続けます。付帯情報の一部の書き誤りで、定義書全体の再生成が止まらないようにするためです。

サイドカーYAMLには、性質の異なる3種類の情報を記述できます。

| セクション | 内容 | 反映先 |
|---|---|---|
| `tables` | 手動の付帯情報（テーブル説明・テーブル備考・カラム備考） | テーブル定義書の各セル |
| `relations` | 論理リレーション（DBに外部キー制約がないテーブル間の関連） | 「論理リレーション情報」セクション + ER図 |
| `viewpoints` | 観点（業務ドメイン別のテーブルのまとまり） | 観点ごとのページ・観点一覧 + 所属テーブルの定義書の「所属する観点」セクション |

#### `tables`（手動付帯情報）

「スキーマ.テーブル」をキーとした以下の構造で記述します（`docs/sample/postgres/annotations.sample.yml`も参照）。

```yaml
tables:
  public.users:            # スキーマ名.物理テーブル名
    description: |         # 「テーブル説明」セクションへ出力（複数行可）
      ユーザーの基本情報を保持するテーブル。
      認証情報と紐づく。
    remarks: 個人情報を含む  # 「テーブル情報」の備考セルへ出力
    columns:               # 物理カラム名 : カラム備考（「カラム情報」の備考セルへ出力）
      email: ログインID兼用
      status: 0=無効 1=有効
```

補足事項:

* キーは**物理名**（`スキーマ名.物理テーブル名`、カラムは物理カラム名）で指定します。論理名ではありません。
* 備考は改行や`|`を含んでいても、Markdownの表を崩さないよう自動でエスケープして出力します（`|`→`\|`、改行→`<br>`）。
* インデックス・制約の備考はDBコメント（`COMMENT ON ...` / `obj_description`）から取得しているため、このサイドカーの対象外です。
* 実在しないテーブル・カラムに対する付帯情報（リネームや削除により乖離したもの）が見つかった場合は、警告を表示して処理を継続します（テーブル単位の警告は、スキーマ・テーブルの出力対象を絞り込んでいない場合のみ行います）。

#### `relations`（論理リレーション）

アプリケーション側で参照整合性を担保しているなどの理由で外部キー制約を張らないDBでは、カタログから読み取れる関連だけではER図がほとんど空になります。`relations`では、DBに制約が存在しないテーブル間の関連を宣言してER図に補うことができます。

```yaml
relations:
  - table: public.logs           # 参照元（子）。スキーマ名.物理テーブル名
    columns: [user_id]           # 参照元のカラム（単一の場合は user_id のようにスカラーでも可）
    parentTable: public.users    # 参照先（親）
    parentColumns: [id]          # 参照先のカラム
    name: rel_logs_users         # 関連名（省略可）
    cardinality: 0..1対多         # 多重度（省略可）
```

宣言した関連は以下に反映されます。

| 反映先 | 内容 |
|---|---|
| 参照元テーブル定義書の「論理リレーション情報」セクション | 関連名・カラムリスト・参照先・多重度の一覧 |
| 各テーブル定義書のER図／スキーマ別ER図 | 物理外部キーの**実線**に対し、**破線**（Mermaidの非識別関連）で描画 |

補足事項:

* 読み手が「DBに制約がある」と誤読しないよう、**「外部キー情報」セクションとは別のセクション**に掲載し、注意書きを添えます。ER図でも線種で区別します。
* `name`を省略した場合は`{テーブル名}_{カラム名}_lrel`形式（例: `logs_user_id_lrel`）で自動生成します。実在する制約名と紛れないよう、DBの慣例（`_fkey`等）とは異なる接尾辞を用いています。
* `cardinality`はDBに制約が無く機械的に判定できないため、明示指定できます。指定できる値は`1対多` / `0..1対多` / `1対1` / `0..1対1`です。省略時は`1対多`とみなします。未知の値の場合は警告を表示して`1対多`とみなします（[多重度の判定](#多重度の判定)も参照）。
* 論理リレーションは物理外部キーと同じ関連としてER図に合流するため、**スキーマ別ER図のグループ分割（連結成分）やスキーマ跨ぎ関連の一覧にも反映されます**。関連を持たないとして図から除外されていたテーブルも、論理リレーションを宣言すれば描画対象になります。
* 参照元・参照先のいずれかが出力対象のテーブルに存在しない場合（出力対象の絞り込み、リネーム、削除など）は、警告を表示してその関連を除外します。

#### `viewpoints`（観点）

スキーマ別ER図のグループ分割は外部キーの繋がりによる機械的なまとまりで、「受注管理」「在庫管理」のような業務の単位にはなりません。`viewpoints`では、業務ドメイン別にテーブルをまとめた「観点」を宣言し、観点ごとのページ（ER図・所属テーブル）を出力できます（出力内容は[観点（Viewpoints）](#観点viewpoints)を参照）。

```yaml
viewpoints:
  - id: order                # 識別子。観点ページのファイル名に使う（英数字・-・_のみ）
    name: 受注管理             # 表示名（省略時は識別子）
    description: |           # 観点ページの「説明」セクションへ出力（複数行可。観点一覧には1行目のみ）
      受注から出荷指示までを扱うテーブル群。
    tables:                  # 所属テーブル（table= と同じ記法）
      - sales.order*
      - sales.customer
      - "!sales.order_bk"
```

| キー | 値 | 未指定の場合 |
|---|---|---|
| `id` | 識別子。観点ページのファイル名（`viewpoint_{DB名}_{id}.md`）に使うため、英数字・`-`・`_`のみ | 誤り（その観点を読み飛ばす） |
| `name` | 表示名 | 識別子を表示名とする |
| `description` | 説明（複数行可） | 説明を出力しない |
| `tables` | 所属テーブルのパターン（リスト。1件の場合はスカラーでも可）。[`table`](#exporttabledefinitionproperties-の記載内容)と同じ記法（ワイルドカード・除外・スキーマ修飾） | 誤り（その観点を読み飛ばす） |

補足事項:

* 先頭が`!`（除外）や`*`（ワイルドカード）のパターンは、YAMLの記号として解釈されないよう引用符で囲んでください（`"!sales.order_bk"`、`"*_log"`）。
* `tables`には、除外パターン以外（包含パターン）を1件以上指定してください。除外パターンのみの場合は、全テーブルが所属してしまうため誤りとして読み飛ばします。
* 1つのテーブルが複数の観点に所属してもかまいません。
* 識別子が誤っている観点、`tables`の指定が誤っている観点、既出の識別子の観点は、警告を表示して読み飛ばします。
* どのテーブルにも一致しないパターン（リネーム・削除の可能性）は、警告を表示します（スキーマ・テーブルの出力対象を絞り込んでいない場合のみ）。
* 観点はスキーマの情報ではないため、スナップショットには含めません（観点を変更しても`--check`は差分として報告しません）。

### mybatis.properties の記載内容

```
driver=ドライバーの名称
url=データベース接続先のURL
username=ユーザ名
password=パスワード
```

| キー | 値 | 未指定の場合 |
|---|---|---|
| `driver` | JDBCドライバーのクラス名 | 誤り |
| `url` | 接続先のJDBC URL | 誤り |
| `username` | ユーザ名 | 空（DBの認証方式による） |
| `password` | パスワード | 空（DBの認証方式による） |

* 各項目は、後述のCLI引数でも指定できます。`driver`・`url`がいずれの方法でも指定されていない場合は、DBへ接続する前に`[result]:FAIL`（終了コード`2`）で終了します。
* 上記以外のキー（キー名の書き誤り等）を書いた場合は誤りとして扱います。

### コマンドライン引数

| 引数 | 意味 |
|---|---|
| `--check` | DB vs ドキュメントの差分検知モードで実行する（[後述](#db-vs-ドキュメントの差分検知--checkモード)） |
| `--rm-dist` | 書き込み前に出力先ディレクトリを削除する（[後述](#出力先ディレクトリの事前クリーンアップ--rm-distオプション)） |
| `--db-driver=値`・`--db-url=値`・`--db-username=値`・`--db-password=値` | DB接続情報を上書きする（次項） |
| `--schema=値`・`--table=値`・`--output-path=値`・`--chunk-size=値`・`--er-diagram-max-nodes=値`・`--output-objects=値`・`--annotation-path=値` | `conf/ExportTableDefinition.properties`の設定を上書きする（次項） |

上記以外の引数（`--chek`のような書き誤り等）を指定した場合は、何も処理せずに`[result]:FAIL`（終了コード`2`）で終了します。書き誤りによって、意図しないモードで実行されないようにするためです。

同梱の`run.sh`・`run.bat`に付けた引数は、そのままツールへ渡されます（例: `./run.sh --check`）。

### CLI引数による上書き

設定ファイル（`conf/mybatis.properties`・`conf/ExportTableDefinition.properties`）の各項目は、CLI引数で上書きできます。
CI等で接続情報をファイルに残したくない場合や、出力先・出力対象をジョブごとに切り替えたい場合に利用してください。

優先順位は `CLI引数 > 設定ファイルの値` です。CLI引数の値を空にした場合は、指定しなかったものとして設定ファイルの値を使います。

DB接続情報（`conf/mybatis.properties`）:

| 項目 | CLI引数 |
|---|---|
| driver | `--db-driver=値` |
| url | `--db-url=値` |
| username | `--db-username=値` |
| password | `--db-password=値` |

```
java -jar exportTableDefinition-1.0-SNAPSHOT.jar --db-url=jdbc:postgresql://localhost:5432/testdb --db-username=user --db-password=pass
```

CLI引数で `driver`/`url`/`username`/`password` の4項目すべてを指定する場合、`conf/mybatis.properties`自体が存在しなくても起動できます。

実行時設定（`conf/ExportTableDefinition.properties`）:

| 項目 | CLI引数 |
|---|---|
| schema | `--schema=値` |
| table | `--table=値` |
| outputPath | `--output-path=値` |
| chunkSize | `--chunk-size=値` |
| erDiagramMaxNodes | `--er-diagram-max-nodes=値` |
| outputObjects | `--output-objects=値` |
| annotationPath | `--annotation-path=値` |

```
java -jar exportTableDefinition-1.0-SNAPSHOT.jar --output-path=./docs/db/prod --schema=sample --table='!flyway_schema_history,*_bk'
```

* 値の形式・未指定の場合・誤りとして扱う値は、設定ファイルに書いた場合と同じです（[ExportTableDefinition.propertiesの記載内容](#exporttabledefinitionproperties-の記載内容)を参照）。誤りがある場合は、どの項目をCLI引数で上書きしたかを添えて表示します。
* `--output-path`で指定した出力先にも、`--rm-dist`で削除してよいディレクトリかの検証（[後述](#出力先ディレクトリの事前クリーンアップ--rm-distオプション)）が同じく適用されます。
* すべての項目を上書きする場合でも、`conf/ExportTableDefinition.properties`自体は必要です（実行するディレクトリを誤った場合に、既定の出力先へ黙って出力しないようにするため）。配布物に同梱の、全項目が未指定の設定ファイルをそのまま使えます。
* 環境変数の値を使いたい場合は、`--db-url="$DB_URL"`のようにシェルで展開して渡してください（[GitHub Actionsでの利用例](#db-vs-ドキュメントの差分検知--checkモード)も参照）。
* `table`の除外（`!`）・ワイルドカード（`*`）は、シェルに解釈されないよう引用符で囲んでください（bashでは`'...'`）。
* 複数のユーザーが使うマシンでは、コマンドラインの引数が他のユーザーからプロセスの一覧で見える場合があります。その場合、パスワードはCLI引数ではなく`conf/mybatis.properties`に記載してください。

> [!NOTE]
> 以前のバージョンでは、DB接続情報を環境変数（`DB_DRIVER`・`DB_URL`・`DB_USERNAME`・`DB_PASSWORD`）でも指定できました。
> 現在はCLI引数と設定ファイルのみです。環境変数で渡していた場合は、`--db-url="$DB_URL"`のようにCLI引数へ展開して渡してください。

### 出力先ディレクトリの事前クリーンアップ（`--rm-dist`オプション）

テーブル定義書は、生成対象のファイルのみを新規作成・上書きする方式のため、DBからテーブルやスキーマを削除した後に再実行しても、削除されたテーブルに対応する`.md`ファイルは`outputPath`配下に残り続けます。`--rm-dist`を付けて実行すると、書き込みを開始する前に`outputPath`のベースディレクトリを再帰的に削除してから生成するため、常に現在のDBの状態のみが出力先に反映されます。

```
java -jar exportTableDefinition-1.0-SNAPSHOT.jar --rm-dist
```

* CI上で定義書を自動生成・コミットする運用（マイグレーション後に再生成してコミットする等）で、削除されたテーブルの残骸ファイルが蓄積するのを防ぐ用途を想定しています。
* `outputPath`が未作成の場合（初回実行など）は何もせず、通常どおり生成します。
* 誤設定による被害を防ぐため、`outputPath`の解決結果がルートディレクトリ・ホームディレクトリ・カレントディレクトリ自体になる場合は削除を拒否し、`[result]:FAIL`（終了コード`2`）で終了します。`outputPath`が既存のファイル（ディレクトリではないもの）を指す場合も、そのファイルを削除せず`[result]:FAIL`で終了します（`--rm-dist`を付けない場合も同じ）。これらはDBへ接続する前に検知します。
* 削除は、設定ファイルの検証（`outputObjects`の種別名等）と、テーブル一覧等の一括取得・サイドカーYAMLの読み込みに成功した後に行います。これらの段階で失敗した場合、既存の出力は削除されません。
* `--check`モードでは出力先ディレクトリへ直接書き込まない（一時ディレクトリへ生成して比較するのみの）ため、`--check`と同時に指定した場合`--rm-dist`は無視されます。
* `outputPath`配下の`snapshot/`も削除・再生成の対象になります。

### DB vs ドキュメントの差分検知（`--check`モード）

`--check`を付けて実行すると、通常のドキュメント出力の代わりに、DBの現状から生成したスキーマのスナップショットと
`outputPath`配下の`snapshot/`に既にコミット済みのスナップショットを比較し、差分（＝マイグレーション後にドキュメントの
再生成・コミットを忘れていないか）をオブジェクト単位（例: `table sample.employee`、
`function sample.calculate_bonus(p_salary numeric)`）で検知するモードで実行されます。CI上で運用事故を機械的に
検知する用途を想定しているため、CIで利用する場合はスナップショットもコミットしておいてください。

```
java -jar exportTableDefinition-1.0-SNAPSHOT.jar --check
```

* 以下の3区分で報告します。
    * 生成側にのみ存在するもの（コミット漏れの可能性）
    * コミット側にのみ存在するもの（削除されたテーブル等の残骸の可能性）
    * 両方に存在するが内容が一致しないもの（unified diff形式で変更箇所を表示。詳細は次項）
* 差分が1件でも見つかった場合は終了コード`1`、差分がない場合は`0`、設定の誤りやDBに接続できない等で比較処理自体が失敗した場合は`2`以上（現在は`2`）で終了します（[終了コード・失敗時の表示](#終了コード失敗時の表示)を参照）。CIのジョブをそのまま失敗させられるほか、「差分あり」と「比較自体の失敗」を終了コードで区別できます。
* `outputPath`配下の`snapshot/`がまだ作成されていない場合（初回実行など）は、生成される全オブジェクトが「生成側にのみ存在するもの」として扱われ、差分ありと判定されます。
* スナップショットは実行のたびに変わる「作成日」を含まないため、ドキュメントを生成した日と別の日に`--check`を実行しても、DBに変更が無ければ差分なしと判定されます。
* スナップショットのみを生成して比較し、Markdownの描画・ER図の生成は行いません。そのため、**Markdownのみに生じた差分（手作業での編集・削除、ツールのバージョンアップによる出力形式の変更等）は検知しません**。削除されたテーブルのMarkdownの残骸ファイルが気になる場合は`--rm-dist`と組み合わせて通常実行してください。
* 関数・プロシージャは同名のもの（オーバーロード）を引数で区別するため、引数（デフォルト値を含む）を変更した場合は、変更前の関数の削除と変更後の関数の追加として報告されます。
* DBからの取得は通常実行と同じく1回です。取得結果を一時ディレクトリへ出力して比較するため、比較対象のスナップショットの書き込み・読み込みの分だけ通常実行より処理が増えます。
* `schema`/`table`/`chunkSize`/`erDiagramMaxNodes`/`outputObjects`/`annotationPath`といった設定は、通常実行と同様に適用されます。

「両方に存在するが内容が一致しないもの」は、以下のように変更箇所をunified diff形式（`diff -u`やgitと同じ表記）で表示します。
比較の前にJSONを1項目1行・配列は1要素1行へ整形しているため、行番号はファイル上のものではなく整形後のものです。

```
Content differs:
 - table sample.employee

--- committed/testdb/sample/tables.jsonl (table sample.employee)
+++ generated/testdb/sample/tables.jsonl (table sample.employee)
@@ -8,6 +8,7 @@
   "columns": [
     {"name":"employee_id","type":"integer","primaryKey":true,"notNull":true}
     {"name":"name","type":"character varying(100)","primaryKey":false,"notNull":true}
+    {"name":"nickname","type":"text","primaryKey":false,"notNull":false}
   ]
   "indexes": [
```

* 1オブジェクトあたり200行、全体で2000行を上限に表示します。超えた分は省略した旨のみ表示しますが、対象自体（`table sample.employee`等）はサマリに全件掲載されるため、見落としにはなりません。
* diffは外部ライブラリを使わず自前で計算しています（Myers法）。

> [!NOTE]
> 以前のバージョンでは`outputSnapshot=false`（既定値）の場合、Markdownのドキュメント一式をファイル単位で比較していました。
> 現在は常にスナップショットを出力し、`--check`も常にスナップショット同士の比較になります。既にMarkdownを比較する運用で
> `--check`を使っていた場合は、一度通常実行して`outputPath`配下の`snapshot/`をコミットしてから`--check`を実行してください
> （コミットするまでは、全オブジェクトが「生成側にのみ存在するもの」として差分ありと判定されます）。

GitHub Actionsでの利用例（マイグレーション後にドキュメント再生成を忘れていないかをCIで検知する）:

```yaml
- name: Check table definition document diff
  # secretsはenvで受け取り、シェルで展開してCLI引数へ渡す（ワークフローの値をスクリプトへ直接埋め込まないため）。
  # 出力先・出力対象もCLI引数でジョブごとに切り替えられる
  run: >-
    java -jar exportTableDefinition-1.0-SNAPSHOT.jar --check
    --db-url="$DB_URL" --db-username="$DB_USERNAME" --db-password="$DB_PASSWORD"
    --output-path=./docs/db/prod --table='!flyway_schema_history'
  env:
    DB_URL: ${{ secrets.DB_URL }}
    DB_USERNAME: ${{ secrets.DB_USERNAME }}
    DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
```

## 出力される内容の詳細

### ER図

テーブル間の外部キー関係をMermaid記法のER図として出力します。テーブル単位とスキーマ単位の2つの粒度で出力し、
スキーマ単位のものには索引が付きます。

| 出力先 | 内容 |
|---|---|
| 各テーブル定義書内の「ER図」セクション | 自テーブルと、直接の参照先・参照元テーブルのみを描画（自テーブルのみカラム・PK付き） |
| `erDiagram_{DB名}_{スキーマ名}.md` | スキーマ単位の全体ER図。属性なしの箱と外部キーの関連線のみ |
| `erDiagram_{DB名}_{スキーマ名}_group{連番}.md` | スキーマが大きい場合に、テーブルのまとまりごとに分割したER図 |
| `erDiagramList_{DB名}.md` | 全体ER図の索引。スキーマ別ER図へのリンクと、スキーマ跨ぎの外部キー一覧 |
| `viewpoint_{DB名}_{識別子}.md` | 観点（業務ドメイン別のテーブルのまとまり）ごとのER図。サイドカーYAMLで観点を宣言した場合のみ（[観点（Viewpoints）](#観点viewpoints)を参照） |

ER図のページに掲載する表が3000行を超える場合は、テーブル一覧と同様に別ファイルへ
分割し、前へ/次へのページ送りリンクを付けて出力します。件数が多くても情報が欠落することはありません。

全体ER図を1枚にまとめるとMermaidが描画できる規模を超えるため、スキーマ単位に分割して出力します。
スキーマ跨ぎの外部キーは参照元・参照先の双方の図に描画されるため、他スキーマのテーブルも箱として登場します。

サイドカーYAMLで論理リレーション（[`relations`](#relations論理リレーション)）を宣言している場合は、
物理的な外部キーと同じ図に**破線**（Mermaidの非識別関連）で描画されます。実線＝DBに制約あり、
破線＝宣言のみ、として読み分けてください。

図が読めなくなるのを避けるため、以下の制御を行っています。

* 外部キー・論理リレーションのいずれによる関連も持たないテーブルは図に描画せず、各ページの「ER図に掲載しているテーブル」セクションに掲載します
* 1枚あたりのテーブル数が`erDiagramMaxNodes`（[こちら](#exporttabledefinitionproperties-の記載内容)）を超える場合は、**外部キーで繋がったテーブルのまとまり（連結成分）ごとにグループへ分割**して出力します。この場合、スキーマのページはグループ一覧（規模と主なテーブルの一覧）になります
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
なお論理リレーション（[`relations`](#relations論理リレーション)）はDBに制約が存在せず上表の判定ができないため、
サイドカーYAMLでの明示指定（未指定の場合は`1対多`）が用いられます。

* 一意性は列名の一致ではなく**包含関係**で判定します。`FK(a, b)` に対して `UNIQUE(a)` がある場合は
  外部キー全体も一意となるため1対1です。逆に `UNIQUE(a, b)` があっても外部キーが `(a)` のみなら1対1ではありません
* 一意制約だけでなく`CREATE UNIQUE INDEX`で作成した一意索引も判定対象に含めます
* PostgreSQLの部分インデックス（`WHERE`付き）は条件付きの一意性しか保証しないため、判定対象から除外します
* 外部キー列にNULLを許容する場合、参照が成立しない行が存在しうるため親側は「0または1」となります
* 「親1件につき子が1件以上存在すること」はテーブル定義では表現できないため、子側が「1以上」となることはありません

### 観点（Viewpoints）

サイドカーYAMLで観点（[`viewpoints`](#viewpoints観点)）を宣言している場合は、観点ごとのページと、その索引を出力します。
観点を宣言していない場合は何も出力せず、他のドキュメントの内容も変わりません。

| 出力先 | 内容 |
|---|---|
| `viewpointList_{DB名}.md` | 観点一覧。観点名・説明（1行目）・所属テーブル数と、観点ページへのリンク。`tableList_{DB名}.md`の「関連ドキュメント」からリンクされる |
| `viewpoint_{DB名}_{識別子}.md` | 観点ページ。説明、所属テーブル同士の関連を描いたER図、所属テーブルの一覧（定義書へのリンク付き）、観点外のテーブルとの関連の一覧 |
| 所属テーブルの定義書の「所属する観点」セクション | 当該テーブルが所属する観点のページへのリンク。所属する観点が無いテーブルには出力しない |

* ER図には、所属テーブル同士の関連（外部キー・論理リレーション）のみを描画します。所属テーブルと観点外のテーブルとの関連は、「観点外のテーブルとの関連」に一覧で掲載します。
* 観点は人が選んだテーブルのまとまりのため、スキーマ別ER図のようなグループ分割は行いません。ER図に描画するテーブル数が`erDiagramMaxNodes`を超える場合は、ER図の描画を省略し、代わりに所属テーブル同士の関連を一覧で掲載します。
* 所属テーブルの一覧が3000行を超える場合は、テーブル一覧と同様に別ファイルへ分割します。

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

これらの追加オブジェクトは、`outputObjects`（[こちら](#exporttabledefinitionproperties-の記載内容)）でオブジェクト種別ごとに
出力有無を絞り込めます。トリガーを対象外にした場合は、`triggerList_{DB名}.md`だけでなく各テーブル定義書内の
「トリガー情報」セクションも出力されなくなります。

### スキーマのスナップショット（JSON Lines）

Markdownのドキュメントに加えて、常にDBから取得したスキーマ情報を構造化したスナップショットを出力します
（出力サンプル: [docs/sample/postgres/output/snapshot](./docs/sample/postgres/output/snapshot)）。
Markdownでは1つの表セルにまとめて表示している情報（NOT NULL・デフォルト値・カラムリスト等）も個別の項目として持つため、
jq等で機械的に扱えます。

```
{outputPath}/snapshot/
└─{DB名}
   ├─database.json       ・・・ DB名・DBMS種別・スナップショットの形式バージョン
   └─{スキーマ名}
      ├─tables.jsonl     ・・・ 1テーブル1行（カラム・インデックス・制約・外部キー・論理リレーション・トリガー・サイドカーの付帯情報）
      ├─functions.jsonl  ・・・ 1関数・プロシージャ1行（定義本体を含む）
      ├─sequences.jsonl  ・・・ 1シーケンス1行
      └─types.jsonl      ・・・ 1ユーザー定義型1行
```

`tables.jsonl`の1行は以下のような内容です（実際は1行。見やすさのため整形しています）。

```json
{
  "schema": "sample", "name": "audit_log", "type": "table",
  "description": "employeeテーブルの変更を記録する監査ログ。…", "remarks": "アプリケーションからの直接INSERTは禁止",
  "columns": [
    {"name": "log_id", "type": "bigint", "primaryKey": true, "notNull": true, "defaultValue": "nextval('sample.audit_log_log_id_seq'::regclass)"},
    {"name": "table_name", "type": "character varying(50)", "precisionScale": "50", "primaryKey": false, "notNull": true, "remarks": "変更対象のテーブル名"}
  ],
  "indexes": [{"name": "audit_log_pkey", "method": "btree", "unique": true, "primary": true, "definition": "CREATE UNIQUE INDEX …"}],
  "constraints": [{"name": "audit_log_pkey", "type": "PRIMARY KEY", "definition": "PRIMARY KEY (log_id)"}],
  "logicalRelations": [{"name": "rel_audit_log_employee", "columns": ["record_id"], "referenceSchema": "sample", "referenceTable": "employee", "referenceColumns": ["employee_id"], "cardinality": "OPTIONAL_ONE_TO_MANY"}]
}
```

* 1オブジェクト1行のJSON Lines形式のため、git上の差分がそのままオブジェクト単位の差分になります。1テーブル分の情報が1行にまとまっているため、行内のどこが変わったかは`git diff --word-diff`で確認すると読みやすくなります（`--check`では、変更箇所を項目単位のunified diffとして表示します）。
* 出力したスナップショットをコミットしておくと、`--check`で差分検知に利用できます（[DB vs ドキュメントの差分検知](#db-vs-ドキュメントの差分検知--checkモード)を参照）。
* 値が無い項目（コメント未設定の論理名、外部キーを持たないテーブルの`foreignKeys`等）は出力を省略します。真偽値の項目（`primaryKey`・`notNull`等）は`false`も出力します。
* 実行のたびに変わる「作成日」は含めません。DBに変更が無ければ、何度実行しても同じ内容になります。
* 値はMarkdown向けのエスケープ（`|`→`\|`等）をしない、DBのカタログ・サイドカーYAMLから取得したままの値です。
* 被参照側の外部キーは、参照元テーブルの`foreignKeys`から導出できるため保持しません。
* `schema`・`table`・`outputObjects`・`annotationPath`の設定はMarkdownと同様に適用されます。`chunkSize`・`erDiagramMaxNodes`はMarkdownの分割出力のための設定のため、スナップショットの内容には影響しません。

## 開発者向け（ソースからビルドする場合）

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
│  ├─javadoc
│  └─sample       ・・・ サンプルDBのDDLと出力のベースライン（結合テストの入力）
├─gradle
│  └─wrapper
└─src
    ├─ main     ・・・ javaソースコードが格納されているフォルダ
    │   └─ java
    │        └─ com
    │            └─ export_table_definition
    ├─ test     ・・・ 単体テスト（DB不要）
    └─ integrationTest ・・・ 結合テスト（Docker上のPostgreSQLを使う）
```

### build

以下のコマンドを実行することで、`exportTableDefinition/build/libs`フォルダ配下に`exportTableDefinition-1.0-SNAPSHOT.jar`が作成される

```
gradlew build
```

### テスト

`gradlew build`（`gradlew test`）で実行される単体テストはDBを使わない。
mapperのSQLを実DBに対して確かめる結合テストは、Dockerで使い捨てのPostgreSQLを起動するため別のタスクに分けてある（Dockerが必要）。

```
gradlew integrationTest
```

結合テストは`docs/sample/postgres/ddl.sql`を流し込んだDBに対して、各SQLの取得結果と、出力全体がコミット済みのベースライン
（`docs/sample/postgres/output`）と一致することを確かめる（基本情報の作成日は比較しない）。出力仕様を意図して変えた場合は、
ベースラインを出力し直してコミットする。PRではGitHub Actions（`.github/workflows/ci.yml`）で両方のテストが実行される。

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
