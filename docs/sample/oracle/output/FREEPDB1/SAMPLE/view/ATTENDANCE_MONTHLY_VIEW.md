# ATTENDANCE_MONTHLY_VIEW（部署別の月次勤務時間）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|Oracle 23|FREEPDB1|2026/10/08|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|SAMPLE|部署別の月次勤務時間|ATTENDANCE_MONTHLY_VIEW|view||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||DEPARTMENT_NAME|VARCHAR2(50)|50||○|||
|2||WORK_MONTH|DATE||||||
|3||TOTAL_MINUTES|NUMBER||||||
|4||ARCHIVE_NOTE|VARCHAR2(50)|50|||||

## ソース

```sql

select
        dv.department_name,
        trunc(a.work_date, 'MM') as work_month,
        sum(a.work_minutes) as total_minutes,
        max(ad.note) as archive_note
    from sample.attendance a
    join sample.employee_directory_view dv on dv.employee_id = a.employee_id
    left join sample_archive.department ad on ad.note = dv.department_name
    group by dv.department_name, trunc(a.work_date, 'MM')

```

## 参照するテーブル

| No. | 参照先 | 区分 |
|:---|:---|:---|
|1|SAMPLE.ATTENDANCE|table|
|2|SAMPLE.EMPLOYEE_DIRECTORY_VIEW|view|
|3|SAMPLE_ARCHIVE.DEPARTMENT|table|

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

[テーブル一覧へ](../../tableList_FREEPDB1.md)
