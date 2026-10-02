package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.table.TableKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MermaidSupport のエンティティ識別子・表示ラベル生成に関するテスト */
public class MermaidSupportTest {

  @Test
  @DisplayName("mermaidId: スキーマ名とテーブル名をアンダースコアで連結する")
  void testMermaidId() {
    assertEquals("public_orders", MermaidSupport.mermaidId(TableKey.of("public", "orders")));
  }

  @Test
  @DisplayName("assignLabels: 同名テーブルが無ければテーブル名のみをラベルにする")
  void testAssignLabelsWithoutCollision() {
    var orders = TableKey.of("public", "orders");
    var customers = TableKey.of("public", "customers");

    Map<TableKey, String> labels = MermaidSupport.assignLabels(List.of(orders, customers));

    assertEquals("orders", labels.get(orders));
    assertEquals("customers", labels.get(customers));
  }

  @Test
  @DisplayName("assignLabels: 同じ図内に同名テーブルが複数スキーマにまたがる場合はスキーマ.テーブルにする")
  void testAssignLabelsWithCollisionAcrossSchemas() {
    var salesCustomers = TableKey.of("sales", "customers");
    var masterCustomers = TableKey.of("master", "customers");

    Map<TableKey, String> labels =
        MermaidSupport.assignLabels(List.of(salesCustomers, masterCustomers));

    assertEquals("sales.customers", labels.get(salesCustomers));
    assertEquals("master.customers", labels.get(masterCustomers));
  }

  @Test
  @DisplayName("assignLabels: 衝突していないテーブルは同じ図内に衝突テーブルがあってもテーブル名のみのまま")
  void testAssignLabelsOnlyQualifiesCollidingTables() {
    var salesCustomers = TableKey.of("sales", "customers");
    var masterCustomers = TableKey.of("master", "customers");
    var orders = TableKey.of("sales", "orders");

    Map<TableKey, String> labels =
        MermaidSupport.assignLabels(List.of(salesCustomers, masterCustomers, orders));

    assertEquals("sales.customers", labels.get(salesCustomers));
    assertEquals("master.customers", labels.get(masterCustomers));
    assertEquals("orders", labels.get(orders));
  }

  @Test
  @DisplayName("aliasLine: 識別子とラベルを角括弧・二重引用符で結んだ1行を出力する")
  void testAliasLine() {
    assertEquals(
        "    public_orders[\"orders\"]" + System.lineSeparator(),
        MermaidSupport.aliasLine("public_orders", "orders"));
  }
}
