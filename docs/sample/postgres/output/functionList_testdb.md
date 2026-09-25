# 関数・プロシージャ一覧（DB名：testdb）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/25|

## 関数・プロシージャ一覧

| No. | スキーマ名 | 種別 | 名前 | 引数 | 戻り値 | 言語 | Link |
|:---|:---|:---|:---|:---|:---|:---|:---|
|1|sample|FUNCTION|calculate_bonus|p_salary numeric, p_rate numeric|numeric|sql|[■](./testdb/sample/function/calculate_bonus_1.md)|
|2|sample|FUNCTION|calculate_bonus|p_salary numeric|numeric|sql|[■](./testdb/sample/function/calculate_bonus_2.md)|
|3|sample|FUNCTION|employee_directory_insert||trigger|plpgsql|[■](./testdb/sample/function/employee_directory_insert.md)|
|4|sample|FUNCTION|log_employee_change||trigger|plpgsql|[■](./testdb/sample/function/log_employee_change.md)|
|5|sample|FUNCTION|notify_truncate||trigger|plpgsql|[■](./testdb/sample/function/notify_truncate.md)|
|6|sample|PROCEDURE|raise_salary|IN p_employee_id integer, IN p_amount numeric||plpgsql|[■](./testdb/sample/function/raise_salary.md)|
|7|sample|FUNCTION|set_updated_at||trigger|plpgsql|[■](./testdb/sample/function/set_updated_at.md)|

___

[テーブル一覧へ](./tableList_testdb.md)
