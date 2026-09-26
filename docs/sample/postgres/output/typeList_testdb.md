# ユーザー定義型一覧（DB名：testdb）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/26|

## ユーザー定義型一覧

| No. | スキーマ名 | 型名 | 種別 | 定義 | Link |
|:---|:---|:---|:---|:---|:---|
|1|sample|address_type|COMPOSITE|street character varying(100), city character varying(50), postal_code character varying(10)|[■](./testdb/sample/type/address_type.md)|
|2|sample|employee_status_enum|ENUM|ACTIVE, ON_LEAVE, RETIRED|[■](./testdb/sample/type/employee_status_enum.md)|
|3|sample|positive_numeric|DOMAIN|DOMAIN OVER numeric(12,2)|[■](./testdb/sample/type/positive_numeric.md)|

___

[テーブル一覧へ](./tableList_testdb.md)
