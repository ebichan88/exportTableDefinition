package com.export_table_definition.domain.model.relation;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableType;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ForeignKeys のインデックス化（参照側・被参照側）に関するテスト */
public class ForeignKeysTest {

  private TableEntity newTable(String schema, String physical) {
    return new TableEntity("TEST_DB", schema, "", physical, TableType.TABLE, "");
  }

  @Test
  @DisplayName("of: 参照側(of)は自テーブルが保有する外部キーを返す")
  void testOfReturnsOwnForeignKeys() {
    var fk =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var foreignKeys = ForeignKeys.of(List.of(fk));

    assertEquals(List.of(fk), foreignKeys.of(newTable("public", "orders")));
    assertEquals(List.of(), foreignKeys.of(newTable("public", "customers")));
  }

  @Test
  @DisplayName("incomingOf: 被参照側は自テーブルを参照している外部キーを返す")
  void testIncomingOfReturnsReferencingForeignKeys() {
    var fk =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var foreignKeys = ForeignKeys.of(List.of(fk));

    assertEquals(List.of(fk), foreignKeys.incomingOf(newTable("public", "customers")));
    assertEquals(List.of(), foreignKeys.incomingOf(newTable("public", "orders")));
  }

  @Test
  @DisplayName("incomingOf: 自己参照の外部キーは被参照側に含まれない（外部キー情報セクションとの重複表示を避けるため）")
  void testIncomingOfExcludesSelfReference() {
    var selfFk =
        ForeignKeyFixtures.physical(
            "public", "categories", "fk_categories_parent", "public", "categories");
    var foreignKeys = ForeignKeys.of(List.of(selfFk));

    assertEquals(List.of(selfFk), foreignKeys.of(newTable("public", "categories")));
    assertEquals(List.of(), foreignKeys.incomingOf(newTable("public", "categories")));
  }

  @Test
  @DisplayName("incomingOf: スキーマを跨いだ参照でも正しく解決される")
  void testIncomingOfAcrossSchemas() {
    var fk = ForeignKeyFixtures.physical("hr", "assignment", "fk_assignment_emp", "sales", "emp");
    var foreignKeys = ForeignKeys.of(List.of(fk));

    assertEquals(List.of(fk), foreignKeys.incomingOf(newTable("sales", "emp")));
  }

  @Test
  @DisplayName("groupBySchema: スキーマ跨ぎの外部キーは参照元・参照先の双方のスキーマに登録される")
  void testGroupBySchemaRegistersBothSides() {
    var crossFk =
        ForeignKeyFixtures.physical("hr", "assignment", "fk_assignment_emp", "sales", "emp");
    var bySchema = ForeignKeys.of(List.of(crossFk)).groupBySchema();

    assertEquals(List.of(crossFk), bySchema.get("hr"));
    assertEquals(List.of(crossFk), bySchema.get("sales"));
  }

  @Test
  @DisplayName("groupBySchema: 同一スキーマ内の外部キーは1度だけ登録される")
  void testGroupBySchemaRegistersSameSchemaOnce() {
    var fk =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var bySchema = ForeignKeys.of(List.of(fk)).groupBySchema();

    assertEquals(List.of(fk), bySchema.get("public"));
    assertEquals(1, bySchema.size());
  }

  @Test
  @DisplayName("groupBySchema: 自己参照の外部キーも所属スキーマに登録される")
  void testGroupBySchemaWithSelfReference() {
    var selfFk =
        ForeignKeyFixtures.physical(
            "public", "categories", "fk_categories_parent", "public", "categories");
    var bySchema = ForeignKeys.of(List.of(selfFk)).groupBySchema();

    assertEquals(List.of(selfFk), bySchema.get("public"));
  }

  @Test
  @DisplayName("crossSchema: スキーマを跨ぐ外部キーのみを返す")
  void testCrossSchema() {
    var sameSchemaFk =
        ForeignKeyFixtures.physical("public", "orders", "fk_same", "public", "customers");
    var crossSchemaFk = ForeignKeyFixtures.physical("hr", "assignment", "fk_cross", "sales", "emp");
    var foreignKeys = ForeignKeys.of(List.of(sameSchemaFk, crossSchemaFk));

    assertEquals(List.of(crossSchemaFk), foreignKeys.crossSchema());
  }

  @Test
  @DisplayName("physicalOf: 物理外部キーのみを返し、論理リレーションは含めない")
  void testPhysicalOfExcludesLogical() {
    var physical =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var logical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_staff", "public", "staff");
    var foreignKeys = ForeignKeys.of(List.of(physical, logical));

    assertEquals(List.of(physical), foreignKeys.physicalOf(newTable("public", "orders")));
  }

  @Test
  @DisplayName("logicalOf: 論理リレーションのみを返し、物理外部キーは含めない")
  void testLogicalOfExcludesPhysical() {
    var physical =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var logical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_staff", "public", "staff");
    var foreignKeys = ForeignKeys.of(List.of(physical, logical));

    assertEquals(List.of(logical), foreignKeys.logicalOf(newTable("public", "orders")));
  }

  @Test
  @DisplayName("of: 論理リレーションも物理外部キーと同じ集合として扱う（ER図で同一のグラフに描くため）")
  void testOfIncludesBothRelationTypes() {
    var physical =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var logical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_staff", "public", "staff");
    var foreignKeys = ForeignKeys.of(List.of(physical, logical));

    assertEquals(List.of(physical, logical), foreignKeys.of(newTable("public", "orders")));
  }

  @Test
  @DisplayName("incomingOf: 論理リレーションも被参照側として解決される")
  void testIncomingOfResolvesLogical() {
    var logical =
        ForeignKeyFixtures.logical(
            "public", "audit_log", "rel_audit_employee", "public", "employee");
    var foreignKeys = ForeignKeys.of(List.of(logical));

    assertEquals(List.of(logical), foreignKeys.incomingOf(newTable("public", "employee")));
  }

  @Test
  @DisplayName("groupBySchema: スキーマを跨ぐ論理リレーションも双方のスキーマに登録される")
  void testGroupBySchemaIncludesCrossSchemaLogical() {
    var logical =
        ForeignKeyFixtures.logical("hr", "assignment", "rel_assignment_emp", "sales", "emp");
    var bySchema = ForeignKeys.of(List.of(logical)).groupBySchema();

    assertEquals(List.of(logical), bySchema.get("hr"));
    assertEquals(List.of(logical), bySchema.get("sales"));
  }
}
