package com.export_table_definition.infrastructure.db.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.database.DatabaseEntity;
import com.export_table_definition.domain.model.relation.Cardinality;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.ConstraintEntity;
import com.export_table_definition.domain.model.table.IndexEntity;
import com.export_table_definition.domain.model.table.TableDetail;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableKey;
import com.export_table_definition.domain.model.table.TableType;
import com.export_table_definition.testsupport.OracleSampleDatabase;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * OracleTableDefinitionRepository（Oracle用mapperのSQLとDTOからの変換）の結合テスト<br>
 * {@code docs/sample/oracle/ddl.sql}を流し込んだ実DBに対してSQLを実行し、DDLに用意した形（複合外部キー・自己参照・
 * 1対1・関数索引・別スキーマの同名テーブル等）が取得できることを確かめる。どのSQLが壊れたかを特定できるよう、取得メソッドごとに検証する
 */
@Tag("oracle")
class OracleTableDefinitionRepositoryIT {

  private static final List<String> SAMPLE_SCHEMA = List.of(OracleSampleDatabase.SCHEMA);

  /** 1列あたり約70バイトのため、ビューのソースが32KBを超える列数 */
  private static final int LONG_VIEW_COLUMNS = 700;

  private static OracleTableDefinitionRepository repository;

  @BeforeAll
  static void setUp() {
    repository = new OracleTableDefinitionRepository(OracleSampleDatabase.sqlSessionFactory());
  }

  @Test
  @DisplayName("selectDatabase: DB名（接続先のPDB名）とRDBMS名を取得する")
  void testSelectDatabase() {
    assertEquals(
        new DatabaseEntity(OracleSampleDatabase.DATABASE_NAME, "Oracle"),
        repository.selectDatabase());
  }

  @Test
  @DisplayName("selectTableList: テーブル・ビュー・マテリアライズドビューを区分とDBコメント由来の論理名つきで取得する")
  void testSelectTableList() {
    final Map<String, TableEntity> tables = byName(tables(), TableEntity::physicalTableName);

    assertEquals(
        List.of(
            "ATTENDANCE",
            "ATTENDANCE_NOTE",
            "AUDIT_LOG",
            "DEPARTMENT",
            "EMPLOYEE",
            "EMPLOYEE_DIRECTORY_VIEW",
            "EMPLOYEE_PROFILE",
            "PARKING_SPOT",
            "PROJECT",
            "PROJECT_ASSIGNMENT",
            "PROJECT_SUMMARY_MV",
            "SHIPMENT",
            "WAREHOUSE_ZONE"),
        tables.keySet().stream().sorted().toList());
    assertEquals(TableType.TABLE, tables.get("EMPLOYEE").tableType());
    assertEquals("従業員", tables.get("EMPLOYEE").logicalTableName());
    // コメントが無い場合は空文字（NULLにしない）
    assertEquals("", tables.get("DEPARTMENT").logicalTableName());
    assertEquals(TableType.VIEW, tables.get("EMPLOYEE_DIRECTORY_VIEW").tableType());
    assertEquals(TableType.MATERIALIZED_VIEW, tables.get("PROJECT_SUMMARY_MV").tableType());
    // マテリアライズドビューのコメントはALL_TAB_COMMENTSではなくALL_MVIEW_COMMENTSにある
    assertEquals("プロジェクト別要員数集計", tables.get("PROJECT_SUMMARY_MV").logicalTableName());
    // ビュー・マテリアライズドビューのソースはLONG型で格納される
    assertTrue(
        tables.get("EMPLOYEE_DIRECTORY_VIEW").definition().contains("join sample.department d"));
    assertTrue(tables.get("PROJECT_SUMMARY_MV").definition().contains("count(pa.employee_id)"));
  }

  @Test
  @DisplayName("selectTableList: パーティション表は1つのテーブルとして取得し、パーティションは別のテーブルとして含めない")
  void testSelectPartitionedTable() {
    final List<TableEntity> attendances =
        tables().stream()
            .filter(table -> table.physicalTableName().startsWith("ATTENDANCE"))
            .toList();

    assertEquals(
        List.of("ATTENDANCE", "ATTENDANCE_NOTE"),
        attendances.stream().map(TableEntity::physicalTableName).toList());
    assertEquals("勤怠（月次パーティション）", attendances.get(0).logicalTableName());
  }

