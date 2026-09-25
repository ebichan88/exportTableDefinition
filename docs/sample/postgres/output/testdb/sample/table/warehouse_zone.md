# warehouse_zone

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/25|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|sample||warehouse_zone|table||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||warehouse_code|character varying(5)|5|○|○| ||
|2||zone_code|character varying(5)|5|○|○| ||
|3||zone_name|character varying(50)|50||○| ||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|warehouse_zone_pkey|btree|○|○|CREATE UNIQUE INDEX warehouse_zone_pkey ON sample.warehouse_zone USING btree (warehouse_code, zone_code)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|warehouse_zone_pkey|PRIMARY KEY|PRIMARY KEY (warehouse_code, zone_code)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
|:---|:---|:---|:---|:---|:---|


## トリガー情報

| No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
|:---|:---|:---|:---|:---|:---|


## ER図

```mermaid
erDiagram
    sample_warehouse_zone ||--o{ sample_shipment : "shipment_warehouse_code_zone_code_fkey"
    sample_warehouse_zone {
        character_varying warehouse_code PK
        character_varying zone_code PK
        character_varying zone_name
    }
```

___

[テーブル一覧へ](../../../tableList_testdb.md)
