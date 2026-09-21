# myview

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/21|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|test_plpgsql||myview|view| |

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||id|numeric(10,0)|10||| ||
|2||userid|character varying(10)|10||| ||
|3||opecode|character varying(10)|10||| ||
|4||opests|numeric(1,0)|1||| ||
|5||logmsg|character varying(256)|256||| ||
|6||accesstime|timestamp(6) without time zone|||| ||

## ソース

```sql

 SELECT id,
    userid,
    opecode,
    opests,
    logmsg,
    accesstime
   FROM test_plpgsql.accesslog;

```

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|


## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|


## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト |
|:---|:---|:---|:---|:---|


## ER図

関連するテーブルはありません。

___

[テーブル一覧へ](../../../tableList_testdb.md)
