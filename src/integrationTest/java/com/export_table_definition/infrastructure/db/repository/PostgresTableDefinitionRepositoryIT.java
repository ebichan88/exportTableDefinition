package com.export_table_definition.infrastructure.db.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.database.DatabaseEntity;
import com.export_table_definition.domain.model.relation.Cardinality;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.ConstraintEntity;
import com.export_table_definition.domain.model.table.IndexEntity;
import com.export_table_definition.domain.model.table.TableDetail;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableType;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.testsupport.SampleDatabase;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PostgresTableDefinitionRepository（PostgreSQL用mapperのSQLとDTOからの変換）の結合テスト<br>
 * {@code docs/sample/postgres/ddl.sql}を流し込んだ実DBに対してSQLを実行し、DDLに用意した形（複合外部キー・自己参照・
 * 1対1・ビュー・トリガー・オーバーロード等）が取得できることを確かめる。どのSQLが壊れたかを特定できるよう、取得メソッドごとに検証する
 */
class PostgresTableDefinitionRepositoryIT {

  private static final List<String> SAMPLE_SCHEMA = List.of("sample");

  private static PostgresTableDefinitionRepository repository;

  @BeforeAll
  static void setUp() {
    repository = new PostgresTableDefinitionRepository(SampleDatabase.sqlSessionFactory());
  }

  @Test
  @DisplayName("selectDatabase: DB名とRDBMS名を取得する")
  void testSelectDatabase() {
    assertEquals(
        new DatabaseEntity(SampleDatabase.DATABASE_NAME, "PostgreSQL"),
        repository.selectDatabase());
  }

  @Test
  @DisplayName("selectTableList: テーブル・ビュー・マテリアライズドビューを区分とDBコメント由来の論理名つきで取得する")
  void testSelectTableList() {
    final Map<String, TableEntity> tables = byName(tables(), TableEntity::physicalTableName);

    assertEquals(
        List.of(
            "audit_log",
            "department",
            "employee",
            "employee_directory_view",
            "employee_profile",
            "parking_spot",
            "project",
            "project_assignment",
            "project_summary_mv",
            "shipment",
            "warehouse_zone"),
        tables.keySet().stream().sorted().toList());
    assertEquals(TableType.TABLE, tables.get("employee").tableType());
    assertEquals("従業員", tables.get("employee").logicalTableName());
    // コメントが無い場合は空文字（NULLにしない）
    assertEquals("", tables.get("department").logicalTableName());
    assertEquals(TableType.VIEW, tables.get("employee_directory_view").tableType());
    assertTrue(
        tables.get("employee_directory_view").definition().contains("JOIN sample.department"));
    assertEquals(TableType.MATERIALIZED_VIEW, tables.get("project_summary_mv").tableType());
    assertEquals("プロジェクト別要員数集計", tables.get("project_summary_mv").logicalTableName());
  }

  @Test
  @DisplayName("selectTableList: スキーマを指定しない場合も、システムカタログ（pg_catalog・information_schema）は含めない")
  void testSelectTableListWithoutSchemaExcludesSystemCatalogs() {
    final List<TableEntity> tables = repository.selectTableList(List.of());

    assertTrue(tables.stream().anyMatch(table -> table.physicalTableName().equals("employee")));
    assertTrue(
        tables.stream()
            .map(TableEntity::schemaName)
            .noneMatch(
                schema -> schema.equals("pg_catalog") || schema.equals("information_schema")));
  }

  @Test
  @DisplayName("selectTableDetails: カラムを定義順に、型・桁数・PK・NOT NULL・デフォルト値・論理名つきで取得する")
  void testSelectColumns() {
    final List<ColumnEntity> columns = detail("employee").columns();

    assertEquals(
        List.of(
            "employee_id",
            "employee_code",
            "employee_name",
            "department_id",
            "manager_id",
            "parking_spot_id",
            "status",
            "salary",
            "profile",
            "hired_date",
            "updated_at"),
        columns.stream().map(ColumnEntity::physicalColumnName).toList());
    final Map<String, ColumnEntity> byName = byName(columns, ColumnEntity::physicalColumnName);
    assertTrue(byName.get("employee_id").primaryKey());
    assertEquals("従業員ID", byName.get("employee_id").logicalColumnName());
    assertEquals("character varying(10)", byName.get("employee_code").columnType());
    assertEquals("10", byName.get("employee_code").precisionScale());
    assertFalse(byName.get("manager_id").notNull());
    assertEquals("sample.employee_status_enum", byName.get("status").columnType());
    assertEquals("'ACTIVE'::sample.employee_status_enum", byName.get("status").defaultValue());
    assertEquals("numeric(10,2)", byName.get("salary").columnType());
    // デフォルト値が無い場合は空文字（NULLにしない）
    assertEquals("", byName.get("employee_name").defaultValue());
  }

