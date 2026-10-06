# MCPサーバー（mcp-server）

cliが出力したスキーマのスナップショット（JSON Lines）を、AIからMCP（Model Context Protocol）のツールで検索できるようにする
サブプロジェクト（`mcp-server/`）。DBには接続せず、スナップショットのファイルだけを読む。
別のリポジトリでコードを書くAIが、テーブル定義・JOINの条件（外部キーと論理リレーション）を引けるようにすることが目的。

cliのアーキテクチャ（[overview.md](./overview.md)）とは独立している。cliのコードには依存せず、両者の接点はスナップショットの形式だけ。

初めてコードを読む人向けに、MCPとMCP Java SDKの事前知識、起動から1回のツール呼び出しまでの流れ、ソースごとの役割と繋がりを
図でまとめた読解ガイド（[mcp-server-guide.html](./mcp-server-guide.html)。ブラウザで開く）がある。ガイドはある時点のコードを読んで
書いたもので、クラス名・行数等が食い違う場合はこの文書とコードを正とする。

## 構成と依存の向き

依存の向きは`tool → catalog ← snapshot`・`catalog ← insight`。検索・関連のたどりは`catalog`に置き、MCPのSDKにもJSONにも依存させない
（cliの`presentation → application → domain ← infrastructure`と同じ考え方で、中心のロジックを単体テストしやすくする）。
`snapshot`と`insight`は互いに依存しない（読み込み元のディレクトリが異なる、独立した入力源）。

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `mcp`（直下） | `McpServerMain` | エントリーポイント。起動引数の検証とスナップショット・参考情報の読み込みの後、stdioのトランスポートでサーバーを起動する |
| | `ServerArguments` | 起動引数（`--snapshot=<ディレクトリ>`）の解釈と検証 |
| | `UserCorrectableException` | 利用者が起動引数・スナップショットを見直せば解消する失敗。起動時に標準エラーへ出して終了コード2で終了する |
| `mcp.catalog` | `SchemaCatalog` | 全オブジェクトを保持し、検索・一覧・名前の解決・カラムの逆引き・オブジェクト間の相互参照・関連のたどりを担う。観点（`ViewpointEntry`）は`withViewpoints`で組み立て後に合成する |
| | `SearchQuery` | テーブルの検索語。空白区切りのAND、NFKC正規化＋小文字化した部分一致、項目ごとの点数（テーブル名＞論理名＞カラム＞説明・備考） |
| | `ColumnQuery`・`ColumnHit` | カラムの逆引きの条件（物理名・論理名の完全一致／部分一致）と、当てはまったカラム（参照先を含む） |
| | `ObjectReference`・`Lookup` | 名前で指定されたオブジェクト（`スキーマ名.名前`も可）と、その解決結果（1つに定まる・複数ある・見つからない）。テーブル以外の種類にも共通で使う |
| | `RelationGraph` | テーブルを頂点、関連（外部キー・論理リレーション）を辺とするグラフ。被参照側の逆引きの索引を持ち、関連のたどりと最短のJOIN経路の探索を担う |
| | `Relation`・`RelatedTables`・`JoinPath`・`JoinPaths` | テーブル間の関連と、幅優先でたどった結果・2つのテーブルをつなぐ最短経路 |
| | `SqlNames`・`TableColumn` | カラムの型・デフォルト値（`nextval`）・トリガーの関数名に現れるオブジェクトの名前の判定（相互参照に使う）と、カラムとそれを持つテーブル |
| | `TableEntry`・`ColumnEntry`・`RelationEntry`・`TriggerEntry`・`ObjectKey`・`DatabaseEntry` | スナップショットの1行のうち、検索・一覧・逆引き・関連のたどりに使う項目 |
| | `ViewpointEntry` | 観点の参考情報1件（識別子・表示名・説明・所属テーブル）。スキーマを持たないため`SchemaObject`は実装しない。テーブルから所属する観点は`SchemaCatalog#viewpointsOf`で逆引きする（読み込み済みの観点を引くだけで、索引は持たない） |
| | `FunctionEntry`・`FunctionOverloads`・`SequenceEntry`・`TypeEntry` | 関数・シーケンス・ユーザー定義型の1行。関数は同名のもの（オーバーロード）を`FunctionOverloads`にまとめて名前の解決の単位にする |
| | `NameFilter` | 関数・シーケンス・型の一覧を、名前の部分一致で絞り込む条件 |
| | `TableFilter`・`TableType` | テーブルの一覧・検索の絞り込み（DB・スキーマ・区分・観点）を1回組み立てて`matches`で問い合わせる値オブジェクトと、テーブルの区分（table/view/materialized_view）。区分の値はツールの入力スキーマの`enum`にも使う |
| | `SchemaSummary` | スキーマごとのオブジェクトの数（`list_schemas`の元） |
| `mcp.snapshot` | `SnapshotDirectoryReader` | スナップショットのディレクトリ（`tables.jsonl`・`functions.jsonl`・`sequences.jsonl`・`types.jsonl`）を読み込み`SchemaCatalog`を組み立てる。未知の項目は無視し、無いファイルは0件とする（cliの`target.objects`で外せるため） |
| `mcp.insight` | `InsightsDirectoryReader` | 参考情報のディレクトリ（`{DB名}/viewpoints.json`）を読み込み`ViewpointEntry`のリストを組み立てる。渡されたスナップショットのディレクトリの親の兄弟を自前で求めるため、起動引数は増えない。ディレクトリ・ファイルが無い場合は0件とする |
| `mcp.tool` | `TableDefinitionTools` | MCPサーバーへ登録するツールの一覧。ツールは関心ごとのクラス（`SchemaTools`・`ViewpointTools`・`TableTools`・`RelationTools`・`FunctionTools`・`SequenceTools`・`TypeTools`・`TriggerTools`）に分けて定義する |
| | `ToolSpecifications`・`ToolResults`・`ObjectResolver`・`Page` | ツールの定義の組み立て、結果のJSON化、名前の解決とエラーの文言、一覧の範囲（`offset`・`limit`と、一覧の件数の既定値・上限） |
| | `TableOutputBuilder` | `get_table`の1テーブル分の結果の組み立て（スナップショットの1行に、`sections`・`columns`の絞り込みと所属する観点を反映する） |
| | `ToolArguments` | ツールの引数の読み取りと、入力スキーマで表せない検証（型・範囲・`enum`・未知の引数はSDKが入力スキーマで検証するため、選択肢は完全一致で照合する）。誤りは`InvalidToolArgumentException`としてツールのエラー（`isError`）で返す |

