# project_assignment

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/26|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|sample||project_assignment|table||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||project_id|integer||○|○|||
|2||employee_id|integer||○|○|||
|3||role|character varying(30)|30||○|'MEMBER'::character varying||
|4||assigned_at|date|||○|CURRENT_DATE||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|project_assignment_pkey|btree|○|○|CREATE UNIQUE INDEX project_assignment_pkey ON sample.project_assignment USING btree (project_id, employee_id)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|project_assignment_employee_id_fkey|FOREIGN KEY|FOREIGN KEY (employee_id) REFERENCES sample.employee(employee_id)||
|2|project_assignment_project_id_fkey|FOREIGN KEY|FOREIGN KEY (project_id) REFERENCES sample.project(project_id)||
|3|project_assignment_pkey|PRIMARY KEY|PRIMARY KEY (project_id, employee_id)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
|:---|:---|:---|:---|:---|:---|
|1|project_assignment_employee_id_fkey|employee_id|sample.employee|employee_id|1対多|
|2|project_assignment_project_id_fkey|project_id|sample.project|project_id|1対多|

## トリガー情報

| No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
|:---|:---|:---|:---|:---|:---|
|1|trg_project_assignment_truncate|AFTER|TRUNCATE|STATEMENT|CREATE TRIGGER trg_project_assignment_truncate AFTER TRUNCATE ON sample.project_assignment FOR EACH STATEMENT EXECUTE FUNCTION sample.notify_truncate()|

## ER図

```mermaid
erDiagram
    sample_employee ||--o{ sample_project_assignment : "project_assignment_employee_id_fkey"
    sample_project ||--o{ sample_project_assignment : "project_assignment_project_id_fkey"
    sample_project_assignment {
        integer project_id PK
        integer employee_id PK
        character_varying role
        date assigned_at
    }
```

___

[テーブル一覧へ](../../../tableList_testdb.md)
