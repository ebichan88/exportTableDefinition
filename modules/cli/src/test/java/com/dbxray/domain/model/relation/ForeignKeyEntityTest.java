package com.dbxray.domain.model.relation;

import static org.junit.jupiter.api.Assertions.*;

import com.dbxray.domain.model.table.TableKey;
import com.dbxray.testsupport.ForeignKeyFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ForeignKeyEntity の関連名解決（resolveLogicalRelationName）に関するテスト */
public class ForeignKeyEntityTest {

  @Test
  @DisplayName("resolveLogicalRelationName: 単一カラムの場合はカラム名をそのまま用いる")
  void testResolveLogicalRelationNameSingleColumn() {
    assertEquals("user_id", ForeignKeyEntity.resolveLogicalRelationName(List.of("user_id")));
  }

  @Test
  @DisplayName("resolveLogicalRelationName: 複合キーの場合は列名をカンマで連結する")
  void testResolveLogicalRelationNameJoinsCompositeColumns() {
    assertEquals(
        "order_id,item_no",
        ForeignKeyEntity.resolveLogicalRelationName(List.of("order_id", "item_no")));
  }

  @Test
  @DisplayName("referenceTableKey: 参照先のスキーマ名・テーブル名からTableKeyを組み立てる")
  void testReferenceTableKey() {
    ForeignKeyEntity fk =
        ForeignKeyFixtures.physical("public", "orders", "fk1", "public", "customers");
    assertEquals(TableKey.of("public", "customers"), fk.referenceTableKey());
  }
}
