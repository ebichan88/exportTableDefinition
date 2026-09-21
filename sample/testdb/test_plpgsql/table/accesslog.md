# accesslog（アクセスログ）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/21|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|test_plpgsql|アクセスログ|accesslog|table||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1|ID|id|numeric(10,0)|10|○|○| ||
|2|ユーザID|userid|character varying(10)|10|○|○| ||
|3|取引コード|opecode|character varying(10)|10|○|○| ||
|4|取引ステータス|opests|numeric(1,0)|1|○|○| ||
|5|ログメッセージ|logmsg|character varying(256)|256||| ||
|6|アクセス日時|accesstime|timestamp(6) without time zone|||| ||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|accesslog_pkey|btree|○|○|CREATE UNIQUE INDEX accesslog_pkey ON test_plpgsql.accesslog USING btree (id, userid, opecode, opests)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|accesslog_pkey|PRIMARY KEY|PRIMARY KEY (id, userid, opecode, opests)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト |
|:---|:---|:---|:---|:---|


## ER図

関連するテーブルはありません。

___

[テーブル一覧へ](../../../tableList_testdb.md)
