package com.export_table_definition.domain.model.collection;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ForeignKeyGroups の連結成分分解に関するテスト */
public class ForeignKeyGroupsTest {

  private ForeignKeyEntity fk(
      String schema, String table, String name, String refSchema, String refTable) {
    return ForeignKeyFixtures.physical(schema, table, name, refSchema, refTable);
  }

  @Test
  @DisplayName("connectedComponents: 外部キーがない場合は空のリストを返す")
  void testEmpty() {
    assertEquals(List.of(), ForeignKeyGroups.connectedComponents(List.of()));
  }

  @Test
  @DisplayName("connectedComponents: 繋がっていない2つのまとまりは別の成分になる")
  void testSeparateComponents() {
    var fk1 = fk("public", "orders", "fk1", "public", "customers");
    var fk2 = fk("public", "logs", "fk2", "public", "users");
    var components = ForeignKeyGroups.connectedComponents(List.of(fk1, fk2));
    assertEquals(2, components.size());
    // いずれの成分も2テーブル・外部キー1本
    assertTrue(components.stream().allMatch(component -> component.size() == 1));
    assertTrue(
        components.stream().allMatch(component -> ForeignKeyGroups.nodeCount(component) == 2));
  }

  @Test
  @DisplayName("connectedComponents: a→b と b→c は1つの成分に併合される")
  void testChainedComponent() {
    var fk1 = fk("public", "a", "fk1", "public", "b");
    var fk2 = fk("public", "b", "fk2", "public", "c");
    var components = ForeignKeyGroups.connectedComponents(List.of(fk1, fk2));
    assertEquals(1, components.size());
    assertEquals(2, components.get(0).size());
    assertEquals(3, ForeignKeyGroups.nodeCount(components.get(0)));
  }

  @Test
  @DisplayName("connectedComponents: 共通の参照先を持つ外部キーは1つの成分に併合される")
  void testSharedReferenceComponent() {
    var fk1 = fk("public", "a", "fk1", "public", "hub");
    var fk2 = fk("public", "b", "fk2", "public", "hub");
    var fk3 = fk("public", "c", "fk3", "public", "hub");
    var components = ForeignKeyGroups.connectedComponents(List.of(fk1, fk2, fk3));
    assertEquals(1, components.size());
    assertEquals(4, ForeignKeyGroups.nodeCount(components.get(0)));
  }

  @Test
  @DisplayName("connectedComponents: 自己参照は1テーブルの成分になる")
  void testSelfReference() {
    var selfFk = fk("public", "categories", "fk_parent", "public", "categories");
    var components = ForeignKeyGroups.connectedComponents(List.of(selfFk));
    assertEquals(1, components.size());
    assertEquals(1, ForeignKeyGroups.nodeCount(components.get(0)));
  }

  @Test
  @DisplayName("connectedComponents: スキーマを跨ぐ外部キーは成分を跨いで併合する")
  void testCrossSchemaComponent() {
    var fk1 = fk("sales", "orders", "fk1", "master", "customers");
    var fk2 = fk("sales", "items", "fk2", "sales", "orders");
    var components = ForeignKeyGroups.connectedComponents(List.of(fk1, fk2));
    assertEquals(1, components.size());
    assertEquals(3, ForeignKeyGroups.nodeCount(components.get(0)));
  }

  @Test
  @DisplayName("connectedComponents: ノード数の降順で返す")
  void testOrderedByNodeCountDesc() {
    var small = fk("public", "x", "fk_small", "public", "y");
    var large1 = fk("public", "a", "fk_large1", "public", "b");
    var large2 = fk("public", "b", "fk_large2", "public", "c");
    var components = ForeignKeyGroups.connectedComponents(List.of(small, large1, large2));
    assertEquals(2, components.size());
    assertEquals(3, ForeignKeyGroups.nodeCount(components.get(0)));
    assertEquals(2, ForeignKeyGroups.nodeCount(components.get(1)));
  }

  @Test
  @DisplayName("connectedComponents: ノード数が同じ場合は先頭テーブル名順で安定して並ぶ")
  void testStableOrderForSameSize() {
    var zz = fk("public", "zz1", "fk_zz", "public", "zz2");
    var aa = fk("public", "aa1", "fk_aa", "public", "aa2");
    var components = ForeignKeyGroups.connectedComponents(List.of(zz, aa));
    assertEquals("fk_aa", components.get(0).get(0).foreignkeyName());
    assertEquals("fk_zz", components.get(1).get(0).foreignkeyName());
  }

  @Test
  @DisplayName("mainTable: 最も多くの外部キーが接続するテーブルを返す")
  void testMainTable() {
    var fk1 = fk("public", "a", "fk1", "public", "hub");
    var fk2 = fk("public", "b", "fk2", "public", "hub");
    var fk3 = fk("public", "c", "fk3", "public", "hub");
    assertEquals(TableKey.of("public", "hub"), ForeignKeyGroups.mainTable(List.of(fk1, fk2, fk3)));
  }

  @Test
  @DisplayName("mainTable: 同数の場合はスキーマ名・テーブル名順で先頭のものを返す")
  void testMainTableTieBreak() {
    var fk1 = fk("public", "zzz", "fk1", "public", "aaa");
    assertEquals(TableKey.of("public", "aaa"), ForeignKeyGroups.mainTable(List.of(fk1)));
  }
}
