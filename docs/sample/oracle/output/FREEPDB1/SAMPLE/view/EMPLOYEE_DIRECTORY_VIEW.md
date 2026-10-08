# EMPLOYEE_DIRECTORY_VIEW

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|Oracle 23|FREEPDB1|2026/10/08|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|SAMPLE||EMPLOYEE_DIRECTORY_VIEW|view||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||EMPLOYEE_ID|NUMBER(10,0)|10||○|||
|2||EMPLOYEE_CODE|VARCHAR2(10)|10||○|||
|3||EMPLOYEE_NAME|VARCHAR2(50)|50||○|||
|4||DEPARTMENT_NAME|VARCHAR2(50)|50||○|||
|5||STATUS|VARCHAR2(10)|10||○|||

## ソース

```sql

select
        e.employee_id,
        e.employee_code,
        e.employee_name,
        d.department_name,
        e.status
    from sample.employee e
    join sample.department d on e.department_id = d.department_id

```

## 参照するテーブル

| No. | 参照先 | 区分 |
|:---|:---|:---|
|1|SAMPLE.DEPARTMENT|table|
|2|SAMPLE.EMPLOYEE|table|

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|


## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|


## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
|:---|:---|:---|:---|:---|:---|


## 参照しているビュー

| No. | 参照元 | 区分 |
|:---|:---|:---|
|1|SAMPLE.ATTENDANCE_MONTHLY_VIEW|view|

## トリガー情報

| No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
|:---|:---|:---|:---|:---|:---|
|1|TRG_EMPLOYEE_DIRECTORY_INSERT|INSTEAD OF|INSERT|ROW|CREATE OR REPLACE TRIGGER sample.trg_employee_directory_insert instead of insert on sample.employee_directory_view for each row|

## ER図

関連するテーブルはありません。

___

[テーブル一覧へ](../../tableList_FREEPDB1.md)
