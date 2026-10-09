# attendance_monthly_view（部署別の月次勤務時間）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL 16|testdb|2026/10/08|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|sample|部署別の月次勤務時間|attendance_monthly_view|view||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||department_name|character varying(50)|50|||||
|2||work_month|date||||||
|3||total_minutes|bigint||||||
|4||archive_note|text||||||

## ソース

```sql

 SELECT dv.department_name,
    (date_trunc('month'::text, (a.work_date)::timestamp with time zone))::date AS work_month,
    sum(a.work_minutes) AS total_minutes,
    max((ad.note)::text) AS archive_note
   FROM ((sample_archive.attendance_2025 a
     JOIN sample.employee_directory_view dv ON ((dv.employee_id = a.employee_id)))
     LEFT JOIN sample_archive.department ad ON (((ad.note)::text = (dv.department_name)::text)))
  GROUP BY dv.department_name, (date_trunc('month'::text, (a.work_date)::timestamp with time zone));

```

## 参照するテーブル

| No. | 参照先 | 区分 |
|:---|:---|:---|
|1|sample.attendance|table|
|2|sample.employee_directory_view|view|
|3|sample_archive.department|table|

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


## ER図

関連するテーブルはありません。

___

[テーブル一覧へ](../../tableList_testdb.md)
