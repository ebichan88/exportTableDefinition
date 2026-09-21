# emp

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/21|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|test_plpgsql||emp|table||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||empno|character(5)|5|○|○| ||
|2||empname|character varying(40)|40||○| ||
|3||poscode|character(1)|1||○| ||
|4||age|numeric(3,0)|3||| ||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|emp_pkey|btree|○|○|CREATE UNIQUE INDEX emp_pkey ON test_plpgsql.emp USING btree (empno)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|emp_age_check|CHECK|CHECK ((age >= (0)::numeric))||
|2|emp_poscode_fkey|FOREIGN KEY|FOREIGN KEY (poscode) REFERENCES test_plpgsql.pos(poscode)||
|3|emp_pkey|PRIMARY KEY|PRIMARY KEY (empno)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト |
|:---|:---|:---|:---|:---|
|1|emp_poscode_fkey|poscode|test_plpgsql.pos|poscode|

## ER図

```mermaid
erDiagram
    test_plpgsql_pos ||--o{ test_plpgsql_emp : "emp_poscode_fkey"
    test_plpgsql_emp ||--o{ test_plpgsql_member : "member_empno_fkey"
    test_plpgsql_emp {
        character empno PK
        character_varying empname
        character poscode
        numeric age
    }
```

___

[テーブル一覧へ](../../../tableList_testdb.md)
