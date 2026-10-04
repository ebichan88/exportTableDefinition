# PROJECT_ASSIGNMENT

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|Oracle|FREEPDB1|2026/10/04|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|SAMPLE||PROJECT_ASSIGNMENT|table||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||PROJECT_ID|NUMBER(10,0)|10|○|○|||
|2||EMPLOYEE_ID|NUMBER(10,0)|10|○|○|||
|3||ROLE|VARCHAR2(30)|30||○|'MEMBER'||
|4||ASSIGNED_AT|DATE|||○|trunc(sysdate)||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|PROJECT_ASSIGNMENT_PKEY|NORMAL|○|○|CREATE UNIQUE INDEX PROJECT_ASSIGNMENT_PKEY ON PROJECT_ASSIGNMENT (PROJECT_ID,EMPLOYEE_ID)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|PROJECT_ASSIGNMENT_EMPLOYEE_ID_FKEY|FOREIGN KEY|FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEE(EMPLOYEE_ID)||
|2|PROJECT_ASSIGNMENT_PROJECT_ID_FKEY|FOREIGN KEY|FOREIGN KEY (PROJECT_ID) REFERENCES PROJECT(PROJECT_ID)||
|3|PROJECT_ASSIGNMENT_PKEY|PRIMARY KEY|PRIMARY KEY (PROJECT_ID,EMPLOYEE_ID)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
|:---|:---|:---|:---|:---|:---|
|1|PROJECT_ASSIGNMENT_EMPLOYEE_ID_FKEY|EMPLOYEE_ID|SAMPLE.EMPLOYEE|EMPLOYEE_ID|1対多|
|2|PROJECT_ASSIGNMENT_PROJECT_ID_FKEY|PROJECT_ID|SAMPLE.PROJECT|PROJECT_ID|1対多|

## トリガー情報

| No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
|:---|:---|:---|:---|:---|:---|
|1|TRG_PROJECT_ASSIGNMENT_DELETE|AFTER|DELETE|STATEMENT|CREATE OR REPLACE TRIGGER sample.trg_project_assignment_delete after delete on sample.project_assignment|

## ER図

```mermaid
erDiagram
    SAMPLE_PROJECT_ASSIGNMENT["PROJECT_ASSIGNMENT"]
    SAMPLE_EMPLOYEE["EMPLOYEE（従業員）"]
    SAMPLE_PROJECT["PROJECT（プロジェクト）"]
    SAMPLE_EMPLOYEE ||--o{ SAMPLE_PROJECT_ASSIGNMENT : "PROJECT_ASSIGNMENT_EMPLOYEE_ID_FKEY"
    SAMPLE_PROJECT ||--o{ SAMPLE_PROJECT_ASSIGNMENT : "PROJECT_ASSIGNMENT_PROJECT_ID_FKEY"
    SAMPLE_PROJECT_ASSIGNMENT {
        NUMBER PROJECT_ID PK, FK
        NUMBER EMPLOYEE_ID PK, FK
        VARCHAR2 ROLE
        DATE ASSIGNED_AT
    }
    SAMPLE_EMPLOYEE {
        NUMBER EMPLOYEE_ID PK "従業員ID"
    }
    SAMPLE_PROJECT {
        NUMBER PROJECT_ID PK "プロジェクトID"
    }
```

___

[テーブル一覧へ](../../tableList_FREEPDB1.md)
