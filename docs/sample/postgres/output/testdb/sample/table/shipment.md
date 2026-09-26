# shipment

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/26|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|sample||shipment|table||

## 所属する観点

* [物流](../../../viewpoint_testdb_logistics.md)  

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||shipment_id|integer||○|○|nextval('sample.shipment_shipment_id_seq'::regclass)||
|2||warehouse_code|character varying(5)|5||○|||
|3||zone_code|character varying(5)|5||○|||
|4||shipped_at|timestamp without time zone|||○|now()||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|shipment_pkey|btree|○|○|CREATE UNIQUE INDEX shipment_pkey ON sample.shipment USING btree (shipment_id)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|shipment_warehouse_code_zone_code_fkey|FOREIGN KEY|FOREIGN KEY (warehouse_code, zone_code) REFERENCES sample.warehouse_zone(warehouse_code, zone_code)||
|2|shipment_pkey|PRIMARY KEY|PRIMARY KEY (shipment_id)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
|:---|:---|:---|:---|:---|:---|
|1|shipment_warehouse_code_zone_code_fkey|warehouse_code,zone_code|sample.warehouse_zone|warehouse_code,zone_code|1対多|

## トリガー情報

| No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
|:---|:---|:---|:---|:---|:---|


## ER図

```mermaid
erDiagram
    sample_warehouse_zone ||--o{ sample_shipment : "shipment_warehouse_code_zone_code_fkey"
    sample_shipment {
        integer shipment_id PK
        character_varying warehouse_code
        character_varying zone_code
        timestamp_without_time_zone shipped_at
    }
```

___

[テーブル一覧へ](../../../tableList_testdb.md)
