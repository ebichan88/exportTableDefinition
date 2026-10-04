# PARKING_SPOT

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|Oracle|FREEPDB1|2026/10/04|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|SAMPLE||PARKING_SPOT|table||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||PARKING_SPOT_ID|NUMBER(10,0)|10|○|○|||
|2||SPOT_CODE|VARCHAR2(10)|10||○|||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|PARKING_SPOT_PKEY|NORMAL|○|○|CREATE UNIQUE INDEX PARKING_SPOT_PKEY ON PARKING_SPOT (PARKING_SPOT_ID)||
|2|PARKING_SPOT_SPOT_CODE_KEY|NORMAL|○||CREATE UNIQUE INDEX PARKING_SPOT_SPOT_CODE_KEY ON PARKING_SPOT (SPOT_CODE)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|PARKING_SPOT_PKEY|PRIMARY KEY|PRIMARY KEY (PARKING_SPOT_ID)||
|2|PARKING_SPOT_SPOT_CODE_KEY|UNIQUE|UNIQUE (SPOT_CODE)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
|:---|:---|:---|:---|:---|:---|


## トリガー情報

| No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
|:---|:---|:---|:---|:---|:---|


## ER図

```mermaid
erDiagram
    SAMPLE_PARKING_SPOT["PARKING_SPOT"]
    SAMPLE_EMPLOYEE["EMPLOYEE（従業員）"]
    SAMPLE_PARKING_SPOT |o--o| SAMPLE_EMPLOYEE : "EMPLOYEE_PARKING_SPOT_ID_FKEY"
    SAMPLE_PARKING_SPOT {
        NUMBER PARKING_SPOT_ID PK
        VARCHAR2 SPOT_CODE
    }
    SAMPLE_EMPLOYEE {
        NUMBER PARKING_SPOT_ID FK "駐車場ID"
    }
```

___

[テーブル一覧へ](../../tableList_FREEPDB1.md)
