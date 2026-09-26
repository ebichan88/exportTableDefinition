package com.export_table_definition.presentation.type;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ExitStatus の終了コードに関するテスト（READMEに記載した終了コードの仕様） */
public class ExitStatusTest {

  @Test
  @DisplayName("終了コードは、成功（差分なし）が0、差分ありが1、失敗が2")
  void testCodes() {
    assertEquals(0, ExitStatus.SUCCESS.code());
    assertEquals(1, ExitStatus.DIFFERENCE_FOUND.code());
    assertEquals(2, ExitStatus.FAILURE.code());
  }
}
