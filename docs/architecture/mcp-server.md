# MCPサーバー（mcp-server）

cliが出力したスキーマのスナップショット（JSON Lines）を、AIからMCP（Model Context Protocol）のツールで検索できるようにする
サブプロジェクト（`mcp-server/`）。DBには接続せず、スナップショットのファイルだけを読む。
別のリポジトリでコードを書くAIが、テーブル定義・JOINの条件（外部キーと論理リレーション）を引けるようにすることが目的。

cliのアーキテクチャ（[overview.md](./overview.md)）とは独立している。cliのコードには依存せず、両者の接点はスナップショットの形式だけ。

## 構成と依存の向き

依存の向きは`tool → catalog ← snapshot`。検索・関連のたどりは`catalog`に置き、MCPのSDKにもJSONにも依存させない
（cliの`presentation → application → domain ← infrastructure`と同じ考え方で、中心のロジックを単体テストしやすくする）。

| パッケージ | 主要クラス | 役割 |
|---|---|---|
| `mcp`（直下） | `McpServerMain` | エントリーポイント。起動引数の検証とスナップショットの読み込みの後、stdioのトランスポートでサーバーを起動する |
| | `ServerArguments` | 起動引数（`--snapshot=<ディレクトリ>`）の解釈と検証 |
| | `UserCorrectableException` | 利用者が起動引数・スナップショットを見直せば解消する失敗。起動時に標準エラーへ出して終了コード2で終了する |
| `mcp.catalog` | `SchemaCatalog` | 全オブジェクトを保持し、検索・一覧・名前の解決・カラムの逆引き・オブジェクト間の相互参照・関連のたどりを担う |
| | `SearchQuery` | テーブルの検索語。空白区切りのAND、NFKC正規化＋小文字化した部分一致、項目ごとの点数（テーブル名＞論理名＞カラム＞説明・備考） |
| | `ColumnQuery`・`ColumnHit` | カラムの逆引きの条件（物理名・論理名の完全一致／部分一致）と、当てはまったカラム（参照先を含む） |
| | `ObjectReference`・`Lookup` | 名前で指定されたオブジェクト（`スキーマ名.名前`も可）と、その解決結果（1つに定まる・複数ある・見つからない）。テーブル以外の種類にも共通で使う |
| | `RelationGraph` | テーブルを頂点、関連（外部キー・論理リレーション）を辺とするグラフ。被参照側の逆引きの索引を持ち、関連のたどりと最短のJOIN経路の探索を担う |
| | `Relation`・`RelatedTables`・`JoinPath`・`JoinPaths` | テーブル間の関連と、幅優先でたどった結果・2つのテーブルをつなぐ最短経路 |
| | `SqlNames`・`TableColumn` | カラムの型・デフォルト値（`nextval`）・トリガーの関数名・関数の定義本体に現れるオブジェクトの名前の判定（相互参照に使う）と、カラムとそれを持つテーブル |
| | `TableEntry`・`ColumnEntry`・`RelationEntry`・`TriggerEntry`・`ObjectKey`・`DatabaseEntry` | スナップショットの1行のうち、検索・一覧・逆引き・関連のたどりに使う項目 |
| | `FunctionEntry`・`FunctionOverloads`・`SequenceEntry`・`TypeEntry` | 関数・シーケンス・ユーザー定義型の1行。関数は同名のもの（オーバーロード）を`FunctionOverloads`にまとめて名前の解決の単位にする |
| | `NameFilter` | 関数・シーケンス・型の一覧を、名前の部分一致で絞り込む条件 |
| | `SchemaSummary` | スキーマごとのオブジェクトの数（`list_schemas`の元） |
| `mcp.snapshot` | `SnapshotDirectoryReader` | スナップショットのディレクトリ（`tables.jsonl`・`functions.jsonl`・`sequences.jsonl`・`types.jsonl`）を読み込み`SchemaCatalog`を組み立てる。未知の項目は無視し、無いファイルは0件とする（cliの`outputObjects`で外せるため） |
| `mcp.tool` | `TableDefinitionTools` | MCPサーバーへ登録するツールの一覧。ツールは関心ごとのクラス（`SchemaTools`・`TableTools`・`RelationTools`・`FunctionTools`・`SequenceTools`・`TypeTools`・`TriggerTools`）に分けて定義する |
| | `ToolSpecifications`・`ToolResults`・`ObjectResolver`・`Page` | ツールの定義の組み立て、結果のJSON化、名前の解決とエラーの文言、一覧の範囲（`offset`・`limit`） |
| | `ToolArguments` | ツールの引数の読み取りと検証。誤りは`InvalidToolArgumentException`としてツールのエラー（`isError`）で返す |

## ツール