## ツール

| ツール | 主な引数 | 返すもの |
|---|---|---|
| `list_schemas` | なし | DB（DBMS種別）ごとのスキーマと、スキーマごとのオブジェクトの数 |
| `list_viewpoints` | `database`（任意） | 観点（業務ドメイン別にテーブルをまとめる切り口。参考情報）の識別子・表示名・説明・所属テーブル数 |
| `search_tables` | `query`、`schema`・`database`・`limit`・`viewpoint`（任意） | 一致したテーブルの概要（名前・論理名・区分・説明）と、一致した項目（`matchedIn`）。`viewpoint`（観点のid）を指定すると所属テーブルだけに絞り込む |
| `list_tables` | `schema`・`database`・`type`・`includeDescription`・`limit`・`offset`・`viewpoint`（任意） | テーブルの概要（名前・論理名・区分）の一覧。`viewpoint`で観点の所属テーブルだけに絞り込める |
| `get_table` | `table`（配列も可。最大10件）、`schema`・`database`・`sections`・`columns`（任意） | スナップショットの1行（cliが項目を追加すれば、そのまま返る）に、テーブルが所属する観点（`viewpoints`。`id`・`name`を宣言順）を加えたもの。`sections`・`columns`で項目・カラムを絞れる（`viewpoints`は絞り込みの対象外で、所属する観点があれば常に返す）。`table`に配列を指定すると`{"tables":[...]}`でまとめて返す（`columns`は1件指定時のみ使える） |
| `find_columns` | `column`、`match`・`schema`・`database`・`limit`・`offset`（任意） | 当てはまったカラム（テーブル・型・PK・NOT NULL・デフォルト値・参照先） |
| `get_related_tables` | `table`、`depth`（1〜3）・`direction`（outgoing/incoming/both）等（任意） | 関連（どのカラム同士か・外部キーか論理リレーションか・多重度・段数）と、関連に現れたテーブルの概要 |
| `find_join_path` | `from`・`to`、`maxLength`（1〜6）・`limit`等（任意） | 2つのテーブルをつなぐ最短の経路（たどる順のテーブルと、各段の関連）。同じ長さの経路はすべて（`limit`まで）返す |
| `list_functions`・`get_function` | `query`等（任意）／`function`、`includeDefinition`（任意） | 関数・プロシージャのシグネチャ（種別・引数・戻り値・言語）。`get_function`はオーバーロードをまとめ、関数を実行するトリガーも返す。`includeDefinition`指定時は定義本体も返す（オーバーロードの本体が同じ場合は1つにまとめ、長い場合は切り詰める） |
| `list_sequences`・`get_sequence` | `query`等（任意）／`sequence` | シーケンスの名前・所有カラム／スナップショットの1行と、採番に使うカラム（`usedByColumns`） |
| `list_types`・`get_type` | `query`・`category`等（任意）／`type` | ユーザー定義型の名前・種別／スナップショットの1行（ENUMの値の一覧等）と、型を使うカラム（`usedByColumns`） |
| `list_triggers` | `schema`・`database`等（任意） | テーブルをまたいだトリガーの一覧（テーブル・タイミング・イベント・実行単位・関数） |

