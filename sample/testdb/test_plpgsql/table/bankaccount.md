# bankaccount（銀行口座）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/21|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|test_plpgsql|銀行口座|bankaccount|table||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1|ユーザID|userid|character varying(10)|10|○|○| ||
|2|口座番号|accnumber|numeric(7,0)|7|○|○| ||
|3|口座種別|acctype|character varying(30)|30|○|○| ||
|4|名義|name|character varying(30)|30||| ||
|5|残高|balance|numeric(20,0)|20||| ||
|6|更新日|lastdate|timestamp(6) without time zone|||| ||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|bankaccount_pkey|btree|○|○|CREATE UNIQUE INDEX bankaccount_pkey ON test_plpgsql.bankaccount USING btree (userid, accnumber, acctype)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|bankaccount_pkey|PRIMARY KEY|PRIMARY KEY (userid, accnumber, acctype)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト |
|:---|:---|:---|:---|:---|


## ER図

関連するテーブルはありません。

___

[テーブル一覧へ](../../../tableList_testdb.md)