| ツール | 主な引数 | 返すもの |
|---|---|---|
| `list_schemas` | なし | DB（DBMS種別）ごとのスキーマと、スキーマごとのオブジェクトの数 |
| `search_tables` | `query`、`schema`・`database`・`limit`（任意） | 一致したテーブルの概要（名前・論理名・区分・説明）と、一致した項目（`matchedIn`） |
| `list_tables` | `schema`・`database`・`type`・`includeDescription`・`limit`・`offset`（任意） | テーブルの概要（名前・論理名・区分）の一覧 |
| `get_table` | `table`、`schema`・`database`・`sections`・`columns`（任意） | スナップショットの1行（cliが項目を追加すれば、そのまま返る）に、定義本体にテーブル名が現れる関数（`mentionedInFunctions`）を加えたもの。`sections`・`columns`で項目・カラムを絞れる |
| `find_columns` | `column`、`match`・`schema`・`database`・`limit`・`offset`（任意） | 当てはまったカラム（テーブル・型・PK・NOT NULL・デフォルト値・参照先） |
| `get_related_tables` | `table`、`depth`（1〜3）・`direction`（outgoing/incoming/both）等（任意） | 関連（どのカラム同士か・外部キーか論理リレーションか・多重度・段数）と、関連に現れたテーブルの概要 |
| `find_join_path` | `from`・`to`、`maxLength`（1〜6）・`limit`等（任意） | 2つのテーブルをつなぐ最短の経路（たどる順のテーブルと、各段の関連）。同じ長さの経路はすべて（`limit`まで）返す |
| `list_functions`・`get_function` | `query`等（任意）／`function` | 関数・プロシージャのシグネチャ（種別・引数・戻り値・言語）。`get_function`はオーバーロードをまとめ、関数を実行するトリガーも返す |
| `list_sequences`・`get_sequence` | `query`等（任意）／`sequence` | シーケンスの名前・所有カラム／スナップショットの1行と、採番に使うカラム（`usedByColumns`） |
| `list_types`・`get_type` | `query`・`category`等（任意）／`type` | ユーザー定義型の名前・種別／スナップショットの1行（ENUMの値の一覧等）と、型を使うカラム（`usedByColumns`） |
| `list_triggers` | `schema`・`database`等（任意） | テーブルをまたいだトリガーの一覧（テーブル・タイミング・イベント・実行単位・関数） |

- 結果はJSONの文字列で返す。値が無い項目（空文字・空リスト）は出力しない。
- テーブル名は大文字小文字を区別しない。同名のテーブルが複数ある・見つからない場合は、候補を示すツールのエラーを返し、
  AIが引数を直して呼び直せるようにする。
- 未知の引数・範囲外の値は、既定値へ黙って置き換えずにツールのエラーにする（cliの入力の検証と同じ方針）。
  `get_table`の`columns`にテーブルに無いカラムを指定した場合も、カラムの一覧を示すエラーにする。
- 一覧を返すツールは`limit`・`offset`で範囲を指定し、続きがあれば`nextOffset`を返す。AIのコンテキストを圧迫しないよう、
  件数の上限を設ける。
- 関数・プロシージャの定義本体はAIのコンテキストを圧迫するため、ツールの結果には出さない
  （読み込みはして、テーブル名が現れる関数の判定にだけ使う）。
- オブジェクト間の相互参照（関数↔トリガー、シーケンス・型↔カラム、テーブル↔関数の定義本体）はスナップショットに無いため、
  呼び出しのたびに求める。名前の比較は大文字小文字を区別せず、スキーマ修飾の無い名前は参照元と同じスキーマとみなす。
- ツールの使い分け（どれから呼ぶか）は、初期化時にサーバーからAIへ渡す説明（`instructions`）に書く。
- 文字列のリストの引数は、JSONの配列のほかカンマ区切りの文字列も受け付ける（配列を文字列にして渡すMCPクライアントがあるため）。

## stdioでの動作

- 標準出力はMCPのプロトコル専用。利用者向けの表示（読み込んだテーブル数・起動時の誤り）は標準エラーへ出す。
  SDKのログ（SLF4J）も`slf4j-simple`で標準エラーへ出す（WARN以上）。
- 標準エラーはロケールに関わらずUTF-8で出す（MCPクライアントがログとして取り込むため）。
- スナップショットは起動時に全件読み込む。更新した場合はサーバーを再起動する（MCPクライアントから再接続する）。
- 起動時の誤り（`--snapshot`の誤り、ディレクトリ・`database.json`が無い、対応していない`formatVersion`、
  JSONとして読めない行）は、何を直せばよいか（ファイル名・行番号を含む）を標準エラーへ出し、終了コード2で終了する。

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

## テスト

| テスト | 対象 |
|---|---|
| `catalog`配下 | 検索の順位・AND・正規化、一覧、カラムの逆引き、名前の解決、相互参照、関連のたどり（向き・段数・自己参照・スナップショットに無い参照先）、JOIN経路の探索 |
| `SnapshotDirectoryReaderTest` | 読み込みと、起動時の誤り（ファイル名・行番号を含むメッセージ） |
| `SampleSnapshotContractTest` | ベースラインとの契約（上記） |
| `tool`配下 | ツールの結果のJSON・エラー・引数の検証 |
| `McpServerProcessTest` | 配布するjar（shadowJar）を子プロセスで起動し、MCPクライアントからstdioで呼び出すE2E。マニフェスト・依存の同梱（ServiceLoaderの登録を含む）・標準出力の汚れを確かめる |
