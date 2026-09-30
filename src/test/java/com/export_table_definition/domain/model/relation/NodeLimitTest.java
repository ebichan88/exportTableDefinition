package com.export_table_definition.domain.model.relation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** NodeLimit の正規化と上限判定に関するテスト */
public class NodeLimitTest {

  @Test
  @DisplayName("of: 0以下の値は上限なしに正規化する")
  void testOfNormalizesNonPositive() {
    assertEquals(NodeLimit.UNLIMITED, NodeLimit.of(0));
    assertEquals(NodeLimit.UNLIMITED, NodeLimit.of(-5));
    assertEquals(80, NodeLimit.of(80).value());
  }

  @Test
  @DisplayName("isExceededBy: 上限を超える場合のみtrue。上限なしは常にfalse")
  void testIsExceededBy() {
    assertFalse(NodeLimit.of(3).isExceededBy(3));
    assertTrue(NodeLimit.of(3).isExceededBy(4));
    assertFalse(NodeLimit.UNLIMITED.isExceededBy(Integer.MAX_VALUE));
  }
}
