# raise_salary

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/25|

## 定義

```sql
CREATE OR REPLACE PROCEDURE sample.raise_salary(IN p_employee_id integer, IN p_amount numeric)
 LANGUAGE plpgsql
AS $procedure$
begin
    update sample.employee set salary = salary + p_amount where employee_id = p_employee_id;
end;
$procedure$

```

___

[関数・プロシージャ一覧へ](../../../functionList_testdb.md)
