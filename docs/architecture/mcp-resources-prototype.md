# MCPのリソース（`@`での添付）の検証用の試作

#78のM7の残り（resources）を採用するかを、Claude CodeとVS Codeの実機で決めるための試作。結果が出たら、採用する出し方だけを
`docs/architecture/mcp-server.md`に取り込み、この文書は消す。

## 試作の中身

テーブルの定義（`get_table`と同じJSON）を、利用者が会話に添付できるリソースとして出す。出し方は2通りあり、クライアントの対応が
異なるため、起動時に切り替えられる。

| 出し方 | MCPでの実体 | 狙い |
|---|---|---|
| `list` | 全テーブルを具体的なリソースとして列挙する（`resources/list`）。URIは`exporttable://{DB名}/{スキーマ名}/{テーブル名}`、名前は`論理名 (スキーマ名.物理名)` | クライアントの`@`の一覧で、論理名・物理名のどちらでもあいまい検索できるか |
| `template` | URIテンプレート（`resources/templates/list`）と、変数（`database`・`schema`・`table`）の補完（`completion/complete`） | 数千テーブルを列挙せずに、入力しながら絞り込めるか |

- 切り替えは起動時のシステムプロパティ`-Dmcp.resources=list,template`（既定）・`list`・`template`・`none`。
- 補完は、テーブル名を物理名・スキーマ修飾名・論理名のどれで入力しても候補が出る。返す値は物理名（URIの区間にするためエンコード済み）。
- 実装は`mcp-server`の`tool.TableDefinitionResources`・`tool.TableResourceUri`。`McpServerMain`が`-Dmcp.resources`を読む。

## 手順

### 1. jarと3,000テーブルのスナップショットを用意する

```
./gradlew :mcp-server:shadowJar
python3 scripts/generate-large-snapshot.py --tables 3000 --out /tmp/big-snapshot
```

jarは`mcp-server/build/libs/exportTableDefinition-mcp.jar`。スナップショットは6スキーマ・3,000テーブルで、
物理名（`t_order_detail`）と論理名（`受注明細`）を持つ。件数は`--tables`で変えられる。

### 2. MCPクライアントに登録する

出し方ごとに別名で登録すると、切り替えずに見比べられる。

Claude Code:

```
claude mcp add etd-list     -- java -Dmcp.resources=list     -jar <jarのパス> --snapshot=/tmp/big-snapshot
claude mcp add etd-template -- java -Dmcp.resources=template -jar <jarのパス> --snapshot=/tmp/big-snapshot
```

VS Code（`.vscode/mcp.json`）:

```json
{
  "servers": {
    "etd-list": {
      "type": "stdio",
      "command": "java",
      "args": ["-Dmcp.resources=list", "-jar", "<jarのパス>", "--snapshot=/tmp/big-snapshot"]
    },
    "etd-template": {
      "type": "stdio",
      "command": "java",
      "args": ["-Dmcp.resources=template", "-jar", "<jarのパス>", "--snapshot=/tmp/big-snapshot"]
    }
  }
}
```

### 3. 確認する

| # | 確認すること | Claude Code | VS Code |
|---|---|---|---|
| 1 | `list`: `@`（VS Codeは「コンテキストの追加」→「MCP Resource」）の一覧にテーブルが出る | | |
| 2 | `list`: 物理名の一部（`order_det`）で絞り込める | | |
| 3 | `list`: 論理名（`受注明細`）で絞り込める | | |
| 4 | `list`: 3,000件で一覧の表示・絞り込みが重くならない（応答の速さ） | | |
| 5 | `list`: 選んで送信すると、テーブル定義がプロンプトに添付される（AIが中身を踏まえて答える） | | |
| 6 | `list`: AIが自分でリソースの一覧を取得するツールを呼んだとき、3,000件がコンテキストにあふれない（Claude Code） | | — |
| 7 | `template`: テンプレートが`@`の一覧に出る | | |
| 8 | `template`: 変数の入力中に候補（補完）が出る。物理名・論理名のどちらでも | | |
| 9 | `template`: 補完で完成させたURIで送信すると、テーブル定義が添付される（Claude Codeは、リスト外のURIが添付されない可能性がある） | | |
| 10 | `template`: VS Codeの入力欄の形（テキストボックス／選択リスト）と、変数を入れる順番 | — | |

