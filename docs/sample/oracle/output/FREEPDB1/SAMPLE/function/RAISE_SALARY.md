# RAISE_SALARY

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|Oracle 23|FREEPDB1|2026/10/08|

## 定義

```sql
CREATE OR REPLACE procedure raise_salary(p_employee_id number, p_amount number) as
begin
    update sample.employee set salary = salary + p_amount where employee_id = p_employee_id;
end;
```

___

[関数・プロシージャ一覧へ](../../functionList_FREEPDB1.md)
