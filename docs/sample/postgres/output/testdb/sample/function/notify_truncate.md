# notify_truncate

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/26|

## 定義

```sql
CREATE OR REPLACE FUNCTION sample.notify_truncate()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
    raise notice 'sample.project_assignment was truncated';
    return null;
end;
$function$

```

___

[関数・プロシージャ一覧へ](../../../functionList_testdb.md)