- 結果はJSONの文字列で返す。値が無い項目（空文字・空リスト）は出力しない。
- テーブル名は大文字小文字を区別しない。同名のテーブルが複数ある・見つからない場合は、候補を示すツールのエラーを返し、
  AIが引数を直して呼び直せるようにする。
- 未知の引数・範囲外の値は、既定値へ黙って置き換えずにツールのエラーにする（cliの入力の検証と同じ方針）。
  型・範囲・`enum`・未知の引数は、ハンドラを呼ぶ前にSDKが入力スキーマ（JSON Schema）で検証し、英語の文言でツールのエラー（`isError`）を返す
  （`validateToolInputs`の既定）。これを正とし、`ToolArguments`は入力スキーマで表せない検証（空白の除去後の空・件数の上限・選択肢の照合・
  名前の解決等）だけを行う。数字の文字列を整数として読む等の寛容な読み取りはしない（入力スキーマの型に合わない値はSDKが拒否するため）。
  `get_table`の`columns`にテーブルに無いカラムを指定した場合も、カラムの一覧を示すエラーにする。
- 一覧を返すツールは`limit`・`offset`で範囲を指定し、続きがあれば`nextOffset`を返す。AIのコンテキストを圧迫しないよう、
  件数の上限を設ける。
- 関数・プロシージャの定義本体はAIのコンテキストを圧迫するため、既定では返さない（`get_function`の`includeDefinition`で返す）。
- オブジェクト間の相互参照（関数↔トリガー、シーケンス・型↔カラム）はスナップショットに無いため、
  呼び出しのたびに求める。名前の比較は大文字小文字を区別せず、スキーマ修飾の無い名前は参照元と同じスキーマとみなす。
- ツールの使い分け（どれから呼ぶか）は、初期化時にサーバーからAIへ渡す説明（`instructions`）に書く。
- `get_table`の`table`のように入力スキーマの型を文字列・配列のどちらも許す引数（`stringOrArrayProperty`）は、JSONの配列のほか
  カンマ区切りの文字列も受け付ける（配列を文字列にして渡すMCPクライアントがあるため）。型が配列だけの引数（`sections`・`columns`）に
  文字列を渡すと、SDKが入力スキーマの検証で拒否する。単一の文字列（カンマを含まない）は1件の配列として読む。
- 全ツールに`title`（短い日本語の表示名）を付ける。MCPクライアントのツール一覧・許可の確認画面での表示に使われる。

## stdioでの動作

- 標準出力はMCPのプロトコル専用。利用者向けの表示（読み込んだテーブル数・起動時の誤り）は標準エラーへ出す。
  SDKのログ（SLF4J）も`slf4j-simple`で標準エラーへ出す（WARN以上）。
- 標準エラーはロケールに関わらずUTF-8で出す（MCPクライアントがログとして取り込むため）。
- スナップショットは起動時に全件読み込む。更新した場合はサーバーを再起動する（MCPクライアントから再接続する）。
- 起動時の誤り（`--snapshot`の誤り、ディレクトリ・`database.json`が無い、対応していない`formatVersion`、
  JSONとして読めない行）は、何を直せばよいか（ファイル名・行番号を含む）を標準エラーへ出し、終了コード2で終了する。
