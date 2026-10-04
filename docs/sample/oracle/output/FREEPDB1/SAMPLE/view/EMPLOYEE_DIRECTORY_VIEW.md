# EMPLOYEE_DIRECTORY_VIEW

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|Oracle|FREEPDB1|2026/10/04|

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