  @Test
  @DisplayName("selectTableList: スキーマを指定した場合は、そのスキーマのテーブルだけを取得する")
  void testSelectTableListOfAnotherSchema() {
    final List<TableEntity> archived = repository.selectTableList(List.of("SAMPLE_ARCHIVE"));

    assertEquals(
        List.of("SAMPLE_ARCHIVE.DEPARTMENT"),
        archived.stream().map(TableEntity::getSchemaTableName).toList());
  }

  @Test
  @DisplayName("selectTableList: スキーマを指定しない場合も、Oracleが管理するスキーマ（SYS・SYSTEM等）は含めない")
  void testSelectTableListWithoutSchemaExcludesOracleMaintainedSchemas() {
    final List<String> schemas =
        repository.selectTableList(List.of()).stream()
            .map(TableEntity::schemaName)
            .distinct()
            .sorted()
            .toList();

    assertEquals(List.of("SAMPLE", "SAMPLE_ARCHIVE"), schemas);
  }

  @Test
  @DisplayName("selectTableDetails: カラムを定義順に、型・桁数・PK・NOT NULL・論理名つきで取得する")
  void testSelectColumns() {
    final List<ColumnEntity> columns = detail("EMPLOYEE").columns();

    assertEquals(
        List.of(
            "EMPLOYEE_ID",
            "EMPLOYEE_CODE",
            "EMPLOYEE_NAME",
            "DEPARTMENT_ID",
            "MANAGER_ID",
            "PARKING_SPOT_ID",
            "STATUS",
            "SALARY",
            "PROFILE",
            "HIRED_DATE",
            "UPDATED_AT"),
        columns.stream().map(ColumnEntity::physicalColumnName).toList());
    final Map<String, ColumnEntity> byName = byName(columns, ColumnEntity::physicalColumnName);
    assertTrue(byName.get("EMPLOYEE_ID").primaryKey());
    assertEquals("従業員ID", byName.get("EMPLOYEE_ID").logicalColumnName());
    assertEquals("NUMBER(10,0)", byName.get("EMPLOYEE_ID").columnType());
    assertEquals("VARCHAR2(10)", byName.get("EMPLOYEE_CODE").columnType());
    assertEquals("10", byName.get("EMPLOYEE_CODE").precisionScale());
    assertTrue(byName.get("EMPLOYEE_CODE").notNull());
    assertFalse(byName.get("MANAGER_ID").notNull());
    assertEquals("NUMBER(10,2)", byName.get("SALARY").columnType());
    assertEquals("10,2", byName.get("SALARY").precisionScale());
    assertEquals("CLOB", byName.get("PROFILE").columnType());
    // デフォルト値はLONG型で、書いたとおり（末尾の空白・改行を含む）に格納される
    assertEquals("'ACTIVE'", byName.get("STATUS").defaultValue());
    assertEquals("trunc(sysdate)", byName.get("HIRED_DATE").defaultValue());
    // IDENTITY列のデフォルト値は、環境ごとに名前の変わるシーケンスのnextvalではなく、生成の種別で表す
    assertEquals("GENERATED BY DEFAULT AS IDENTITY", byName.get("EMPLOYEE_ID").defaultValue());
    // デフォルト値が無い場合は空文字（NULLにしない）
    assertEquals("", byName.get("EMPLOYEE_NAME").defaultValue());
  }

  @Test
  @DisplayName("LONG型の値（ビューのソース・デフォルト値・索引の式）は、&・<・引用符・マルチバイト文字を含み32KBを超えても欠けずに取得する")
  void testSelectLongValuesWithSpecialCharacters() throws SQLException {
    final String literal = "'あいうえお&<>\"''' || note";
    final String columns =
        IntStream.range(0, LONG_VIEW_COLUMNS)
            .mapToObj(i -> "        " + literal + " AS c" + i)
            .collect(Collectors.joining(",\n"));
    try {
      execute(
          "CREATE TABLE sample.zz_long_values (id NUMBER, note VARCHAR2(100) DEFAULT 'a&<b>''c''あ')",
          "CREATE INDEX sample.zz_long_values_expr ON sample.zz_long_values"
              + " (CASE WHEN id < 0 AND note <> 'x' THEN 1 END)",
          "CREATE VIEW sample.zz_long_view AS SELECT\n" + columns + "\nFROM sample.zz_long_values");

      final String definition = table("ZZ_LONG_VIEW").definition();
      assertEquals(
          LONG_VIEW_COLUMNS,
          definition.split(Pattern.quote(literal), -1).length - 1,
          "ビューのソースに含まれる列の数");
      assertTrue(definition.endsWith("FROM sample.zz_long_values"), definition);
      final TableDetail values =
          repository.selectTableDetails(List.of(table("ZZ_LONG_VALUES"))).get(0);
      assertEquals(
          "'a&<b>''c''あ'",
          byName(values.columns(), ColumnEntity::physicalColumnName).get("NOTE").defaultValue());
      assertEquals(
          "CREATE INDEX ZZ_LONG_VALUES_EXPR ON ZZ_LONG_VALUES"
              + " (CASE  WHEN (\"ID\"<0 AND \"NOTE\"<>'x') THEN 1 END )",
          values.indexes().get(0).indexDefinition());
    } finally {
      execute("DROP VIEW sample.zz_long_view", "DROP TABLE sample.zz_long_values PURGE");
    }
  }

