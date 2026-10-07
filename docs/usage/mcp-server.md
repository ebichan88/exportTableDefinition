# AIからテーブル定義を調べる（MCPサーバー）

出力した[スキーマのスナップショット](./cli.md#スキーマのスナップショットjson-lines)と[参考情報](./cli.md#参考情報insights)を、AIからMCP
（Model Context Protocol）のツールで検索できるようにするサーバーです。テーブル定義書のリポジトリとは別のリポジトリでコードを書くとき、
AIが正しいテーブル名・カラム・JOINの条件（外部キーと、サイドカーYAMLで宣言した論理リレーション）を調べられるようになります。

* DBには接続しません。スナップショット・参考情報のファイルを読むだけなので、DBの接続情報をAIに渡す必要はありません。
* 配布用zipの`mcp/exportTableDefinition-mcp.jar`がサーバー本体です。同梱のJava実行環境（`runtime`）で動くため、Javaのインストールは不要です。

## MCPサーバー動作イメージ

<img width="1348" height="772" alt="MCPサーバー動作イメージ" src="https://github.com/user-attachments/assets/4cb1a424-ec1b-4a2f-9965-1863b39e0a21" />

## 準備

1. このツールでテーブル定義書を出力し、`output.path`配下の`snapshot/`（観点を宣言している場合は`insights/`も）をGit等で共有する（テーブル定義書と一緒にコミットしておく等）
2. AIを使う人が、そのリポジトリを手元にcloneし、zipを展開しておく

## 起動方法

MCPクライアント（Claude Code・Claude Desktop等）が、標準入出力で通信するサーバーとして起動します。自分で起動しておく必要はありません。

```
<展開先>/runtime/bin/java -jar <展開先>/mcp/exportTableDefinition-mcp.jar --snapshot=<output.path配下のsnapshotディレクトリ>
```

Windowsでは`<展開先>\runtime\bin\java.exe`を指定します。

| 引数 | 必須 | 内容 |
|---|---|---|
| `--snapshot=<ディレクトリ>` | ○ | このツールの出力先（`output.path`）配下の`snapshot`ディレクトリ。複数のDBのスナップショットを含んでいてもよい |

* 上記以外の引数・`--snapshot`の重複は誤りとして扱います。
* 引数・スナップショットに誤りがある場合（ディレクトリが無い、`output.path`そのものを指定した、新しい形式のスナップショット、
  マージの衝突等でJSONとして読めない行がある等）は、何を直せばよいか（ファイル名・行番号を含む）を標準エラーに出し、終了コード`2`で終了します。
  MCPクライアントでサーバーの起動に失敗した場合は、クライアントのログで確認してください。
* スナップショットは起動時に読み込みます。スナップショットを更新（`git pull`等）した場合は、MCPクライアントからサーバーに再接続してください。

## MCPクライアントの設定例

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

## ツール

| ツール | 主な引数 | 内容 |
|---|---|---|
| `list_schemas` | なし | スナップショットに含まれるDB（DBMS種別）・スキーマと、スキーマごとのテーブル・ビュー・マテリアライズドビュー・関数・シーケンス・ユーザー定義型の数を返す |
| `list_viewpoints` | `database` | 観点（業務ドメイン別にテーブルをまとめる切り口。[参考情報](./cli.md#参考情報insights)）の識別子・表示名・説明・所属テーブル数を返す |
| `search_tables` | `query`（必須）、`schema`・`database`・`limit`・`viewpoint`（観点のid） | テーブル名・論理名・説明・カラム名・カラムの論理名を部分一致で検索し、一致の強い順に概要を返す。空白区切りの複数語はすべてを含むものだけを返す。`viewpoint`を指定すると、その観点の所属テーブルだけに絞り込む |
| `list_tables` | `schema`・`database`・`type`（`table`／`view`／`materialized_view`）・`includeDescription`・`limit`（既定100、最大500）・`offset`・`viewpoint`（観点のid） | テーブル（ビューを含む）の名前・論理名・区分を名前の順に返す。`viewpoint`を指定すると、その観点の所属テーブルだけに絞り込む |
| `get_table` | `table`（必須。配列で複数指定できる。最大10件）、`schema`・`database`・`sections`・`columns` | テーブル（ビューを含む）の定義を返す（スナップショットの1行）。`sections`（`columns`・`indexes`・`constraints`・`foreignKeys`・`logicalRelations`・`triggers`・`definition`）で返す項目を、`columns`で返すカラムを絞り込める。`table`に複数指定した場合は`{"tables":[...]}`でまとめて返す（`columns`は1件指定時のみ使える）。テーブルが観点に所属する場合は、所属する観点（`viewpoints`。`id`・`name`を宣言順）も返す |
| `find_columns` | `column`（必須）、`match`（`exact`／`partial`、既定`exact`）・`schema`・`database`・`limit`（既定50、最大500）・`offset` | カラムの物理名・論理名から、そのカラムを持つテーブルを逆引きする。型・PK・NOT NULL・デフォルト値と、外部キー・論理リレーションの参照先も返す |
| `get_related_tables` | `table`（必須）、`schema`・`database`・`depth`（1〜3、既定1）・`direction`（`outgoing`／`incoming`／`both`、既定`both`） | 外部キーと論理リレーションをたどり、つながるテーブルと、どのカラム同士でつながるか・多重度を返す。参照される側（被参照）からもたどれる |
| `find_join_path` | `from`・`to`（必須。`スキーマ名.テーブル名`も可）、`database`・`maxLength`（1〜6、既定4）・`limit`（1〜20、既定5） | 2つのテーブルをつなぐ最短のJOIN経路を、外部キーと論理リレーションを向きを問わずたどって返す。同じ長さの経路が複数ある場合はすべて（`limit`まで）返す |
| `list_functions` | `query`・`schema`・`database`・`limit`（既定100、最大500）・`offset` | 関数・プロシージャの名前・種別・引数・戻り値・言語を返す（定義本体は返さない）。`query`は名前の部分一致 |
| `get_function` | `function`（必須）、`schema`・`database`・`includeDefinition` | 関数・プロシージャのシグネチャ（種別・引数・戻り値・言語）を、同名のもの（オーバーロード）をまとめて返す。関数を実行するトリガー（`calledByTriggers`）も返す。`includeDefinition`を指定すると定義本体も返す（既定false。オーバーロードの本体が同じ場合は1つにまとめ、長い場合は切り詰める） |
| `list_sequences` | `query`・`schema`・`database`・`limit`（既定100、最大500）・`offset` | シーケンスの名前と所有カラムを返す |
| `get_sequence` | `sequence`（必須）、`schema`・`database` | シーケンスの定義（増分・最小値・最大値・キャッシュ・開始値・循環の有無・所有カラム）と、デフォルト値（`nextval`）で採番に使うカラム（`usedByColumns`）を返す |
| `list_types` | `query`・`category`（PostgreSQLは`ENUM`／`COMPOSITE`／`DOMAIN`／`RANGE`、Oracleは`OBJECT`／`VARRAY`／`NESTED TABLE`）・`schema`・`database`・`limit`（既定100、最大500）・`offset` | ユーザー定義型の名前と種別を返す |
| `get_type` | `type`（必須）、`schema`・`database` | ユーザー定義型の定義（ENUMの値の一覧・複合型の属性等）と、型を使うカラム（`usedByColumns`）を返す |
| `list_triggers` | `schema`・`database`・`limit`（既定100、最大500）・`offset` | トリガーを、テーブル・タイミング・イベント・実行単位・実行される関数とともに、テーブルをまたいで返す |

* テーブル名は大文字小文字を区別しません。`スキーマ名.テーブル名`の形でも指定できます。
* 同名のテーブルが複数のスキーマにある場合・見つからない場合は、候補を示すエラーを返します（AIが引数を直して呼び直します）。
* `viewpoint`に存在しない観点のidを指定した場合は、宣言されている観点を示すエラーを返します。
* 一覧を返すツールは、件数が`limit`を超える場合に続きの`offset`（`nextOffset`）を返します。
* `target.objects`で出力対象から外した種別は0件になります。
* 関数・プロシージャの定義本体は、AIのコンテキストを圧迫するため既定では返しません（`get_function`の`includeDefinition`で返します）。
* `get_table`の`sections`を指定しても、テーブル名・論理名・区分・説明・備考・所属する観点は常に返します。`columns`を指定した場合は、`sections`に関わらず指定したカラムを返します。
