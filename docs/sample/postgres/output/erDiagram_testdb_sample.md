# ER図（DB名：testdb / スキーマ名：sample）

## 基本情報

| RDBMS | データベース名 | 作成日 |
|:---|:---|:---|
|PostgreSQL|testdb|2026/09/25|

## ER図

```mermaid
erDiagram
    sample_department ||--o{ sample_employee : "employee_department_id_fkey"
    sample_employee |o--o{ sample_employee : "employee_manager_id_fkey"
    sample_parking_spot |o--o| sample_employee : "employee_parking_spot_id_fkey"
    sample_employee ||--o| sample_employee_profile : "employee_profile_employee_id_fkey"
    sample_employee ||--o{ sample_project_assignment : "project_assignment_employee_id_fkey"
    sample_project ||--o{ sample_project_assignment : "project_assignment_project_id_fkey"
    sample_warehouse_zone ||--o{ sample_shipment : "shipment_warehouse_code_zone_code_fkey"
```

## ER図に掲載しているテーブル

| No. | スキーマ名 | 物理テーブル名 | 論理テーブル名 | 区分 | Link |
|:---|:---|:---|:---|:---|:---|
| 1 | sample | department |  | table | [■](./testdb/sample/table/department.md) |
| 2 | sample | employee | 従業員 | table | [■](./testdb/sample/table/employee.md) |
| 3 | sample | employee_profile |  | table | [■](./testdb/sample/table/employee_profile.md) |
| 4 | sample | parking_spot |  | table | [■](./testdb/sample/table/parking_spot.md) |
| 5 | sample | project | プロジェクト | table | [■](./testdb/sample/table/project.md) |
| 6 | sample | project_assignment |  | table | [■](./testdb/sample/table/project_assignment.md) |
| 7 | sample | shipment |  | table | [■](./testdb/sample/table/shipment.md) |
| 8 | sample | warehouse_zone |  | table | [■](./testdb/sample/table/warehouse_zone.md) |

___

[ER図一覧へ](./erDiagramList_testdb.md) [テーブル一覧へ](./tableList_testdb.md)
