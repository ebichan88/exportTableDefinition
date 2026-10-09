# set_updated_at

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL 16|testdb|2026/10/08|

## 定義

```sql
CREATE OR REPLACE FUNCTION sample.set_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
    new.updated_at := now();
    return new;
end;
$function$

```

___

[関数・プロシージャ一覧へ](../../functionList_testdb.md)