  @Test
  @DisplayName("selectTableDetails: btree以外の索引種別・複合索引・一意索引を取得する")
  void testSelectIndexes() {
    final Map<String, IndexEntity> indexes =
        byName(detail("employee").indexes(), IndexEntity::indexName);

    assertEquals(
        List.of(
            "employee_employee_code_key",
            "employee_parking_spot_id_key",
            "employee_pkey",
            "idx_employee_department_status",
            "idx_employee_profile_gin"),
        indexes.keySet().stream().sorted().toList());
    assertTrue(indexes.get("employee_pkey").isPrimary());
    assertEquals("gin", indexes.get("idx_employee_profile_gin").indexMethod());
    assertFalse(indexes.get("idx_employee_department_status").isUnique());
    assertTrue(
        indexes
            .get("idx_employee_department_status")
            .indexDefinition()
            .endsWith("(department_id, status)"));
  }

  @Test
  @DisplayName("selectTableDetails: 制約の区分に改行・余分な空白が混じらない（'PRIMARY KEY'の折り返しで壊れた過去の不具合）")
  void testSelectConstraints() {
    final Map<String, ConstraintEntity> constraints =
        byName(detail("employee").constraints(), ConstraintEntity::constraintName);

    assertEquals("PRIMARY KEY", constraints.get("employee_pkey").constraintType());
    assertEquals(
        "PRIMARY KEY (employee_id)", constraints.get("employee_pkey").constraintDefinition());
    assertEquals("CHECK", constraints.get("employee_salary_check").constraintType());
    assertEquals("UNIQUE", constraints.get("employee_employee_code_key").constraintType());
    assertEquals("FOREIGN KEY", constraints.get("employee_department_id_fkey").constraintType());
  }

  @Test
  @DisplayName("selectTableDetails: 指定したテーブルの詳細情報だけを、テーブルごとに振り分けて取得する")
  void testSelectTableDetailsOnlyForRequestedTables() {
    final List<TableEntity> requested =
        tables().stream()
            .filter(
                table ->
                    table.physicalTableName().equals("shipment")
                        || table.physicalTableName().equals("warehouse_zone"))
            .toList();

    final List<TableDetail> details = repository.selectTableDetails(requested);

    assertEquals(
        List.of("shipment", "warehouse_zone"),
        details.stream().map(detail -> detail.table().physicalTableName()).sorted().toList());
    details.forEach(
        detail ->
            assertTrue(
                detail.columns().stream()
                    .allMatch(
                        column -> column.tableName().equals(detail.table().physicalTableName()))));
  }

  @Test
  @DisplayName("selectForeignKeyList: 複合外部キーは、参照元と参照先の列を同じ順序で対応させる")
  void testSelectCompositeForeignKey() {
    final ForeignKeyEntity foreignKey = foreignKey("shipment_warehouse_code_zone_code_fkey");

    assertEquals("warehouse_zone", foreignKey.referenceTableName());
    assertEquals(List.of("warehouse_code", "zone_code"), foreignKey.columnNames());
    assertEquals(List.of("warehouse_code", "zone_code"), foreignKey.referenceColumnNames());
    assertEquals(Cardinality.ONE_TO_MANY, foreignKey.cardinality());
  }

  @Test
  @DisplayName("selectForeignKeyList: 参照元の列のNOT NULL・一意性から多重度を判定する（自己参照・1対1を含む）")
  void testSelectForeignKeyCardinality() {
    assertEquals(Cardinality.ONE_TO_MANY, foreignKey("employee_department_id_fkey").cardinality());
    final ForeignKeyEntity selfReference = foreignKey("employee_manager_id_fkey");
    assertEquals("employee", selfReference.tableName());
    assertEquals("employee", selfReference.referenceTableName());
    assertEquals(Cardinality.OPTIONAL_ONE_TO_MANY, selfReference.cardinality());
    assertEquals(
        Cardinality.OPTIONAL_ONE_TO_ONE, foreignKey("employee_parking_spot_id_fkey").cardinality());
    assertEquals(
        Cardinality.ONE_TO_ONE, foreignKey("employee_profile_employee_id_fkey").cardinality());
  }

