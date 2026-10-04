# check_work_minutes

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/10/04|

## 定義

```sql
CREATE OR REPLACE FUNCTION sample.check_work_minutes()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
    if new.work_minutes < 0 then
        raise exception 'work_minutes must not be negative';
    end if;
    return new;
end;
$function$

```

___

[関数・プロシージャ一覧へ](../../functionList_testdb.md)