`@`で探すときは、サーバー名を付けずに`@order_det`・`@受注明細`のように打つ。`@etd-list:受注明細`のようにサーバー名を付けると、
サーバー名の部分が全件に当たるため、論理名の部分が効かず順位が崩れる（Fuse.jsの再現で確認）。

### 4. 結果に応じた判断

- `list`の2〜5が通れば、`list`を採用する（`name`の形は`論理名 (スキーマ名.物理名)`のまま）。4で重いなら、件数の上限を設ける。
- `template`の8・9が通れば、数千テーブルの大きなDBは`template`を併用する。9が通らない（Claude Code）場合は、`list`だけにする。
- どちらも通らない場合は、resourcesは見送る（M7の残りはTier 3のまま）。

## 実機の結果（途中経過）

| 確認 | Claude Code（ターミナル） | Claude Code（VS Code拡張） |
|---|---|---|
| `@`の一覧にテーブルが出る（表の1・2） | 出る。`@order_det`で`etd-list:exporttable://testdb…`の候補が並ぶ | 出ない（`@etd-list:order_det`で「No files found」） |
| 一覧取得ツールの出力量（表の6） | — | 3,000件・681.5KBは全文をファイルに保存し、先頭2KBだけがAIの文脈に入る。AIはPythonで件数を数えて答えた |
| 完全なURIを手で打って送信（表の5） | 未確認 | AIが`ReadMcpResourceTool`で読んで答えた（自動添付ではなくツール経由に見える） |

- ターミナル版の候補は、URIが途中で切れ、表示は`name`より`description`が優先される。初版は説明が`testdb / table`だけで
  どのテーブルか分からなかったため、説明の先頭に`論理名 (スキーマ名.物理名)`を入れた。

## サーバー側の測定（3,000テーブル・6スキーマ）

stdioで直接呼んだ結果（クライアントの描画は含まない）。クラウドの開発用コンテナでの1回の測定で、目安として読む。

| 項目 | 結果 |
|---|---|
| JVM起動〜初期化 | 約1.0秒（スナップショットの読み込みを含む） |
| `resources/list` | 68ミリ秒・3,000件・約623KB。1ページで返す（`nextCursor`なし） |
| `resources/read` | 44ミリ秒（初回）・約1.3KB。存在しないURIはエラーコード-32002（Resource not found） |
| `completion/complete` | 5〜25ミリ秒。100件を超える場合は`total`と`hasMore`を返す |
| 応答を待たずに続けて60件 | すべて174ミリ秒で応答（止まらない） |

クライアント側の確認項目（上の表の6）は、`resources/list`の約623KBが一覧の取得ツールの結果としてAIに渡る場合の量が気になる。

## 検証前に分かっていること

- Claude Code 2.1.296のコードを読んだ範囲では、`@`の絞り込み（Fuse.js、`threshold` 0.6）の対象は`name`（重み3）・`displayText`
  （`サーバー名:URI`。重み2）・`uriTemplate`（重み2）・`description`・`server`（重み1）で、`title`は対象外。論理名を`name`に入れたのはそのため。
- 同じ版では、`@サーバー名:`に続けてテンプレートを選ぶと変数の補完を呼ぶ。一方、送信時の添付は、`resources/list`にあるURIだけを
  解決しているように読める（コードの読解のみ。実機で確かめる）。
- `immediateExecution(true)`は、リソースの読み出しと補完の変換にも同じ引数が渡される（SDKのバイトコードで確認）。
  並行呼び出しの回帰は、ツールのテストと同じ仕組みで防げる見込み。
