package com.export_table_definition.infrastructure.file.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.type.Cardinality;
import com.export_table_definition.domain.model.type.RelationType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AnnotationYamlRepository のサイドカーYAML読み込みに関するテスト */
public class AnnotationYamlRepositoryTest {

  private final AnnotationYamlRepository repository = new AnnotationYamlRepository();

  private TableEntity table(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, "table", "", "", "");
  }

  private Path writeYaml(Path dir, String content) throws IOException {
    Path file = dir.resolve("annotations.yml");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  @Test
  @DisplayName("load: 説明（複数行）・テーブル備考・カラム備考を読み込む")
  void testLoadFull(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                tables:
                  public.users:
                    description: |
                      ユーザー基本情報。
                      認証と紐づく。
                    remarks: 個人情報を含む
                    columns:
                      email: ログインID兼用
                      status: 0=無効 1=有効
                """);

    Annotations annotations = repository.load(file.toString()).annotations();
    TableAnnotation users = annotations.of(table("public", "users"));

    assertEquals("ユーザー基本情報。\n認証と紐づく。\n", users.description().replace("\r\n", "\n"));
    assertEquals("個人情報を含む", users.remarks());
    assertEquals("ログインID兼用", users.columnRemark("email"));
    assertEquals("0=無効 1=有効", users.columnRemark("status"));
  }

  @Test
  @DisplayName("load: パスが空・null・空白の場合は空のAnnotationsを返す")
  void testLoadBlankPath() {
    assertTrue(repository.load(null).annotations().isEmpty());
    assertTrue(repository.load("").annotations().isEmpty());
    assertTrue(repository.load("   ").annotations().isEmpty());
  }

  @Test
  @DisplayName("load: ファイルが存在しない場合は空のAnnotationsを返す")
  void testLoadMissingFile(@TempDir Path dir) {
    assertTrue(repository.load(dir.resolve("not_exist.yml").toString()).annotations().isEmpty());
  }

  @Test
  @DisplayName("load: 'schema.table'形式でないキーは読み飛ばす")
  void testLoadIgnoresInvalidKey(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                tables:
                  invalidkey:
                    remarks: 無視される
                  public.orders:
                    remarks: 有効
                """);

    Annotations annotations = repository.load(file.toString()).annotations();

    assertEquals(1, annotations.tableKeys().size());
    assertEquals("有効", annotations.of(table("public", "orders")).remarks());
  }

  @Test
  @DisplayName("load: tablesキーが存在しない場合は空のAnnotationsを返す")
  void testLoadNoTablesKey(@TempDir Path dir) throws IOException {
    Path file = writeYaml(dir, "other: value\n");
    assertTrue(repository.load(file.toString()).annotations().isEmpty());
  }

  @Test
  @DisplayName("load: スキーマ名にドットが無いテーブル名も、最初のドットで分割して解釈する")
  void testLoadSplitsOnFirstDot(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                tables:
                  public.my.table:
                    remarks: ドット入りテーブル名
                """);

    Annotations annotations = repository.load(file.toString()).annotations();

    assertEquals("ドット入りテーブル名", annotations.of(table("public", "my.table")).remarks());
  }

  @Test
  @DisplayName("load: relations の全項目を指定した論理リレーションを読み込む")
  void testLoadRelationFull(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                relations:
                  - table: public.logs
                    columns: [user_id]
                    parentTable: public.users
                    parentColumns: [id]
                    name: rel_logs_users
                    cardinality: 0..1対多
                """);

    List<ForeignKeyEntity> relations = repository.load(file.toString()).logicalRelations();

    assertEquals(1, relations.size());
    ForeignKeyEntity relation = relations.get(0);
    assertEquals("public", relation.schemaName());
    assertEquals("logs", relation.tableName());
    assertEquals("rel_logs_users", relation.foreignkeyName());
    assertEquals("user_id", relation.columnNames());
    assertEquals("public.users", relation.getReferenceSchemaTableName());
    assertEquals("id", relation.referenceColumnNames());
    assertEquals(Cardinality.OPTIONAL_ONE_TO_MANY, relation.cardinality());
    assertEquals(RelationType.LOGICAL, relation.relationType());
    assertTrue(relation.isLogical());
  }

  @Test
  @DisplayName("load: 複合キーの論理リレーションはカラムをカンマ区切りで連結する")
  void testLoadRelationCompositeKey(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                relations:
                  - table: public.shipment
                    columns: [warehouse_code, zone_code]
                    parentTable: public.warehouse_zone
                    parentColumns: [warehouse_code, zone_code]
                """);

    ForeignKeyEntity relation = repository.load(file.toString()).logicalRelations().get(0);

    assertEquals("warehouse_code,zone_code", relation.columnNames());
    assertEquals("warehouse_code,zone_code", relation.referenceColumnNames());
  }

  @Test
  @DisplayName("load: 単一カラムはリストでなくスカラーでも指定できる")
  void testLoadRelationScalarColumns(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                relations:
                  - table: public.logs
                    columns: user_id
                    parentTable: public.users
                    parentColumns: id
                """);

    ForeignKeyEntity relation = repository.load(file.toString()).logicalRelations().get(0);

    assertEquals("user_id", relation.columnNames());
    assertEquals("id", relation.referenceColumnNames());
  }

  @Test
  @DisplayName("load: name 省略時は「テーブル名_カラム名_lrel」形式で関連名を自動生成する")
  void testLoadRelationGeneratesName(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                relations:
                  - table: public.logs
                    columns: [user_id]
                    parentTable: public.users
                    parentColumns: [id]
                """);

    assertEquals(
        "logs_user_id_lrel",
        repository.load(file.toString()).logicalRelations().get(0).foreignkeyName());
  }

  @Test
  @DisplayName("load: cardinality 省略時・未知の値の場合は既定の1対多とする")
  void testLoadRelationCardinalityFallback(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                relations:
                  - table: public.logs
                    columns: [user_id]
                    parentTable: public.users
                    parentColumns: [id]
                  - table: public.events
                    columns: [user_id]
                    parentTable: public.users
                    parentColumns: [id]
                    cardinality: 多対多
                """);

    List<ForeignKeyEntity> relations = repository.load(file.toString()).logicalRelations();

    assertEquals(Cardinality.ONE_TO_MANY, relations.get(0).cardinality());
    assertEquals(Cardinality.ONE_TO_MANY, relations.get(1).cardinality());
  }

  @Test
  @DisplayName("load: 必須項目が欠けている relations の要素は読み飛ばす")
  void testLoadRelationIgnoresIncomplete(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                relations:
                  - table: public.logs
                    parentTable: public.users
                    parentColumns: [id]
                  - table: public.logs
                    columns: [user_id]
                    parentColumns: [id]
                  - table: invalidkey
                    columns: [user_id]
                    parentTable: public.users
                    parentColumns: [id]
                  - table: public.events
                    columns: [user_id]
                    parentTable: public.users
                    parentColumns: [id]
                """);

    List<ForeignKeyEntity> relations = repository.load(file.toString()).logicalRelations();

    assertEquals(1, relations.size());
    assertEquals("events", relations.get(0).tableName());
  }

  @Test
  @DisplayName("load: relations キーが無い・リストでない場合は空のリストを返す")
  void testLoadNoRelations(@TempDir Path dir) throws IOException {
    Path noKey = writeYaml(dir, "tables:\n  public.users:\n    remarks: 備考\n");
    assertTrue(repository.load(noKey.toString()).logicalRelations().isEmpty());

    Path notList = dir.resolve("not_list.yml");
    Files.writeString(notList, "relations:\n  table: public.logs\n", StandardCharsets.UTF_8);
    assertTrue(repository.load(notList.toString()).logicalRelations().isEmpty());
  }

  @Test
  @DisplayName("load: tables と relations は同一ファイルに共存できる")
  void testLoadTablesAndRelationsTogether(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                tables:
                  public.logs:
                    remarks: 監査ログ
                relations:
                  - table: public.logs
                    columns: [user_id]
                    parentTable: public.users
                    parentColumns: [id]
                """);

    var sidecar = repository.load(file.toString());

    assertEquals("監査ログ", sidecar.annotations().of(table("public", "logs")).remarks());
    assertEquals(1, sidecar.logicalRelations().size());
  }

  @Test
  @DisplayName("load: relations のみを記述したサイドカーも有効に読み込める")
  void testLoadRelationsOnly(@TempDir Path dir) throws IOException {
    Path file =
        writeYaml(
            dir,
            """
                relations:
                  - table: public.logs
                    columns: [user_id]
                    parentTable: public.users
                    parentColumns: [id]
                """);

    var sidecar = repository.load(file.toString());

    assertTrue(sidecar.annotations().isEmpty());
    assertEquals(1, sidecar.logicalRelations().size());
  }
}
