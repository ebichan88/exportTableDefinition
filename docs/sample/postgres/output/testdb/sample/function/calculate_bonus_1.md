# calculate_bonus

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/25|

## 定義

```sql
CREATE OR REPLACE FUNCTION sample.calculate_bonus(p_salary numeric, p_rate numeric)
 RETURNS numeric
 LANGUAGE sql
 IMMUTABLE
AS $function$
    select p_salary * p_rate;
$function$

```

___

[関数・プロシージャ一覧へ](../../../functionList_testdb.md)
