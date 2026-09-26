# log_employee_change

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/26|

## 定義

```sql
CREATE OR REPLACE FUNCTION sample.log_employee_change()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
begin
    insert into sample.audit_log(table_name, record_id, action, changed_by)
    values ('employee', coalesce(new.employee_id, old.employee_id), tg_op, current_user);
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$function$

```

___

[関数・プロシージャ一覧へ](../../../functionList_testdb.md)
