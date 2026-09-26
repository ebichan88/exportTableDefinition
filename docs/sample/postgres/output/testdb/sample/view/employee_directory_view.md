# employee_directory_view

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/26|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|sample||employee_directory_view|view||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||employee_id|integer||||||
|2||employee_code|character varying(10)|10|||||
|3||employee_name|character varying(50)|50|||||
|4||department_name|character varying(50)|50|||||
|5||status|sample.employee_status_enum||||||

## ソース

```sql

 SELECT e.employee_id,
    e.employee_code,
    e.employee_name,
    d.department_name,
    e.status
   FROM (sample.employee e
     JOIN sample.department d ON ((e.department_id = d.department_id)));

```

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|


## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|


## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
|:---|:---|:---|:---|:---|:---|


## トリガー情報

| No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
|:---|:---|:---|:---|:---|:---|
|1|trg_employee_directory_insert|INSTEAD OF|INSERT|ROW|CREATE TRIGGER trg_employee_directory_insert INSTEAD OF INSERT ON sample.employee_directory_view FOR EACH ROW EXECUTE FUNCTION sample.employee_directory_insert()|

## ER図

関連するテーブルはありません。

___

[テーブル一覧へ](../../../tableList_testdb.md)
