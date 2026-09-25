# audit_log

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/25|

## テーブル説明

employeeテーブルの変更を記録する監査ログ。トリガー経由でのみ書き込まれる。

## テーブル情報

| スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
|:---|:---|:---|:---|:---|
|sample||audit_log|table|アプリケーションからの直接INSERTは禁止|

## カラム情報

| No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1||log_id|bigint||○|○|nextval('sample.audit_log_log_id_seq'::regclass)||
|2||table_name|character varying(50)|50||○| |変更対象のテーブル名|
|3||record_id|integer|||| |変更対象の主キー値|
|4||action|character varying(10)|10||○| |変更種別（INSERT / UPDATE / DELETE）|
|5||changed_at|timestamp without time zone|||○|now()|変更日時<br>（サーバータイムゾーン基準 \| UTC変換は未実施）<br>|
|6||changed_by|character varying(50)|50||| |変更を行ったDBユーザー|

## インデックス情報

| No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
|:---|:---|:---|:---|:---|:---|:---|
|1|audit_log_pkey|btree|○|○|CREATE UNIQUE INDEX audit_log_pkey ON sample.audit_log USING btree (log_id)||

## 制約情報

| No. | 制約名 | 種類 | 制約定義 | 備考 |
|:---|:---|:---|:---|:---|
|1|audit_log_action_check|CHECK|CHECK (((action)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))||
|2|audit_log_pkey|PRIMARY KEY|PRIMARY KEY (log_id)||

## 外部キー情報

| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
|:---|:---|:---|:---|:---|:---|


## トリガー情報

| No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
|:---|:---|:---|:---|:---|:---|


## ER図

関連するテーブルはありません。

___

[テーブル一覧へ](../../../tableList_testdb.md)
