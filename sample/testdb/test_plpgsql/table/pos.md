# pos

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/21|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|test_plpgsql||pos|table||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||poscode|character(1)|1|○|○| ||
|2||posname|character varying(20)|20||○| ||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|pos_pkey|btree|○|○|CREATE UNIQUE INDEX pos_pkey ON test_plpgsql.pos USING btree (poscode)||
|2|pos_posname_key|btree|○||CREATE UNIQUE INDEX pos_posname_key ON test_plpgsql.pos USING btree (posname)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|pos_pkey|PRIMARY KEY|PRIMARY KEY (poscode)||
|2|pos_posname_key|UNIQUE|UNIQUE (posname)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト |
|:---|:---|:---|:---|:---|


## ER図

```mermaid
erDiagram
    test_plpgsql_pos ||--o{ test_plpgsql_emp : "emp_poscode_fkey"
    test_plpgsql_pos {
        character poscode PK
        character_varying posname
    }
```

___

[テーブル一覧へ](../../../tableList_testdb.md)
