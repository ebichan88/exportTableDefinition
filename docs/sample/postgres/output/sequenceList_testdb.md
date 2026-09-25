# シーケンス一覧（DB名：testdb）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/25|

## シーケンス一覧

| No. | スキーマ名 | シーケンス名 | 増分 | 最小値 | 最大値 | キャッシュ | 開始値 | 循環 | 所有カラム | Link |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
|1|sample|audit_log_log_id_seq|1|1|9223372036854775807|1|1||audit_log.log_id|[■](./testdb/sample/sequence/audit_log_log_id_seq.md)|
|2|sample|department_department_id_seq|1|1|2147483647|1|1||department.department_id|[■](./testdb/sample/sequence/department_department_id_seq.md)|
|3|sample|employee_employee_id_seq|1|1|2147483647|1|1||employee.employee_id|[■](./testdb/sample/sequence/employee_employee_id_seq.md)|
|4|sample|invoice_no_seq|1|1000|999999|5|1000|○||[■](./testdb/sample/sequence/invoice_no_seq.md)|
|5|sample|parking_spot_parking_spot_id_seq|1|1|2147483647|1|1||parking_spot.parking_spot_id|[■](./testdb/sample/sequence/parking_spot_parking_spot_id_seq.md)|
|6|sample|project_project_id_seq|1|1|2147483647|1|1||project.project_id|[■](./testdb/sample/sequence/project_project_id_seq.md)|
|7|sample|shipment_shipment_id_seq|1|1|2147483647|1|1||shipment.shipment_id|[■](./testdb/sample/sequence/shipment_shipment_id_seq.md)|

___

[テーブル一覧へ](./tableList_testdb.md)
