package com.export_table_definition.domain.model.schemaobject;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Types の取得順の保持・空判定・反復に関するテスト */
public class TypesTest {

  private TypeEntity type(String name) {
    return new TypeEntity("testdb", "public", name, "enum", "");
  }

  @Test
  @DisplayName("asList・iterator: 渡した順序のまま返す")
  void testKeepsOrder() {
    var t1 = type("t2");
    var t2 = type("t1");
    var types = Types.of(List.of(t1, t2));

    var iterated = new ArrayList<TypeEntity>();
    types.forEach(iterated::add);

    assertEquals(List.of(t1, t2), types.asList());
    assertEquals(List.of(t1, t2), iterated);
  }

  @Test
  @DisplayName("isEmpty: 1件も無い場合のみtrueを返す")
  void testIsEmpty() {
    assertTrue(Types.of(List.of()).isEmpty());
    assertFalse(Types.of(List.of(type("t1"))).isEmpty());
  }
}
