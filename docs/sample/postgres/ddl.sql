-- ============================================================================
-- exportTableDefinition サンプルDDL（PostgreSQL）
--
-- テーブル定義書の出力結果サンプルを作成するためのDDLです。
-- 出力対象となりうるオブジェクト種別（テーブル／ビュー／マテリアライズドビュー／
-- インデックス／制約／外部キー／シーケンス／トリガー／関数・プロシージャ／
-- ユーザー定義型（ENUM/COMPOSITE/DOMAIN））と、ER図の多重度パターン
-- （1対1・0..1対1・1対多・0..1対多・多対多・自己参照・複合外部キー・関連なし）
-- を一通り含めています。
--
-- 実行後、conf/ExportTableDefinition.properties の schema に "sample" を、
-- annotationPath に annotations.sample.yml のパスを指定して実行すると
-- 出力結果を確認できます。
-- ============================================================================

/*
 * スキーマ作成
 */
create schema sample;

/*
 * 型定義（ユーザー定義型を3種類網羅。RANGE型は作成時に構築関数が自動生成され
 * 関数一覧サンプルが冗長になるため、ここでは扱わない）
 */
-- ENUM型：employeeのstatus列で使用
create type sample.employee_status_enum as enum ('ACTIVE', 'ON_LEAVE', 'RETIRED');

-- COMPOSITE型：employee_profileのaddress列で使用
create type sample.address_type as (
    street varchar(100),
    city varchar(50),
    postal_code varchar(10)
);

-- DOMAIN型：projectのbudget列で使用
create domain sample.positive_numeric as numeric(12, 2) check (value >= 0);

/*
 * シーケンス
 */
-- どの列にも紐付かない独立したシーケンス（owned_byが空になるパターン）
create sequence sample.invoice_no_seq
    increment by 1
    minvalue 1000
    maxvalue 999999
    start with 1000
    cache 5
    cycle;

/*
 * DDL作成（テーブル）
 */
-- 親テーブル（コメントは付与せず、annotations.sample.yml側で論理名・備考を補う）
create table sample.department (
    department_id serial primary key,
    department_code varchar(10) not null unique,
    department_name varchar(50) not null
);

-- 単独テーブル（他のテーブルと参照関係を持たない）
create table sample.parking_spot (
    parking_spot_id serial primary key,
    spot_code varchar(10) not null unique
);

-- 1対多（必須）：department → employee
-- 0..1対多（任意・自己参照）：employee.manager_id → employee
-- 0..1対1（任意・一意）：parking_spot → employee.parking_spot_id
create table sample.employee (
    employee_id serial primary key,
    employee_code varchar(10) not null unique,
    employee_name varchar(50) not null,
    department_id integer not null references sample.department(department_id),
    manager_id integer references sample.employee(employee_id),
    parking_spot_id integer unique references sample.parking_spot(parking_spot_id),
    status sample.employee_status_enum not null default 'ACTIVE',
    salary numeric(10, 2) not null default 0 check (salary >= 0),
    profile jsonb not null default '{}'::jsonb,
    hired_date date not null default current_date,
    updated_at timestamp not null default now()
);

-- 1対1（必須）：employee ⇔ employee_profile（PKがそのままFK）
create table sample.employee_profile (
    employee_id integer primary key references sample.employee(employee_id),
    address sample.address_type,
    emergency_contact varchar(50),
    notes text
);

create table sample.project (
    project_id serial primary key,
    project_code varchar(10) not null unique,
    project_name varchar(100) not null,
    budget sample.positive_numeric
);

-- 多対多：project ⇔ employee（複合主キー＝2本の1対多外部キーの組み合わせ）
create table sample.project_assignment (
    project_id integer not null references sample.project(project_id),
    employee_id integer not null references sample.employee(employee_id),
    role varchar(30) not null default 'MEMBER',
    assigned_at date not null default current_date,
    primary key (project_id, employee_id)
);

create table sample.warehouse_zone (
    warehouse_code varchar(5) not null,
    zone_code varchar(5) not null,
    zone_name varchar(50) not null,
    primary key (warehouse_code, zone_code)
);

-- 1対多（必須・複合外部キー）：warehouse_zone → shipment
create table sample.shipment (
    shipment_id serial primary key,
    warehouse_code varchar(5) not null,
    zone_code varchar(5) not null,
    shipped_at timestamp not null default now(),
    foreign key (warehouse_code, zone_code) references sample.warehouse_zone(warehouse_code, zone_code)
);