  @Test
  @DisplayName("selectTriggerList: タイミング（BEFORE/AFTER/INSTEAD OF）・イベント・行/文単位を取得する")
  void testSelectTriggers() {
    final Map<String, TriggerEntity> triggers =
        byName(repository.selectTriggerList(SAMPLE_SCHEMA), TriggerEntity::triggerName);

    final TriggerEntity audit = triggers.get("trg_employee_audit");
    assertEquals("AFTER", audit.timing());
    assertEquals(List.of("INSERT", "DELETE", "UPDATE"), audit.events());
    assertEquals("ROW", audit.orientation());
    assertEquals("sample.log_employee_change", audit.functionName());
    assertEquals("BEFORE", triggers.get("trg_employee_set_updated_at").timing());
    final TriggerEntity truncate = triggers.get("trg_project_assignment_truncate");
    assertEquals(List.of("TRUNCATE"), truncate.events());
    assertEquals("STATEMENT", truncate.orientation());
    final TriggerEntity insteadOf = triggers.get("trg_employee_directory_insert");
    assertEquals("INSTEAD OF", insteadOf.timing());
    assertEquals("employee_directory_view", insteadOf.tableName());
  }

  @Test
  @DisplayName("selectFunctionList: 関数とプロシージャを区別し、オーバーロードに作成順の番号を振る")
  void testSelectFunctions() {
    final List<FunctionEntity> functions = repository.selectFunctionList(SAMPLE_SCHEMA);

    final List<FunctionEntity> overloads =
        functions.stream()
            .filter(function -> function.functionName().equals("calculate_bonus"))
            .toList();
    assertEquals(List.of(1, 2), overloads.stream().map(FunctionEntity::overloadIndex).toList());
    assertTrue(overloads.stream().allMatch(function -> function.overloadCount() == 2));
    assertEquals("p_salary numeric, p_rate numeric", overloads.get(0).functionArguments());
    final FunctionEntity procedure =
        functions.stream()
            .filter(function -> function.functionName().equals("raise_salary"))
            .findFirst()
            .orElseThrow();
    assertEquals("PROCEDURE", procedure.functionKind());
    // 一覧の取得では定義本体を取得しない
    assertEquals("", procedure.definition());
  }

  @Test
  @DisplayName("selectFunctionDefList: 定義本体を取得する")
  void testSelectFunctionDefinitions() {
    final FunctionEntity function =
        repository.selectFunctionDefList(SAMPLE_SCHEMA).stream()
            .filter(f -> f.functionName().equals("set_updated_at"))
            .findFirst()
            .orElseThrow();

    assertTrue(function.definition().contains("new.updated_at := now();"));
  }

  @Test
  @DisplayName("selectSequenceList: 列に紐付かないシーケンスは所有者が空、serial列のシーケンスは所有する列を取得する")
  void testSelectSequences() {
    final Map<String, SequenceEntity> sequences =
        byName(repository.selectSequenceList(SAMPLE_SCHEMA), SequenceEntity::sequenceName);

    final SequenceEntity invoice = sequences.get("invoice_no_seq");
    assertEquals("1000", invoice.minValue());
    assertEquals("999999", invoice.maxValue());
    assertEquals("5", invoice.cacheSize());
    assertTrue(invoice.cycle());
    assertEquals("", invoice.ownedBy());
    assertEquals("audit_log.log_id", sequences.get("audit_log_log_id_seq").ownedBy());
  }

  @Test
  @DisplayName("selectTypeList: ENUM・COMPOSITE・DOMAINのユーザー定義型を取得する")
  void testSelectTypes() {
    final Map<String, TypeEntity> types =
        byName(repository.selectTypeList(SAMPLE_SCHEMA), TypeEntity::typeName);

    assertEquals("ENUM", types.get("employee_status_enum").typeCategory());
    assertEquals("ACTIVE, ON_LEAVE, RETIRED", types.get("employee_status_enum").definition());
    assertEquals("COMPOSITE", types.get("address_type").typeCategory());
    assertEquals("DOMAIN", types.get("positive_numeric").typeCategory());
  }

  private static List<TableEntity> tables() {
    return repository.selectTableList(SAMPLE_SCHEMA);
  }

  private static TableDetail detail(String tableName) {
    final List<TableEntity> table =
        tables().stream().filter(t -> t.physicalTableName().equals(tableName)).toList();
    return repository.selectTableDetails(table).get(0);
  }

  private static ForeignKeyEntity foreignKey(String foreignKeyName) {
    return byName(repository.selectForeignKeyList(SAMPLE_SCHEMA), ForeignKeyEntity::foreignkeyName)
        .get(foreignKeyName);
  }

  private static <T> Map<String, T> byName(List<T> values, Function<T, String> name) {
    return values.stream().collect(Collectors.toMap(name, Function.identity()));
  }
}
