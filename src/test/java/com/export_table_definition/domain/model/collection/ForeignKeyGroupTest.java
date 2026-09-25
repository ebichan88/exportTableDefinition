package com.export_table_definition.domain.model.collection;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ForeignKeyGroup のノード算出・規模の判定に関するテスト */
public class ForeignKeyGroupTest {

  private ForeignKeyEntity fk(
      String schema, String table, String name, String refSchema, String refTable) {
    return ForeignKeyFixtures.physical(schema, table, name, refSchema, refTable);
  }

  @Test
  @DisplayName("nodes: 外部キーの両端を重複なく、スキーマ名・テーブル名順に並べて返す")
  void testNodes() {
    var fk1 = fk("public", "orders", "fk_orders_customer", "master", "customers");
    var fk2 = fk("public", "items", "fk_items_orders", "public", "orders");
    var group = ForeignKeyGroup.of(List.of(fk1, fk2));
    assertEquals(
        List.of(
            TableKey.of("master", "customers"),
            TableKey.of("public", "items"),
            TableKey.of("public", "orders")),
        group.nodes());
    assertEquals(3, group.nodeCount());
  }

  @Test
  @DisplayName("exceeds: ノード数が上限を超える場合のみtrue。上限が0以下の場合は常にfalse")
  void testExceeds() {
    var group =
        ForeignKeyGroup.of(
            List.of(
                fk("public", "a", "fk1", "public", "b"), fk("public", "b", "fk2", "public", "c")));
    assertTrue(group.exceeds(2));
    assertFalse(group.exceeds(3));
    assertFalse(group.exceeds(0));
    assertFalse(group.exceeds(-1));
  }

  @Test
  @DisplayName("mainTable: 最も多くの外部キーが接続するテーブルを返す")
  void testMainTable() {
    var fk1 = fk("public", "a", "fk1", "public", "hub");
    var fk2 = fk("public", "b", "fk2", "public", "hub");
    var fk3 = fk("public", "c", "fk3", "public", "hub");
    assertEquals(
        TableKey.of("public", "hub"), ForeignKeyGroup.of(List.of(fk1, fk2, fk3)).mainTable());
  }

  @Test
  @DisplayName("mainTable: 同数の場合はスキーマ名・テーブル名順で先頭のものを返す")
  void testMainTableTieBreak() {
    var fk1 = fk("public", "zzz", "fk1", "public", "aaa");
    assertEquals(TableKey.of("public", "aaa"), ForeignKeyGroup.of(List.of(fk1)).mainTable());
  }
}