  @Test
  @DisplayName("selectTableDetails: 精度を指定しないNUMBER（INTEGER・集計列）は、空の精度を連結せずに表記する")
  void testSelectColumnsOfNumberWithoutPrecision() {
    final ColumnEntity recordId =
        byName(detail("AUDIT_LOG").columns(), ColumnEntity::physicalColumnName).get("RECORD_ID");
    final ColumnEntity memberCount =
        byName(detail("PROJECT_SUMMARY_MV").columns(), ColumnEntity::physicalColumnName)
            .get("MEMBER_COUNT");

    assertEquals("NUMBER(*,0)", recordId.columnType());
    assertEquals("", recordId.precisionScale());
    assertEquals("NUMBER", memberCount.columnType());
  }

  @Test
  @DisplayName("selectTableDetails: オブジェクト型の列は型名で取得する")
  void testSelectColumnsOfObjectType() {
    final ColumnEntity address =
        byName(detail("EMPLOYEE_PROFILE").columns(), ColumnEntity::physicalColumnName)
            .get("ADDRESS");

    assertEquals("ADDRESS_TYPE", address.columnType());
  }

  @Test
  @DisplayName("selectTableDetails: 関数索引（式がLONG型で格納される）・複合索引・一意索引を取得する")
  void testSelectIndexes() {
    final Map<String, IndexEntity> indexes =
        byName(detail("EMPLOYEE").indexes(), IndexEntity::indexName);

    assertEquals(
        List.of(
            "EMPLOYEE_EMPLOYEE_CODE_KEY",
            "EMPLOYEE_PARKING_SPOT_ID_KEY",
            "EMPLOYEE_PKEY",
            "IDX_EMPLOYEE_DEPARTMENT_STATUS",
            "IDX_EMPLOYEE_PROFILE_SKILL"),
        indexes.keySet().stream().sorted().toList());
    assertTrue(indexes.get("EMPLOYEE_PKEY").isPrimary());
    assertTrue(indexes.get("EMPLOYEE_EMPLOYEE_CODE_KEY").isUnique());
    assertFalse(indexes.get("IDX_EMPLOYEE_DEPARTMENT_STATUS").isUnique());
    assertEquals(
        "CREATE INDEX IDX_EMPLOYEE_DEPARTMENT_STATUS ON EMPLOYEE (DEPARTMENT_ID,STATUS)",
        indexes.get("IDX_EMPLOYEE_DEPARTMENT_STATUS").indexDefinition());
    final IndexEntity functionBased = indexes.get("IDX_EMPLOYEE_PROFILE_SKILL");
    assertEquals("FUNCTION-BASED NORMAL", functionBased.indexMethod());
    assertTrue(
        functionBased.indexDefinition().contains("JSON_VALUE(\"PROFILE\""),
        functionBased.indexDefinition());
  }

  @Test
  @DisplayName("selectTableDetails: 別スキーマに同名のテーブルがあっても、インデックスは自スキーマのテーブルのものだけを取得する")
  void testSelectIndexesOfSameNamedTablesInDifferentSchemas() {
    assertEquals(
        List.of("DEPARTMENT_DEPARTMENT_CODE_KEY", "DEPARTMENT_PKEY"),
        detail("DEPARTMENT").indexes().stream().map(IndexEntity::indexName).sorted().toList());

    final TableDetail archived =
        repository.selectTableDetails(repository.selectTableList(List.of("SAMPLE_ARCHIVE"))).get(0);
    assertEquals(
        List.of("DEPARTMENT_PKEY", "IDX_ARCHIVE_DEPARTMENT_NOTE"),
        archived.indexes().stream().map(IndexEntity::indexName).sorted().toList());
    assertTrue(
        archived.indexes().stream().allMatch(index -> index.schemaName().equals("SAMPLE_ARCHIVE")));
    assertEquals(
        List.of("ARCHIVE_ID"),
        archived.columns().stream()
            .filter(ColumnEntity::primaryKey)
            .map(ColumnEntity::physicalColumnName)
            .toList());
  }

