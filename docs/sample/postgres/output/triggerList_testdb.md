# トリガー一覧（DB名：testdb）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/26|

## トリガー一覧

| No. | スキーマ名 | テーブル名 | トリガー名 | タイミング | イベント | 実行関数 |
|:---|:---|:---|:---|:---|:---|:---|
|1|sample|employee|trg_employee_audit|AFTER|INSERT/DELETE/UPDATE|sample.log_employee_change|
|2|sample|employee|trg_employee_set_updated_at|BEFORE|UPDATE|sample.set_updated_at|
|3|sample|employee_directory_view|trg_employee_directory_insert|INSTEAD OF|INSERT|sample.employee_directory_insert|
|4|sample|project_assignment|trg_project_assignment_truncate|AFTER|TRUNCATE|sample.notify_truncate|

___

[テーブル一覧へ](./tableList_testdb.md)
