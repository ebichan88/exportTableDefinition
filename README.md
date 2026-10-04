# exportTableDefinition

## Overview

DBに接続し、テーブル一覧・各テーブルの定義書・ER図などをMarkdown形式で出力するツールです。

出力されるものの全体像は以下のとおりです（各項目の詳細は[出力される内容の詳細](#出力される内容の詳細)を参照）。

| 出力物 | 内容 |
|---|---|
| README（`{DB名}/README.md`） | 生成された全ドキュメントへのリンクをまとめた索引。データベースごとに1つ生成される |
| テーブル一覧（`tableList_{DB名}.md`） | 対象スキーマ・テーブルの一覧と、各テーブル定義書・関連ドキュメントへのリンク |
| 各テーブル定義書 | カラム・インデックス・制約・外部キー情報など（PostgreSQLの場合はトリガー情報も） |
| ER図 | テーブル間の外部キー関係を表すMermaid記法の図（テーブル単位・スキーマ単位の2種類） |
| 観点ごとのページ・観点一覧 | サイドカーYAMLで宣言した業務ドメイン別の観点（「受注管理」等）ごとのER図と所属テーブル。[詳細](#観点viewpoints) |
| 追加オブジェクトの一覧・個別ページ | 関数・プロシージャ、シーケンス、ユーザー定義型（ENUM等） |
| スキーマのスナップショット | 上記と同じ情報を機械可読なJSON Lines形式で構造化したもの。[詳細](#スキーマのスナップショットjson-lines) |

あわせて、出力したスナップショットをAI（Claude Code等）から検索できるようにするMCPサーバーを同梱しています。
別のリポジトリでコードを書くAIが、テーブル定義やJOINの条件を調べられます。[詳細](#aiからテーブル定義を調べるmcpサーバー)

出力サンプル: [README](./docs/sample/postgres/output/testdb/README.md) / [テーブル一覧](./docs/sample/postgres/output/testdb/tableList_testdb.md)

### 対象DBMS

* PostgreSQL（13以上。パーティション表の外部キー・トリガーが親から子へ複製されたものを見分けるため、13以上の列を参照します）
* Oracle（12c以上）
    * Oracle固有の扱いは[Oracleの場合](#oracleの場合)を参照

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
├─mcp
│     exportTableDefinition-mcp.jar             ・・・ MCPサーバー（「AIからテーブル定義を調べる」を参照）
├─runtime                                       ・・・ 同梱のJava実行環境（別途Javaのインストール不要）
└─conf
   ├─ExportTableDefinition.properties
   └─mybatis.properties.template
```

Java実行環境（runtimeフォルダ）を同梱しているため、PCにJavaをインストールしていなくてもそのまま実行できます。

### 設定

1. `conf/mybatis.properties.template`を`conf/mybatis.properties`にリネームし、接続先DBの情報を記載する（[mybatis.propertiesの記載内容](#mybatisproperties-の記載内容)を参照）
   * パスワードは`conf/mybatis.properties`には書けません。環境変数`EXPORT_TABLE_DEFINITION_DB_PASSWORD`で渡してください（[パスワードの指定](#パスワードの指定)を参照）
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
| `outputObjects` | 出力対象とする追加オブジェクトの種別（`trigger`・`function`・`sequence`・`type`。カンマ区切りで複数指定可。後述） | 全種別 | 左記以外の値 |
| `annotationPath` | 手動付帯情報・論理リレーション・観点を記述したサイドカーYAMLのパス（後述） | マージしない | 存在しないファイル、YAMLとして読めないファイル |

* すべての項目は省略できます。キーを書かない場合と値を空白にした場合は、同じ「未指定」として扱います。
* すべての項目は、CLI引数で上書きできます（[CLI引数による上書き](#cli引数による上書き)を参照）。
* 上記以外のキー（キー名の書き誤り等）を書いた場合は誤りとして扱います。
* カンマ区切りで複数指定する項目（`schema`・`table`・`outputObjects`）は、各値の前後の空白を無視します（`schema=public, sample`のように記述できます）。
* 誤りがある場合は、見つかった誤りをまとめて表示し、`[result]:FAIL`（終了コード`2`）で終了します。設定ファイルの誤り（`conf`ディレクトリ・設定ファイル自体が見つからない場合を含む）と`outputPath`の誤りはDBへ接続する前に、`annotationPath`のファイルの誤りはDBからの取得・出力先の削除（`--rm-dist`）より前に検知します。

Markdownのドキュメントに加えて、同じ取得結果から構造化したスキーマのスナップショット（JSON Lines）を、常に
`outputPath`配下の`snapshot/`へ出力します。jq等での機械処理や、プルリクエストでのスキーマ変更のレビュー（git diff）、
`--check`モードでの差分検知に利用できます。形式は[スキーマのスナップショット（JSON Lines）](#スキーマのスナップショットjson-lines)を参照してください。

`outputObjects`は、追加オブジェクト（トリガー・関数/プロシージャ・シーケンス・ユーザー定義型）のうち、
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

`table`には完全一致のテーブル名だけでなく、以下の記法も指定できます（`schema`はスキーマ名の完全一致のみ対応）。

| 記法 | 例 | 意味 |
|---|---|---|
| ワイルドカード（`*`は0文字以上の任意文字列） | `*_bk`, `*_20240101` | パターンに一致するテーブルすべてが対象 |
| 除外（先頭に`!`） | `!flyway_schema_history` | パターンに一致するテーブルを常に対象外にする（包含パターンより優先） |
| スキーマ修飾 | `sample.employee` | 指定したスキーマのテーブルのみを対象にする（スキーマ修飾がない場合は全スキーマが対象） |

例えば`table=!flyway_schema_history,!*_bk,!*_20240101`のように除外パターンのみを指定すると、マイグレーション管理テーブルやバックアップ用の一時テーブルを定義書・ER図から除外できます。除外パターンは包含パターンと併用でき、その場合は除外が常に優先されます。なお`table`による絞り込みはSQLではなくアプリケーション側で行われるため、`schema`のみで絞り込む場合と比べてDBへの問い合わせ量が増える点に留意してください。

`chunkSize`は、カラム・インデックス・制約・外部キーといった詳細情報を、スキーマ内でさらに指定件数ごとに分割して取得・出力・破棄するための設定です。テーブル数が非常に多い（特に1スキーマに集中している）場合に、同時にメモリ保持する情報量を抑えてメモリ使用量を安定させます。値を小さくするほどピークメモリは減りますが、DBへの問い合わせ回数は増えます。出力される定義書の内容は`chunkSize`の値によって変わりません。

`erDiagramMaxNodes`は、ER図1枚に描画するテーブル数の上限です。Mermaidはノード数が増えると描画に時間がかかり、ブラウザやGitHub上でレンダリングされなくなるため、この値を超える場合の扱い（グループ分割・描画の省略）と、上限の判定対象（関連を持つテーブル数）は[ER図の出し分け](#er図の出し分け)を参照してください。読みやすい図が得られる値はDBの構造によって異なるため、出力結果を見ながら調整してください。0以下を指定すると上限なしとなり、グループ分割も行いません。

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
    cardinality: 0..1対多         # 多重度（省略可）
```

宣言した関連は以下に反映されます。

| 反映先 | 内容 |
|---|---|
| 参照元テーブル定義書の「論理リレーション情報」セクション | カラムリスト・参照先・多重度の一覧 |
| 各テーブル定義書のER図／スキーマ別ER図 | 物理外部キーの**実線**に対し、**破線**（Mermaidの非識別関連）で描画 |

補足事項:

* 読み手が「DBに制約がある」と誤読しないよう、**「外部キー情報」セクションとは別のセクション**に掲載し、注意書きを添えます。ER図でも線種で区別します。
* 関連名は参照元（子）のカラム名をカンマで連結した形式（例: `user_id`、複合キーの場合は`order_id,item_no`）で自動生成し、ER図のエッジラベル・観点ページの関連一覧に使います。テーブル定義書の「論理リレーション情報」セクションでは、同じ内容がカラムリスト列に既に出ているため、関連名の列は設けません。
* `cardinality`はDBに制約が無く機械的に判定できないため、明示指定できます。指定できる値は`1対多` / `0..1対多` / `1対1` / `0..1対1`です。省略時は`1対多`とみなします。未知の値の場合は警告を表示して`1対多`とみなします（[多重度の判定](#多重度の判定)も参照）。
* 論理リレーションは物理外部キーと同じ関連としてER図に合流するため、[ER図の出し分け](#er図の出し分け)の判定・グループ分割やスキーマ跨ぎ関連の一覧にも反映されます。関連を持たないとして図から除外されていたテーブルも、論理リレーションを宣言すれば描画対象になります。
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
```

| キー | 値 | 未指定の場合 |
|---|---|---|
| `driver` | JDBCドライバーのクラス名 | 誤り |
| `url` | 接続先のJDBC URL | 誤り |
| `username` | ユーザ名 | 空（DBの認証方式による） |

* 各項目は、後述のCLI引数でも指定できます。`driver`・`url`がいずれの方法でも指定されていない場合は、DBへ接続する前に`[result]:FAIL`（終了コード`2`）で終了します。
* 上記以外のキー（キー名の書き誤り等）を書いた場合は誤りとして扱います。
* `password`は書けません（値が空でも誤りとして扱います）。以前のテンプレートから作ったファイルに`password=`の行が残っている場合は、行ごと削除してください。パスワードは次項の環境変数で渡します。

### パスワードの指定

パスワードは、環境変数`EXPORT_TABLE_DEFINITION_DB_PASSWORD`で渡します。

```
# Linux／macOS
export EXPORT_TABLE_DEFINITION_DB_PASSWORD='パスワード'
./run.sh

# Windows（コマンドプロンプト）
set EXPORT_TABLE_DEFINITION_DB_PASSWORD=パスワード
run.bat
```

* CLI引数`--db-password=値`でも指定でき、環境変数より優先します。
* 環境変数・CLI引数のどちらも未指定（空を含む）の場合は、パスワードを空として接続します（DBの認証方式による）。
* 設定ファイルに書けないのは、作業ディレクトリ内のファイルが、AIエージェント等のツールから読まれ得るためです。秘密をファイルに残さないようにしています。

### コマンドライン引数

| 引数 | 意味 |
|---|---|
| `--check` | DB vs ドキュメントの差分検知モードで実行する（[後述](#db-vs-ドキュメントの差分検知--checkモード)） |
| `--rm-dist` | 書き込み前に出力先ディレクトリを削除する（[後述](#出力先ディレクトリの事前クリーンアップ--rm-distオプション)） |
| `--db-driver=値`・`--db-url=値`・`--db-username=値` | DB接続情報を上書きする（次項） |
| `--db-password=値` | パスワードを指定する（[パスワードの指定](#パスワードの指定)を参照） |
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
| password | `--db-password=値`（設定ファイルではなく、環境変数`EXPORT_TABLE_DEFINITION_DB_PASSWORD`の値より優先する） |

```
java -jar exportTableDefinition-1.0-SNAPSHOT.jar --db-url=jdbc:postgresql://localhost:5432/testdb --db-username=user
```

CLI引数で `driver`/`url`/`username` の3項目すべてを指定する場合（パスワードは環境変数またはCLI引数）、`conf/mybatis.properties`自体が存在しなくても起動できます。

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
* 複数のユーザーが使うマシンでは、コマンドラインの引数が他のユーザーからプロセスの一覧で見える場合があります。パスワードはCLI引数ではなく、環境変数`EXPORT_TABLE_DEFINITION_DB_PASSWORD`で渡してください。

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

GitHub Actionsでの利用例（マイグレーション後にドキュメント再生成を忘れていないかをCIで検知する）:

```yaml
- name: Check table definition document diff
  # secretsはenvで受け取り、シェルで展開してCLI引数へ渡す（ワークフローの値をスクリプトへ直接埋め込まないため）。
  # パスワードはツールが環境変数から直接読む。出力先・出力対象もCLI引数でジョブごとに切り替えられる
  run: >-
    java -jar exportTableDefinition-1.0-SNAPSHOT.jar --check
    --db-url="$DB_URL" --db-username="$DB_USERNAME"
    --output-path=./docs/db/prod --table='!flyway_schema_history'
  env:
    DB_URL: ${{ secrets.DB_URL }}
    DB_USERNAME: ${{ secrets.DB_USERNAME }}
    EXPORT_TABLE_DEFINITION_DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
```

## 出力される内容の詳細

### 出力先ディレクトリの構成

生成されるすべてのドキュメントは、出力先（`outputPath`）の直下に作成される`{DB名}/`ディレクトリにまとめて出力されます。
同じ出力先へ複数のデータベースを出力しても、データベースごとに`{DB名}/`ディレクトリが分かれるため、ドキュメントが混ざりません。

```
{outputPath}/
└─{DB名}/
   ├─README.md                       ・・・ このデータベースの全ドキュメントへのリンクをまとめた索引（自動生成）
   ├─tableList_{DB名}.md
   ├─erDiagram_{DB名}_{スキーマ名}.md
   ├─erDiagramList_{DB名}.md
   ├─viewpointList_{DB名}.md         ・・・ 観点を宣言した場合のみ
   ├─viewpoint_{DB名}_{識別子}.md
   ├─triggerList_{DB名}.md
   ├─functionList_{DB名}.md
   ├─sequenceList_{DB名}.md
   ├─typeList_{DB名}.md
   └─{スキーマ名}/
      ├─{テーブル区分}/{物理テーブル名}.md ・・・ テーブル定義書
      ├─function/{関数名}.md
      ├─sequence/{シーケンス名}.md
      └─type/{型名}.md
```

以降の節に記載するファイル名は、この`{DB名}/`ディレクトリからの相対的なファイル名です
（スキーマ配下の個別定義書のみ`{DB名}/`から続く`{スキーマ名}/...`のパスで示します）。

### ER図

テーブル間の外部キー関係をMermaid記法のER図として出力します。テーブル単位とスキーマ単位の2つの粒度で出力し、
スキーマ単位のものには索引が付きます。

| 出力先 | 内容 |
|---|---|
| 各テーブル定義書内の「ER図」セクション | 自テーブルと、直接の参照先・参照元テーブルのみを描画（自テーブルは全カラム、参照先・参照元テーブルは関連をつなぐカラムのみ） |
| `erDiagram_{DB名}_{スキーマ名}.md` | スキーマ単位の全体ER図。関連をつなぐカラムのみを表示した箱と、外部キーの関連線 |
| `erDiagram_{DB名}_{スキーマ名}_group{連番}.md` | スキーマが大きい場合に、テーブルのまとまりごとに分割したER図 |
| `erDiagramList_{DB名}.md` | 全体ER図の索引。スキーマ別ER図へのリンクと、スキーマ跨ぎの外部キー一覧 |
| `viewpoint_{DB名}_{識別子}.md` | 観点（業務ドメイン別のテーブルのまとまり）ごとのER図。サイドカーYAMLで観点を宣言した場合のみ（[観点（Viewpoints）](#観点viewpoints)を参照） |

ER図のテーブルの箱には、次の内容を表示します。

* 箱の見出しは`テーブル名（論理テーブル名）`です。論理テーブル名（テーブルのコメント）が無い場合はテーブル名のみです。
* 箱には、その図に描いた関連（外部キー・論理リレーション）の参照元・参照先として使われるカラムだけを表示します。
  各テーブル定義書の「ER図」セクションの自テーブルだけは、全カラムを表示します。
* カラムは定義順に、`型 物理カラム名 キー "論理カラム名"`の形式で表示します。型は桁数・精度を除いたもの、
  キーは主キーなら`PK`、図に描いた関連の参照元のカラムなら`FK`（両方なら`PK, FK`）です。
  論理カラム名（カラムのコメント）が無い場合は省略します。
* 論理リレーションで宣言したカラムが実在しない場合、そのカラムは表示しません。

ER図のページに掲載する表が3000行を超える場合は、テーブル一覧と同様に別ファイルへ
分割し、前へ/次へのページ送りリンクを付けて出力します。件数が多くても情報が欠落することはありません。

全体ER図を1枚にまとめるとMermaidが描画できる規模を超えるため、スキーマ単位に分割して出力します。
スキーマ跨ぎの外部キーは参照元・参照先の双方の図に描画されるため、他スキーマのテーブルも箱として登場します。

図が読めなくなるのを避けるため、描画するかどうか・分割するかどうかを[ER図の出し分け](#er図の出し分け)の規則で決めています。

図中のノードから各テーブル定義書への導線は、各ページの「テーブル一覧」セクションのリンクで辿れます
（Mermaidの`click`構文はGitHub上では無効化されるため、リンクは表側に持たせています）。

`erDiagramList_{DB名}.md`への導線は、`tableList_{DB名}.md`の「関連ドキュメント」セクションに配置しています。

ER図の出力はPostgreSQL／Oracleの双方に対応しています。

#### ER図の出し分け

ER図の描画・分割・フォールバックの規則は、このセクションに集約しています（他の節からはここへリンクしています）。
判定に使う「ノード数」は、**外部キーまたは論理リレーションによる関連を持つテーブルの数**です。関連を持たないテーブルは図に描画されずノード数にも数えず、各ページには載りません（全テーブルは`tableList_{DB名}.md`に載っています）。
上限は`erDiagramMaxNodes`（[こちら](#exporttabledefinitionproperties-の記載内容)。0以下は上限なし）で、以下の表の「上限超」はノード数がこの値を超えることを指します。上限なしの場合は常に「上限以内」の行になります。

| ページ | 関連なし（ノード数0） | 上限以内 | 上限超 |
|---|---|---|---|
| 各テーブル定義書の「ER図」セクション | 描画なし | 自テーブルと直接の参照先・参照元を描画 | （上限の対象外） |
| スキーマ別ER図 `erDiagram_{DB名}_{スキーマ名}.md` | 「関連を持つテーブルはありません」と表示 | 1枚に描画し、掲載テーブルの表を付ける | 連結成分ごとにグループへ分割する（下記）。分割しても連結成分が1つ以下なら分割せず、ER図の描画を省略して外部キー一覧を掲載 |
| グループ別ER図 `erDiagram_{DB名}_{スキーマ名}_group{連番}.md` | （作られない） | 1枚に描画し、掲載テーブルの表を付ける | そのグループのみ描画を省略し、外部キー一覧を掲載 |
| 観点ページ `viewpoint_{DB名}_{識別子}.md` | 「所属テーブル同士の関連はありません」と表示 | 所属テーブル同士の関連を描画 | 描画を省略し、所属テーブル同士の関連を一覧で掲載（グループ分割はしない） |

分割の規則（スキーマ別ER図が上限超のとき）:

* 外部キーで繋がったテーブルのまとまり（連結成分）を求め、ノード数の多い順に並べます。同数の場合はテーブル名の昇順です。
* 1テーブルだけのグループが大量にできるのを避けるため、上限に収まる範囲で複数の連結成分を同じグループにまとめます。まとめられたテーブル同士は線で繋がっていないため、関連を誤読するおそれはありません。
* 1つの連結成分だけで上限を超える場合は、その成分が単独のグループになり、上表の「描画を省略」の扱いになります。
* 分割した場合のスキーマ別ER図のページは、グループ一覧（規模と主なテーブル）になります。

線種は、物理的な外部キーが**実線**、論理リレーション（[`relations`](#relations論理リレーション)）が**破線**（Mermaidの非識別関連）です。論理リレーションは物理外部キーと同じ関連として、上の判定・分割・スキーマ跨ぎの外部キー一覧の対象になります。

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
* 観点は人が選んだテーブルのまとまりのため、スキーマ別ER図のようなグループ分割は行いません。ER図に描画するテーブル数が`erDiagramMaxNodes`を超える場合の扱いは[ER図の出し分け](#er図の出し分け)を参照してください。
* 所属テーブルの一覧が3000行を超える場合は、テーブル一覧と同様に別ファイルへ分割します。

### 追加の出力対象（トリガー・関数等）

PostgreSQLの場合は、テーブル定義に加えて以下のオブジェクトも出力します。

| 対象 | 取得元カタログ | 出力 |
|---|---|---|
| トリガー | `pg_trigger` + `pg_get_triggerdef` | 各テーブル定義書内の「トリガー情報」セクション + `triggerList_{DB名}.md` |
| 関数・プロシージャ | `pg_proc` + `pg_get_functiondef`（plpgsql/sql/C 等） | `functionList_{DB名}.md` + `{DB名}/{スキーマ名}/function/{関数名}.md`（後者のみ`{DB名}/`から続くパス） |
| シーケンス | `pg_sequences`（増分・最小値・最大値・キャッシュ・開始値・循環・所有カラム） | `sequenceList_{DB名}.md` + `{DB名}/{スキーマ名}/sequence/{シーケンス名}.md`（後者のみ`{DB名}/`から続くパス） |
| ユーザー定義型（ENUM等） | `pg_type` + `pg_enum` | `typeList_{DB名}.md` + `{DB名}/{スキーマ名}/type/{型名}.md`（後者のみ`{DB名}/`から続くパス） |

各一覧（`functionList`／`sequenceList`／`typeList`／`triggerList`）への導線は、`tableList_{DB名}.md` の
「関連ドキュメント」セクションに集約しています（対象が存在するカテゴリのみリンクを表示します）。

上の表の取得元はPostgreSQLのものです。Oracleでの扱いは[Oracleの場合](#oracleの場合)を参照してください。
また、関数・プロシージャ・シーケンス・ユーザー定義型はスキーマ単位のオブジェクトのため、
`table`（出力対象テーブル）による絞り込みの対象外です（`schema`による絞り込みのみ適用されます）。

これらの追加オブジェクトは、`outputObjects`（[こちら](#exporttabledefinitionproperties-の記載内容)）でオブジェクト種別ごとに
出力有無を絞り込めます。トリガーを対象外にした場合は、`triggerList_{DB名}.md`だけでなく各テーブル定義書内の
「トリガー情報」セクションも出力されなくなります。

### PostgreSQLのパーティション表

宣言的パーティション（`PARTITION BY`）を使ったテーブルは、親（パーティション表）だけをテーブルとして出力し、
子のパーティションは親のテーブル定義書の「パーティション情報」セクションにまとめます。
月次パーティションのように子が大量にあっても、テーブル一覧とER図が子で埋まりません。

| 対象 | 出力 |
|---|---|
| パーティション表（親） | 通常のテーブルと同じく、カラム・インデックス・制約・外部キー・トリガーを出力します（インデックスは親に付いたパーティションインデックス。定義に`ON ONLY`が付きます）。区分は`table`、論理名は親の`COMMENT ON`です。 |
| 子のパーティション（多段パーティションの中間・親と別のスキーマに置いたものを含む） | テーブル一覧・個別の定義書・ER図・スナップショットには出しません。親の「パーティション情報」に、パーティションキー（`RANGE (sold_on)`等）と、パーティションごとの名前・親・範囲（`FOR VALUES FROM ... TO ...`・`DEFAULT`等）を、親から子へ階層順に載せます。多段パーティションは「親」の列で入れ子を示します。 |
| 外部キー・トリガー | 親から子のパーティションへDB内で複製されたものは出さず、親に定義したものだけを1つ出力します。パーティション表を参照するテーブルの外部キーも、参照先のパーティションごとに複製されたものは出しません。 |

* 子のパーティションにだけ付けたインデックス・制約・外部キーは出力されません（子の定義書を持たないため）。子にだけ付けたトリガーは、`triggerList_{DB名}.md`にのみ載ります。
* `table`に子のパーティションの名前を指定しても、出力対象になりません。親が出力対象であれば、子は親の「パーティション情報」に載ります。
* スナップショットには、パーティションキー（`partitionKey`）だけを含め、個々のパーティションは含めません。`pg_partman`等でパーティションを自動的に追加する運用でも、`--check`が追加のたびに差分を報告しないようにするためです。パーティションの追加で変わるのは、親の「パーティション情報」（Markdown）だけです。
* 旧来の継承（`INHERITS`）は対象外で、親子とも通常のテーブルとして出力します。
* **この機能を含むバージョンへ更新した後の初回の`--check`は、パーティション表を持つDBで差分を報告します**（親へのカラム・インデックスの追加、子のパーティションの消滅、`partitionKey`の追加）。ドキュメントとスナップショットを再生成してコミットしてください。

### Oracleの場合

テーブル・ビュー等に加えて、PostgreSQLと同じ種別の追加オブジェクトとパーティション情報を出力します。Oracleの仕組みに合わせて、以下のように扱います。

| 対象 | 出力 |
|---|---|
| トリガー | テーブル・ビューのトリガー（スキーマ・データベースのイベントトリガーは対象外）。本体のPL/SQLはトリガーの中に書かれ、呼び出す関数が無いため、「実行関数」は空欄です。定義には宣言部（`CREATE OR REPLACE TRIGGER ... FOR EACH ROW`とWHEN句）を出力します。 |
| 関数・プロシージャ | 単独の関数・プロシージャと、パッケージ内のサブプログラム（`パッケージ名.サブプログラム名`の名前。オーバーロードには番号を振ります）。サブプログラムの定義には、パッケージの仕様部と本体を出力します。 |
| シーケンス | IDENTITY列が自動で作るシーケンス（`ISEQ$$_...`）は出力しません（名前が環境ごとに変わるため。IDENTITY列はカラムのデフォルト値に`GENERATED ... AS IDENTITY`と出力します）。開始値はディクショナリに残らないため空欄です。 |
| ユーザー定義型 | オブジェクト型（属性の一覧）と、コレクション型（`VARRAY(n) OF ...`・`TABLE OF ...`）。 |
| パーティション | テーブル一覧には元からパーティション表だけが並びます。パーティション表の「パーティション情報」に、パーティションキー（`RANGE (WORK_DATE)`等）と、パーティション・サブパーティションを位置の順に、境界（`VALUES LESS THAN (...)`・`VALUES (...)`・`DEFAULT`）とともに載せます。HASHパーティションは境界を持たないため空欄です。 |

* **この機能を含むバージョンへ更新した後の初回の`--check`は、Oracleのデータベースで差分を報告します**（関数・シーケンス・ユーザー定義型・トリガー・カラムのデフォルト値・`partitionKey`の追加）。ドキュメントとスナップショットを再生成してコミットしてください。

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
      ├─tables.jsonl     ・・・ 1テーブル1行（カラム・インデックス・制約・外部キー・論理リレーション・トリガー・サイドカーの付帯情報・パーティション表のパーティションキー）
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
  "logicalRelations": [{"name": "record_id", "columns": ["record_id"], "referenceSchema": "sample", "referenceTable": "employee", "referenceColumns": ["employee_id"], "cardinality": "OPTIONAL_ONE_TO_MANY"}]
}
```

* 1オブジェクト1行のJSON Lines形式のため、git上の差分がそのままオブジェクト単位の差分になります。1テーブル分の情報が1行にまとまっているため、行内のどこが変わったかは`git diff --word-diff`で確認すると読みやすくなります（`--check`では、変更箇所を項目単位のunified diffとして表示します）。
* 出力したスナップショットをコミットしておくと、`--check`で差分検知に利用できます（[DB vs ドキュメントの差分検知](#db-vs-ドキュメントの差分検知--checkモード)を参照）。
* 値が無い項目（コメント未設定の論理名、外部キーを持たないテーブルの`foreignKeys`等）は出力を省略します。真偽値の項目（`primaryKey`・`notNull`等）は`false`も出力します。
* 実行のたびに変わる「作成日」は含めません。DBに変更が無ければ、何度実行しても同じ内容になります。
* 値はMarkdown向けのエスケープ（`|`→`\|`等）をしない、DBのカタログ・サイドカーYAMLから取得したままの値です。
* 被参照側の外部キーは、参照元テーブルの`foreignKeys`から導出できるため保持しません。
* `schema`・`table`・`outputObjects`・`annotationPath`の設定はMarkdownと同様に適用されます。`chunkSize`・`erDiagramMaxNodes`はMarkdownの分割出力のための設定のため、スナップショットの内容には影響しません。

## AIからテーブル定義を調べる（MCPサーバー）

出力した[スキーマのスナップショット](#スキーマのスナップショットjson-lines)を、AIからMCP（Model Context Protocol）のツールで
検索できるようにするサーバーです。テーブル定義書のリポジトリとは別のリポジトリでコードを書くとき、AIが正しいテーブル名・カラム・
JOINの条件（外部キーと、サイドカーYAMLで宣言した論理リレーション）を調べられるようになります。

* DBには接続しません。スナップショットのファイルを読むだけなので、DBの接続情報をAIに渡す必要はありません。
* 配布用zipの`mcp/exportTableDefinition-mcp.jar`がサーバー本体です。同梱のJava実行環境（`runtime`）で動くため、Javaのインストールは不要です。

### 準備

1. このツールでテーブル定義書を出力し、`outputPath`配下の`snapshot/`をGit等で共有する（テーブル定義書と一緒にコミットしておく等）
2. AIを使う人が、そのリポジトリを手元にcloneし、zipを展開しておく

### 起動方法

MCPクライアント（Claude Code・Claude Desktop等）が、標準入出力で通信するサーバーとして起動します。自分で起動しておく必要はありません。

```
<展開先>/runtime/bin/java -jar <展開先>/mcp/exportTableDefinition-mcp.jar --snapshot=<outputPath配下のsnapshotディレクトリ>
```

Windowsでは`<展開先>\runtime\bin\java.exe`を指定します。

| 引数 | 必須 | 内容 |
|---|---|---|
| `--snapshot=<ディレクトリ>` | ○ | このツールの出力先（`outputPath`）配下の`snapshot`ディレクトリ。複数のDBのスナップショットを含んでいてもよい |

* 上記以外の引数・`--snapshot`の重複は誤りとして扱います。
* 引数・スナップショットに誤りがある場合（ディレクトリが無い、`outputPath`そのものを指定した、新しい形式のスナップショット、
  マージの衝突等でJSONとして読めない行がある等）は、何を直せばよいか（ファイル名・行番号を含む）を標準エラーに出し、終了コード`2`で終了します。
  MCPクライアントでサーバーの起動に失敗した場合は、クライアントのログで確認してください。
* スナップショットは起動時に読み込みます。スナップショットを更新（`git pull`等）した場合は、MCPクライアントからサーバーに再接続してください。

### MCPクライアントの設定例

Claude Codeの場合、次のコマンドで登録できます（Linux／macOSの例）。

```
claude mcp add table-definition -- /opt/exportTableDefinition-linux/runtime/bin/java -jar /opt/exportTableDefinition-linux/mcp/exportTableDefinition-mcp.jar --snapshot=/work/schema-docs/output/snapshot
```

チームで共有する場合は、アプリのリポジトリに`.mcp.json`をコミットし、各自の環境で異なるパスを環境変数で与えます
（`ETD_HOME`はzipの展開先、`SCHEMA_SNAPSHOT_DIR`はcloneしたテーブル定義書のリポジトリの`snapshot`ディレクトリ）。

```json
{
  "mcpServers": {
    "table-definition": {
      "command": "${ETD_HOME}/runtime/bin/java",
      "args": ["-jar", "${ETD_HOME}/mcp/exportTableDefinition-mcp.jar", "--snapshot=${SCHEMA_SNAPSHOT_DIR}"]
    }
  }
}
```

他のMCPクライアントでも、`command`に同梱のjava、`args`に上記の引数を指定すれば利用できます。

### ツール

| ツール | 主な引数 | 内容 |
|---|---|---|
| `list_schemas` | なし | スナップショットに含まれるDB（DBMS種別）・スキーマと、スキーマごとのテーブル・ビュー・マテリアライズドビュー・関数・シーケンス・ユーザー定義型の数を返す |
| `search_tables` | `query`（必須）、`schema`・`database`・`limit` | テーブル名・論理名・説明・カラム名・カラムの論理名を部分一致で検索し、一致の強い順に概要を返す。空白区切りの複数語はすべてを含むものだけを返す |
| `list_tables` | `schema`・`database`・`type`（`table`／`view`／`materialized_view`）・`includeDescription`・`limit`（既定100、最大500）・`offset` | テーブル（ビューを含む）の名前・論理名・区分を名前の順に返す |
| `get_table` | `table`（必須）、`schema`・`database`・`sections`・`columns` | テーブル（ビューを含む）の定義を返す（スナップショットの1行）。`sections`（`columns`・`indexes`・`constraints`・`foreignKeys`・`logicalRelations`・`triggers`・`definition`）で返す項目を、`columns`で返すカラムを絞り込める |
| `find_columns` | `column`（必須）、`match`（`exact`／`partial`、既定`exact`）・`schema`・`database`・`limit`（既定50、最大500）・`offset` | カラムの物理名・論理名から、そのカラムを持つテーブルを逆引きする。型・PK・NOT NULL・デフォルト値と、外部キー・論理リレーションの参照先も返す |
| `get_related_tables` | `table`（必須）、`schema`・`database`・`depth`（1〜3、既定1）・`direction`（`outgoing`／`incoming`／`both`、既定`both`） | 外部キーと論理リレーションをたどり、つながるテーブルと、どのカラム同士でつながるか・多重度を返す。参照される側（被参照）からもたどれる |
| `find_join_path` | `from`・`to`（必須。`スキーマ名.テーブル名`も可）、`database`・`maxLength`（1〜6、既定4）・`limit`（1〜20、既定5） | 2つのテーブルをつなぐ最短のJOIN経路を、外部キーと論理リレーションを向きを問わずたどって返す。同じ長さの経路が複数ある場合はすべて（`limit`まで）返す |
| `list_functions` | `query`・`schema`・`database`・`limit`（既定100、最大500）・`offset` | 関数・プロシージャの名前・種別・引数・戻り値・言語を返す（定義本体は返さない）。`query`は名前の部分一致 |
| `get_function` | `function`（必須）、`schema`・`database` | 関数・プロシージャのシグネチャ（種別・引数・戻り値・言語）を、同名のもの（オーバーロード）をまとめて返す（定義本体は返さない）。関数を実行するトリガー（`calledByTriggers`）も返す |
| `list_sequences` | `query`・`schema`・`database`・`limit`（既定100、最大500）・`offset` | シーケンスの名前と所有カラムを返す |
| `get_sequence` | `sequence`（必須）、`schema`・`database` | シーケンスの定義（増分・最小値・最大値・キャッシュ・開始値・循環の有無・所有カラム）と、デフォルト値（`nextval`）で採番に使うカラム（`usedByColumns`）を返す |
| `list_types` | `query`・`category`（PostgreSQLは`ENUM`／`COMPOSITE`／`DOMAIN`／`RANGE`、Oracleは`OBJECT`／`VARRAY`／`NESTED TABLE`）・`schema`・`database`・`limit`（既定100、最大500）・`offset` | ユーザー定義型の名前と種別を返す |
| `get_type` | `type`（必須）、`schema`・`database` | ユーザー定義型の定義（ENUMの値の一覧・複合型の属性等）と、型を使うカラム（`usedByColumns`）を返す |
| `list_triggers` | `schema`・`database`・`limit`（既定100、最大500）・`offset` | トリガーを、テーブル・タイミング・イベント・実行単位・実行される関数とともに、テーブルをまたいで返す |

* テーブル名は大文字小文字を区別しません。`スキーマ名.テーブル名`の形でも指定できます。
* 同名のテーブルが複数のスキーマにある場合・見つからない場合は、候補を示すエラーを返します（AIが引数を直して呼び直します）。
* 一覧を返すツールは、件数が`limit`を超える場合に続きの`offset`（`nextOffset`）を返します。
* `outputObjects`で出力対象から外した種別は0件になります。
* 関数・プロシージャの定義本体は、AIのコンテキストを圧迫するため返しません。
* `get_table`の`sections`を指定しても、テーブル名・論理名・区分・説明・備考は常に返します。`columns`を指定した場合は、`sections`に関わらず指定したカラムを返します。

## 開発者向け（ソースからビルドする場合）

### 主なディレクトリ構成

```
exportTableDefinition
├─cli              ・・・ このツール本体（Gradleのサブプロジェクト）
│  ├─build
│  │  └─libs
│  │      ├─conf  ・・・ 設定ファイルが格納されているフォルダ
│  │      │  ├─ExportTableDefinition.properties
│  │      │  └─mybatis.properties
│  │      ├─output
│  │      └─exportTableDefinition-1.0-SNAPSHOT.jar ・・・ 実行可能形式Jarファイル
│  └─src
│      ├─ main     ・・・ javaソースコードが格納されているフォルダ
│      │   └─ java
│      │        └─ com
│      │            └─ export_table_definition
│      ├─ test     ・・・ 単体テスト（DB不要）
│      └─ integrationTest ・・・ 結合テスト（Docker上のPostgreSQL・Oracleを使う）
├─mcp-server       ・・・ スナップショットをAIから検索するMCPサーバー（Gradleのサブプロジェクト）
│  └─build
│      └─libs
│          └─exportTableDefinition-mcp.jar ・・・ MCPサーバーの実行可能形式Jarファイル
├─docs           ・・・ アーキテクチャ等のドキュメントが格納されているフォルダ
│  └─sample       ・・・ サンプルDBのDDLと出力のベースライン（結合テストの入力）
├─gradle
│  └─wrapper
├─scripts        ・・・ 配布用zipに同梱する起動スクリプト
├─build.gradle   ・・・ サブプロジェクト共通のビルド設定
└─settings.gradle
```

### ビルド

以下のコマンドを実行することで、`exportTableDefinition/cli/build/libs`フォルダ配下に`exportTableDefinition-1.0-SNAPSHOT.jar`が、
`exportTableDefinition/mcp-server/build/libs`フォルダ配下にMCPサーバーの`exportTableDefinition-mcp.jar`が作成される

```
gradlew build
```

### テスト

`gradlew build`（`gradlew test`）で実行される単体テストはDBを使わない。MCPサーバー（`mcp-server`）のテストもここに含まれ、
ビルドしたjarを子プロセスで起動してMCPクライアントから呼び出すE2Eテストまでを行う。
mapperのSQLを実DBに対して確かめる結合テストは、Dockerで使い捨てのPostgreSQLを起動するため別のタスクに分けてある（Dockerが必要）。

```
gradlew integrationTest
```

結合テストは`docs/sample/postgres/ddl.sql`を流し込んだDBに対して、各SQLの取得結果と、出力全体がコミット済みのベースライン
（`docs/sample/postgres/output`）と一致することを確かめる（基本情報の作成日は比較しない）。出力仕様を意図して変えた場合は、
ベースラインを出力し直してコミットする。

Oracle用のmapperは、Docker上の使い捨てのOracle Database Free（`gvenzl/oracle-free`。イメージ約1.3GB・メモリ2GB程度）に対して確かめる。
PostgreSQLより重いため、さらに別のタスクに分けてある。

```
gradlew oracleIntegrationTest
```

`docs/sample/oracle/ddl.sql`（PostgreSQL版と同じスキーマ構成をOracleで作るDDL）を流し込んだDBに対して、各SQLの取得結果と、
出力全体がベースライン（`docs/sample/oracle/output`）と一致することを確かめる。
PRではGitHub Actions（`.github/workflows/ci.yml`）でこれらのテストがすべて実行される。

#### カバレッジ

`gradlew build`（`gradlew test`）を実行すると、単体テストのカバレッジ計測（JaCoCo）も行われる。

```
gradlew jacocoTestReport
```

でHTMLレポート（`cli/build/reports/jacoco/test/html/index.html`）を生成できる。
また`gradlew build`（＝`check`）には`jacocoTestCoverageVerification`が含まれており、ドメイン層
（`com.export_table_definition.domain`配下）の単体テストカバレッジがline 95%・branch 85%を下回ると
ビルドが失敗する（結合テストは対象外。基準は`cli/build.gradle`の`jacocoTestCoverageVerification`で定義）。
PRではGitHub ActionsがカバレッジレポートをArtifactとしてアップロードし、PRへの概要コメントも投稿する。

### Javadoc

以下のコマンドを実行することで、`exportTableDefinition/cli/build/docs/javadoc`フォルダ配下にjavadocが作成される（`build`配下はGit管理対象外）

```
gradlew javadoc
```

### 実行方法

`conf/ExportTableDefinition.properties`（※）及び`conf/mybatis.properties`に必要な設定値を記載した状態で以下のコマンドを実行する

```
java -jar .\exportTableDefinition-1.0-SNAPSHOT.jar
```

※`conf/mybatis.properties.template`を`conf/mybatis.properties`にリネームしてください
