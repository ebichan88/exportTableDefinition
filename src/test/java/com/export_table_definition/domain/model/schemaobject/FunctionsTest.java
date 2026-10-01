package com.export_table_definition.domain.model.schemaobject;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Functions の取得順の保持・空判定・スキーマ名の抽出に関するテスト */
public class FunctionsTest {

  private FunctionEntity function(String schema, String name) {
    return new FunctionEntity("testdb", schema, name, 1, 1, "", "", "", "", "");
  }

  @Test
  @DisplayName("asList: 渡した順序のまま返す")
  void testAsListKeepsOrder() {
    var f1 = function("sales", "f1");
    var f2 = function("public", "f2");
    var f3 = function("sales", "f3");

    assertEquals(List.of(f1, f2, f3), Functions.of(List.of(f1, f2, f3)).asList());
  }

  @Test
  @DisplayName("isEmpty: 1件も無い場合のみtrueを返す")
  void testIsEmpty() {
    assertTrue(Functions.of(List.of()).isEmpty());
    assertFalse(Functions.of(List.of(function("public", "f1"))).isEmpty());
  }

  @Test
  @DisplayName("schemaNames: 重複を除いたスキーマ名を、初めて現れた順に返す")
  void testSchemaNamesDistinctInOrder() {
    var functions =
        Functions.of(
            List.of(function("sales", "f1"), function("public", "f2"), function("sales", "f3")));

    assertEquals(List.of("sales", "public"), functions.schemaNames());
  }

  @Test
  @DisplayName("schemaNames: 関数が無い場合は空リストを返す")
  void testSchemaNamesEmpty() {
    assertEquals(List.of(), Functions.of(List.of()).schemaNames());
  }
}