- ツールの呼び出しは、標準入力を読むスレッドで1つずつ直列に実行する（`McpServer.sync(...)`の`immediateExecution(true)`）。
  MCP Java SDK（`mcp-core:2.0.1`）の既定（ツールをboundedElasticの複数スレッドに並行実行）は、応答の書き込み先への同時書き込みで
  応答が止まる不具合があり（[java-sdk#686](https://github.com/modelcontextprotocol/java-sdk/issues/686)。修正はSDKのmainに入ったが
  未リリース）、MCPクライアントは独立したツールを並行に呼ぶため日常的に起こる。ツールはメモリ上の検索だけで速いため、直列実行による
  実用上の影響は小さい。SDKの修正入りの版がリリースされたら、`immediateExecution`を残すかを判断する。

## 配布

配布用zip（`.github/workflows/release.yml`）の`mcp/exportTableDefinition-mcp.jar`として、cliと同じzipに同梱する。

- Java実行環境（`runtime`）はcliと共有する。jlinkに含めるモジュールは、cliとMCPサーバーの両方のjarを`jdeps`に渡して合算する。
- jarはzipの直下ではなく`mcp/`に置く。cliの起動スクリプト（`run.bat`/`run.sh`）は直下の`*.jar`を起動するため。
- MCPクライアントからは同梱のjavaを直接起動してもらう（起動スクリプトは`pause`や画面表示をするためMCPには使えない）。
  利用手順はREADMEの「AIからテーブル定義を調べる（MCPサーバー）」に書く。

## スナップショット形式との互換

mcp-serverはcliのスナップショットのrecordを共有せず、読み込み用の型を自前で持つ（cliのrecordがエンティティに依存しているため）。
形式のずれは次の仕組みで検知する。

- `SampleSnapshotContractTest`が、cliのベースライン（`docs/sample/postgres/output/snapshot`）を読み込んで検索・関連のたどりを確かめる。
  cliの出力形式を変えるとベースラインを出力し直すため、読み込み側の追従漏れがこのテストで分かる。
- 項目の追加は、未知の項目を無視するため互換。互換性の無い変更をした場合はcliの`DatabaseSnapshot.FORMAT_VERSION`を上げ、
  mcp-serverの`SnapshotDirectoryReader.SUPPORTED_FORMAT_VERSION`を追従させる。

## 参考情報（insights）の読み込み

参考情報（観点等。cliの[参考情報（insights）](./overview.md#参考情報insights)を参照）はスナップショットとは別の
ディレクトリに置かれるが、起動引数は`--snapshot`のみで増やさない。

- `InsightsDirectoryReader`が、渡された`--snapshot`のディレクトリの**親の兄弟**（`{親}/insights/`）を自前で求めて読む。
  cliの出力先（`output.path`）配下で`snapshot/`と`insights/`が常に兄弟になる配置規則に依存する
- `McpServerMain`が`SnapshotDirectoryReader`で組み立てた`SchemaCatalog`に、`withViewpoints`で観点を合成する
- ディレクトリ・DBごとのファイルが無い場合は0件とする（観点を1つも宣言していない場合、cliの版が古く参考情報を
  まだ出力しない場合等）。起動時の誤りにはしない（スナップショットが1つも無い場合とは扱いが異なる）
- 互換性はスナップショットと同じ仕組み（`SampleInsightsContractTest`によるベースラインの確認、
  cliの`ViewpointsInsight.FORMAT_VERSION`とmcp-serverの`InsightsDirectoryReader.SUPPORTED_FORMAT_VERSION`の追従）に乗せる

## テスト

| テスト | 対象 |
|---|---|
| `catalog`配下 | 検索の順位・AND・正規化、一覧、カラムの逆引き、名前の解決、相互参照、関連のたどり（向き・段数・自己参照・スナップショットに無い参照先）、JOIN経路の探索、観点の一覧・解決・テーブルの絞り込み・テーブルからの逆引き |
| `SnapshotDirectoryReaderTest` | 読み込みと、起動時の誤り（ファイル名・行番号を含むメッセージ） |
| `SampleSnapshotContractTest` | ベースラインとの契約（上記） |
| `InsightsDirectoryReaderTest` | 読み込み（兄弟ディレクトリの解決を含む）と、起動時の誤り |
| `SampleInsightsContractTest` | ベースラインとの契約（観点の所属テーブルを含む。スナップショットのテーブルから所属する観点を逆引きできること＝両者のキーが一致すること） |
| `tool`配下 | ツールの結果のJSON・エラー・引数の検証 |
| `McpServerProcessTest` | 配布するjar（shadowJar）を子プロセスで起動し、MCPクライアントからstdioで呼び出すE2E。マニフェスト・依存の同梱（ServiceLoaderの登録を含む）・標準出力の汚れを確かめる。応答を待たずに続けてツールを呼び出しても止まらないこと（`immediateExecution`の回帰）、型・範囲・未知の引数がSDKの入力スキーマの検証で拒否されること（ツールのテストはハンドラを直接呼ぶため通らない）も確かめる |

`catalog`には単体テストのカバレッジの最低基準（line 95%・branch 85%）を設け、`./gradlew build`で検査する。
検索・関連のたどりのルールを持つ中心のロジックのため（cliのドメイン層と同じ扱い）。E2Eテストは子プロセスで動くため計測の対象外。
