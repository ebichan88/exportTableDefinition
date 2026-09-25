package com.export_table_definition.domain.model.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.value.TableKey;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ForeignKeyEntity の関連名解決（resolveLogicalRelationName）に関するテスト */
public class ForeignKeyEntityTest {

  @Test
  @DisplayName("resolveLogicalRelationName: 明示指定された名称はそのままトリムして用いる")
  void testResolveLogicalRelationNameUsesExplicitName() {
    assertEquals(
        "rel_logs_users",
        ForeignKeyEntity.resolveLogicalRelationName(
            "  rel_logs_users  ", "logs", List.of("user_id")));
  }

  @Test
  @DisplayName("resolveLogicalRelationName: 未指定・空白の場合は「テーブル名_列名..._lrel」形式を自動生成する")
  void testResolveLogicalRelationNameGeneratesDefault() {
    assertEquals(
        "logs_user_id_lrel",
        ForeignKeyEntity.resolveLogicalRelationName(null, "logs", List.of("user_id")));
    assertEquals(
        "logs_user_id_lrel",
        ForeignKeyEntity.resolveLogicalRelationName("", "logs", List.of("user_id")));
    assertEquals(
        "logs_user_id_lrel",
        ForeignKeyEntity.resolveLogicalRelationName("   ", "logs", List.of("user_id")));
  }

  @Test
  @DisplayName("resolveLogicalRelationName: 複合キーの場合は列名をアンダースコアで連結する")
  void testResolveLogicalRelationNameJoinsCompositeColumns() {
    assertEquals(
        "order_items_order_id_item_no_lrel",
        ForeignKeyEntity.resolveLogicalRelationName(
            null, "order_items", List.of("order_id", "item_no")));
  }

  @Test
  @DisplayName("referenceTableKey: 参照先のスキーマ名・テーブル名からTableKeyを組み立てる")
  void testReferenceTableKey() {
    ForeignKeyEntity fk =
        new ForeignKeyEntity("public", "orders", "fk1", "customer_id", "public", "customers", "id");
    assertEquals(TableKey.of("public", "customers"), fk.referenceTableKey());
  }
}