-- 単独テーブル（関連なし。トリガーからの書き込み先）
create table sample.audit_log (
    log_id bigserial primary key,
    table_name varchar(50) not null,
    record_id integer,
    action varchar(10) not null check (action in ('INSERT', 'UPDATE', 'DELETE')),
    changed_at timestamp not null default now(),
    changed_by varchar(50)
);

/*
 * ビュー
 */
create view sample.employee_directory_view as
    select
        e.employee_id,
        e.employee_code,
        e.employee_name,
        d.department_name,
        e.status
    from sample.employee e
    join sample.department d on e.department_id = d.department_id;

/*
 * マテリアライズドビュー
 */
create materialized view sample.project_summary_mv as
    select
        p.project_id,
        p.project_name,
        count(pa.employee_id) as member_count
    from sample.project p
    left join sample.project_assignment pa on pa.project_id = p.project_id
    group by p.project_id, p.project_name;

/*
 * インデックス（btree以外の索引種別・複合索引・マテビューへの索引も含める）
 */
create index idx_employee_department_status on sample.employee (department_id, status);
create index idx_employee_profile_gin on sample.employee using gin (profile);
create unique index project_summary_mv_pkey on sample.project_summary_mv (project_id);

/*
 * トリガー用関数
 */
create or replace function sample.set_updated_at() returns trigger
language plpgsql as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

create or replace function sample.log_employee_change() returns trigger
language plpgsql as $$
begin
    insert into sample.audit_log(table_name, record_id, action, changed_by)
    values ('employee', coalesce(new.employee_id, old.employee_id), tg_op, current_user);
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

create or replace function sample.notify_truncate() returns trigger
language plpgsql as $$
begin
    raise notice 'sample.project_assignment was truncated';
    return null;
end;
$$;

create or replace function sample.employee_directory_insert() returns trigger
language plpgsql as $$
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
$$;

/*
 * トリガー（BEFORE / AFTER / INSTEAD OF、ROW / STATEMENT を一通り網羅）
 */
create trigger trg_employee_set_updated_at
    before update on sample.employee
    for each row execute function sample.set_updated_at();

create trigger trg_employee_audit
    after insert or update or delete on sample.employee
    for each row execute function sample.log_employee_change();

create trigger trg_project_assignment_truncate
    after truncate on sample.project_assignment
    for each statement execute function sample.notify_truncate();

create trigger trg_employee_directory_insert
    instead of insert on sample.employee_directory_view
    for each row execute function sample.employee_directory_insert();

/*
 * 関数・プロシージャ（オーバーロード関数、プロシージャを含める）
 */
create or replace function sample.calculate_bonus(p_salary numeric, p_rate numeric) returns numeric
language sql immutable as $$
    select p_salary * p_rate;
$$;

create or replace function sample.calculate_bonus(p_salary numeric) returns numeric
language sql immutable as $$
    select sample.calculate_bonus(p_salary, 0.1);
$$;

create or replace procedure sample.raise_salary(p_employee_id integer, p_amount numeric)
language plpgsql as $$
begin
    update sample.employee set salary = salary + p_amount where employee_id = p_employee_id;
end;
$$;

/*
 * コメント（論理名）
 * employeeとprojectはDBコメントで論理名を付与し、departmentは無コメントのまま
 * annotations.sample.yml側で説明・備考を補う対比になるようにしている
 */
comment on table sample.employee is '従業員';
comment on column sample.employee.employee_id is '従業員ID';
comment on column sample.employee.employee_code is '従業員コード';
comment on column sample.employee.employee_name is '従業員名';
comment on column sample.employee.department_id is '所属部署ID';
comment on column sample.employee.manager_id is '上長の従業員ID';
comment on column sample.employee.parking_spot_id is '駐車場ID';
comment on column sample.employee.status is '在籍状況';
comment on column sample.employee.salary is '給与';
comment on column sample.employee.profile is 'プロフィール（JSON）';
comment on column sample.employee.hired_date is '入社日';
comment on column sample.employee.updated_at is '更新日時';

comment on table sample.project is 'プロジェクト';
comment on column sample.project.project_id is 'プロジェクトID';
comment on column sample.project.project_code is 'プロジェクトコード';
comment on column sample.project.project_name is 'プロジェクト名';
comment on column sample.project.budget is '予算';

comment on materialized view sample.project_summary_mv is 'プロジェクト別要員数集計';
