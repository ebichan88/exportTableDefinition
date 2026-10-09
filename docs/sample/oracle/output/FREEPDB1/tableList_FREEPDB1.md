# テーブル一覧（DB名：FREEPDB1）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|Oracle 23|FREEPDB1|2026/10/08|

## 関連ドキュメント

* [ER図一覧](./erDiagramList_FREEPDB1.md)  
* [関数・プロシージャ一覧](./functionList_FREEPDB1.md)  
* [シーケンス一覧](./sequenceList_FREEPDB1.md)  
* [ユーザー定義型一覧](./typeList_FREEPDB1.md)  
* [トリガー一覧](./triggerList_FREEPDB1.md)  

## テーブル情報

| No. | スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | Link |
|:---|:---|:---|:---|:---|:---|
|1|SAMPLE|勤怠（月次パーティション）|ATTENDANCE|table|[■](./SAMPLE/table/ATTENDANCE.md)|
|2|SAMPLE|部署別の月次勤務時間|ATTENDANCE_MONTHLY_VIEW|view|[■](./SAMPLE/view/ATTENDANCE_MONTHLY_VIEW.md)|
|3|SAMPLE||ATTENDANCE_NOTE|table|[■](./SAMPLE/table/ATTENDANCE_NOTE.md)|
|4|SAMPLE||AUDIT_LOG|table|[■](./SAMPLE/table/AUDIT_LOG.md)|
|5|SAMPLE||DEPARTMENT|table|[■](./SAMPLE/table/DEPARTMENT.md)|
|6|SAMPLE|従業員|EMPLOYEE|table|[■](./SAMPLE/table/EMPLOYEE.md)|
|7|SAMPLE||EMPLOYEE_DIRECTORY_VIEW|view|[■](./SAMPLE/view/EMPLOYEE_DIRECTORY_VIEW.md)|
|8|SAMPLE||EMPLOYEE_PROFILE|table|[■](./SAMPLE/table/EMPLOYEE_PROFILE.md)|
|9|SAMPLE||PARKING_SPOT|table|[■](./SAMPLE/table/PARKING_SPOT.md)|
|10|SAMPLE|プロジェクト|PROJECT|table|[■](./SAMPLE/table/PROJECT.md)|
|11|SAMPLE||PROJECT_ASSIGNMENT|table|[■](./SAMPLE/table/PROJECT_ASSIGNMENT.md)|
|12|SAMPLE|プロジェクト別要員数集計|PROJECT_SUMMARY_MV|materialized_view|[■](./SAMPLE/materialized_view/PROJECT_SUMMARY_MV.md)|
|13|SAMPLE||SHIPMENT|table|[■](./SAMPLE/table/SHIPMENT.md)|
|14|SAMPLE||WAREHOUSE_ZONE|table|[■](./SAMPLE/table/WAREHOUSE_ZONE.md)|

