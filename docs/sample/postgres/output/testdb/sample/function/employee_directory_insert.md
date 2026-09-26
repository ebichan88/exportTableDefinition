# employee_directory_insert

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/26|

## 定義

```sql
CREATE OR REPLACE FUNCTION sample.employee_directory_insert()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
declare
    v_department_id integer;
begin
    select department_id into v_department_id
    from sample.department
    where department_name = new.department_name;

    insert into sample.employee(employee_code, employee_name, department_id)
    values (new.employee_code, new.employee_name, v_department_id);
    return new;
end;
$function$

```

___

[関数・プロシージャ一覧へ](../../../functionList_testdb.md)