  @Test
  @DisplayName("selectTableDetails: 制約を区分・定義つきで取得し、NOT NULL（SYS_C...の名前になる）は含めない")
  void testSelectConstraints() {
    final Map<String, ConstraintEntity> constraints =
        byName(detail("EMPLOYEE").constraints(), ConstraintEntity::constraintName);

    assertEquals(
        List.of(
            "EMPLOYEE_DEPARTMENT_ID_FKEY",
            "EMPLOYEE_EMPLOYEE_CODE_KEY",
            "EMPLOYEE_MANAGER_ID_FKEY",
            "EMPLOYEE_PARKING_SPOT_ID_FKEY",
            "EMPLOYEE_PARKING_SPOT_ID_KEY",
            "EMPLOYEE_PKEY",
            "EMPLOYEE_PROFILE_CHECK",
            "EMPLOYEE_SALARY_CHECK",
            "EMPLOYEE_STATUS_CHECK"),
        constraints.keySet().stream().sorted().toList());
    assertEquals("PRIMARY KEY", constraints.get("EMPLOYEE_PKEY").constraintType());
    assertEquals(
        "PRIMARY KEY (EMPLOYEE_ID)", constraints.get("EMPLOYEE_PKEY").constraintDefinition());
    assertEquals("CHECK", constraints.get("EMPLOYEE_SALARY_CHECK").constraintType());
    assertEquals(
        "CHECK (salary >= 0)", constraints.get("EMPLOYEE_SALARY_CHECK").constraintDefinition());
    assertEquals(
        "UNIQUE (EMPLOYEE_CODE)",
        constraints.get("EMPLOYEE_EMPLOYEE_CODE_KEY").constraintDefinition());
    assertEquals(
        "FOREIGN KEY (DEPARTMENT_ID) REFERENCES DEPARTMENT(DEPARTMENT_ID)",
        constraints.get("EMPLOYEE_DEPARTMENT_ID_FKEY").constraintDefinition());
  }

  @Test
  @DisplayName("selectTableDetails: 複合キーの制約は、列を定義順に連結した1件として取得する")
  void testSelectCompositeConstraints() {
    final List<ConstraintEntity> constraints = detail("SHIPMENT").constraints();

    assertEquals(
        List.of(
            "FOREIGN KEY (WAREHOUSE_CODE,ZONE_CODE) REFERENCES WAREHOUSE_ZONE(WAREHOUSE_CODE,ZONE_CODE)",
            "PRIMARY KEY (SHIPMENT_ID)"),
        constraints.stream().map(ConstraintEntity::constraintDefinition).toList());
    assertEquals(
        List.of("PRIMARY KEY (WAREHOUSE_CODE,ZONE_CODE)"),
        detail("WAREHOUSE_ZONE").constraints().stream()
            .map(ConstraintEntity::constraintDefinition)
            .toList());
  }

  @Test
  @DisplayName("selectTableDetails: 指定したテーブルの詳細情報だけを、テーブルごとに振り分けて取得する")
  void testSelectTableDetailsOnlyForRequestedTables() {
    final List<TableEntity> requested =
        tables().stream()
            .filter(
                table ->
                    table.physicalTableName().equals("SHIPMENT")
                        || table.physicalTableName().equals("WAREHOUSE_ZONE"))
            .toList();

    final List<TableDetail> details = repository.selectTableDetails(requested);

    assertEquals(
        List.of("SHIPMENT", "WAREHOUSE_ZONE"),
        details.stream().map(detail -> detail.table().physicalTableName()).sorted().toList());
    details.forEach(
        detail ->
            assertTrue(
                detail.columns().stream()
                    .allMatch(
                        column -> column.tableName().equals(detail.table().physicalTableName()))));
  }

