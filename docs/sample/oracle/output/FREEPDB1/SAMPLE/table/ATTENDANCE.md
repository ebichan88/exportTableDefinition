# ATTENDANCE（勤怠（月次パーティション））

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|Oracle|FREEPDB1|2026/10/04|

## テーブル説明

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|SAMPLE|勤怠（月次パーティション）|ATTENDANCE|table||

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||ATTENDANCE_ID|NUMBER(19,0)|19|○|○|||
|2|勤務日（パーティションキー）|WORK_DATE|DATE||○|○|||
|3||EMPLOYEE_ID|NUMBER(10,0)|10||○|||
|4|勤務時間（分）|WORK_MINUTES|NUMBER(*,0)||||||

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|ATTENDANCE_PKEY|NORMAL|○|○|CREATE UNIQUE INDEX ATTENDANCE_PKEY ON ATTENDANCE (ATTENDANCE_ID,WORK_DATE)||
|2|IDX_ATTENDANCE_EMPLOYEE|NORMAL|||CREATE INDEX IDX_ATTENDANCE_EMPLOYEE ON ATTENDANCE (EMPLOYEE_ID)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|ATTENDANCE_EMPLOYEE_ID_FKEY|FOREIGN KEY|FOREIGN KEY (EMPLOYEE_ID) REFERENCES EMPLOYEE(EMPLOYEE_ID)||
|2|ATTENDANCE_PKEY|PRIMARY KEY|PRIMARY KEY (ATTENDANCE_ID,WORK_DATE)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
|:---|:---|:---|:---|:---|:---|
|1|ATTENDANCE_EMPLOYEE_ID_FKEY|EMPLOYEE_ID|SAMPLE.EMPLOYEE|EMPLOYEE_ID|1対多|

## トリガー情報

| No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
|:---|:---|:---|:---|:---|:---|


## ER図

```mermaid
erDiagram
    SAMPLE_ATTENDANCE["ATTENDANCE（勤怠（月次パーティション））"]
    SAMPLE_EMPLOYEE["EMPLOYEE（従業員）"]
    SAMPLE_ATTENDANCE_NOTE["ATTENDANCE_NOTE"]
    SAMPLE_EMPLOYEE ||--o{ SAMPLE_ATTENDANCE : "ATTENDANCE_EMPLOYEE_ID_FKEY"
    SAMPLE_ATTENDANCE ||--o{ SAMPLE_ATTENDANCE_NOTE : "ATTENDANCE_NOTE_ATTENDANCE_ID_WORK_DATE_FKEY"
    SAMPLE_ATTENDANCE {
        NUMBER ATTENDANCE_ID PK
        DATE WORK_DATE PK "勤務日（パーティションキー）"
        NUMBER EMPLOYEE_ID FK
        NUMBER WORK_MINUTES "勤務時間（分）"
    }
    SAMPLE_EMPLOYEE {
        NUMBER EMPLOYEE_ID PK "従業員ID"
    }
    SAMPLE_ATTENDANCE_NOTE {
        NUMBER ATTENDANCE_ID FK
        DATE WORK_DATE FK
    }
```

___

[テーブル一覧へ](../../tableList_FREEPDB1.md)