  @Test
  @DisplayName("selectColumnList: 指定したテーブルのカラムだけを、テーブルごとに定義順で取得する")
  void testSelectColumnListOnlyForRequestedTables() {
    final List<ColumnEntity> columns =
        repository.selectColumnList(
            List.of(TableKey.of("SAMPLE", "EMPLOYEE"), TableKey.of("SAMPLE", "DEPARTMENT")));

    assertEquals(
        List.of("DEPARTMENT", "EMPLOYEE"),
        columns.stream().map(ColumnEntity::tableName).distinct().toList());
    final List<ColumnEntity> employeeColumns =
        columns.stream().filter(column -> column.tableName().equals("EMPLOYEE")).toList();
    assertEquals("EMPLOYEE_ID", employeeColumns.get(0).physicalColumnName());
    final ColumnEntity departmentId =
        employeeColumns.stream()
            .filter(column -> column.physicalColumnName().equals("DEPARTMENT_ID"))
            .findFirst()
            .orElseThrow();
    assertEquals("所属部署ID", departmentId.logicalColumnName());
  }

  @Test
  @DisplayName("selectForeignKeyList: 複合外部キーは、参照元と参照先の列を同じ順序で対応させる")
  void testSelectCompositeForeignKey() {
    final ForeignKeyEntity foreignKey = foreignKey("SHIPMENT_WAREHOUSE_CODE_ZONE_CODE_FKEY");

    assertEquals("WAREHOUSE_ZONE", foreignKey.referenceTableName());
    assertEquals(List.of("WAREHOUSE_CODE", "ZONE_CODE"), foreignKey.columnNames());
    assertEquals(List.of("WAREHOUSE_CODE", "ZONE_CODE"), foreignKey.referenceColumnNames());
    assertEquals(Cardinality.ONE_TO_MANY, foreignKey.cardinality());
  }

  @Test
  @DisplayName("selectForeignKeyList: 参照元の列のNOT NULL・一意性から多重度を判定する（自己参照・1対1・パーティション表を含む）")
  void testSelectForeignKeyCardinality() {
    assertEquals(Cardinality.ONE_TO_MANY, foreignKey("EMPLOYEE_DEPARTMENT_ID_FKEY").cardinality());
    final ForeignKeyEntity selfReference = foreignKey("EMPLOYEE_MANAGER_ID_FKEY");
    assertEquals("EMPLOYEE", selfReference.tableName());
    assertEquals("EMPLOYEE", selfReference.referenceTableName());
    assertEquals(Cardinality.OPTIONAL_ONE_TO_MANY, selfReference.cardinality());
    assertEquals(
        Cardinality.OPTIONAL_ONE_TO_ONE, foreignKey("EMPLOYEE_PARKING_SPOT_ID_FKEY").cardinality());
    assertEquals(
        Cardinality.ONE_TO_ONE, foreignKey("EMPLOYEE_PROFILE_EMPLOYEE_ID_FKEY").cardinality());
    assertEquals(
        Cardinality.ONE_TO_MANY,
        foreignKey("ATTENDANCE_NOTE_ATTENDANCE_ID_WORK_DATE_FKEY").cardinality());
  }

  private static List<TableEntity> tables() {
    return repository.selectTableList(SAMPLE_SCHEMA);
  }

  private static TableEntity table(String tableName) {
    return tables().stream()
        .filter(t -> t.physicalTableName().equals(tableName))
        .findFirst()
        .orElseThrow();
  }

  /** 失敗した文があっても残りを実行する（後片付けで、作成に失敗したオブジェクトがあっても残りを削除するため） */
  private static void execute(String... statements) throws SQLException {
    SQLException failure = null;
    try (SqlSession session = OracleSampleDatabase.sqlSessionFactory().openSession();
        Statement statement = session.getConnection().createStatement()) {
      for (final String sql : statements) {
        try {
          statement.execute(sql);
        } catch (SQLException e) {
          if (failure == null) {
            failure = e;
          } else {
            failure.addSuppressed(e);
          }
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static TableDetail detail(String tableName) {
    final List<TableEntity> table =
        tables().stream().filter(t -> t.physicalTableName().equals(tableName)).toList();
    return repository.selectTableDetails(table).get(0);
  }

  private static ForeignKeyEntity foreignKey(String foreignKeyName) {
    return byName(repository.selectForeignKeyList(SAMPLE_SCHEMA), ForeignKeyEntity::foreignKeyName)
        .get(foreignKeyName);
  }

  private static <T> Map<String, T> byName(List<T> values, Function<T, String> name) {
    return values.stream().collect(Collectors.toMap(name, Function.identity()));
  }
}
