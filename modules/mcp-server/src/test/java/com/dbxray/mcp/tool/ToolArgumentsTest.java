package com.dbxray.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dbxray.mcp.catalog.Direction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link ToolArguments}のテスト */
class ToolArgumentsTest {

  @Test
  @DisplayName("引数がnullの場合は引数なしとみなす")
  void treatsNullAsNoArguments() {
    assertEquals("", new ToolArguments(null).optionalString("schema"));
  }

  @Test
  @DisplayName("文字列は前後の空白を除き、必須の引数が空なら失敗にする")
  void readsStrings() {
    final ToolArguments arguments = arguments(Map.of("table", "  employee ", "schema", " "));

    assertEquals("employee", arguments.requiredString("table"));
    assertEquals("", arguments.optionalString("schema"));
    assertThrows(InvalidToolArgumentException.class, () -> arguments.requiredString("schema"));
    assertThrows(
        InvalidToolArgumentException.class,
        () -> arguments(Map.of("table", 1)).optionalString("table"));
  }

  @Test
  @DisplayName("整数は数値を受け付け、未指定なら既定値を返す。数値でない値は失敗にする")
  void readsIntegers() {
    assertEquals(3, arguments(Map.of("depth", 3)).optionalInt("depth", 1));
    assertEquals(3, arguments(Map.of("depth", 3L)).optionalInt("depth", 1));
    assertEquals(1, arguments(Map.of()).optionalInt("depth", 1));
    final Map<String, Object> nullValue = new HashMap<>();
    nullValue.put("depth", null);
    assertEquals(1, arguments(nullValue).optionalInt("depth", 1));
    assertThrows(
        InvalidToolArgumentException.class,
        () -> arguments(Map.of("depth", "3")).optionalInt("depth", 1));
  }

  @Test
  @DisplayName("列挙値は小文字の定数名を完全一致で受け付け、該当しない値は候補を示して失敗にする")
  void readsEnums() {
    assertEquals(
        Direction.INCOMING,
        arguments(Map.of("direction", "incoming"))
            .optionalEnum("direction", Direction.class, Direction.BOTH));
    assertEquals(
        Direction.BOTH,
        arguments(Map.of()).optionalEnum("direction", Direction.class, Direction.BOTH));

    final InvalidToolArgumentException e =
        assertThrows(
            InvalidToolArgumentException.class,
            () ->
                arguments(Map.of("direction", "Incoming"))
                    .optionalEnum("direction", Direction.class, Direction.BOTH));
    assertTrue(e.getMessage().contains("outgoing, incoming, both"), e.getMessage());
  }

  @Test
  @DisplayName("真偽値は、JSONの真偽値を受け付け、未指定なら既定値を返す。文字列は失敗にする")
  void readsBooleans() {
    assertTrue(arguments(Map.of("flag", true)).optionalBoolean("flag", false));
    assertFalse(arguments(Map.of("flag", false)).optionalBoolean("flag", true));
    assertTrue(arguments(Map.of()).optionalBoolean("flag", true));
    assertThrows(
        InvalidToolArgumentException.class,
        () -> arguments(Map.of("flag", "true")).optionalBoolean("flag", false));
  }

  @Test
  @DisplayName("文字列のリストは、配列とカンマ区切りの文字列を受け付け、空白・空の要素・重複を除く")
  void readsStringLists() {
    assertEquals(
        List.of("columns", "indexes"),
        arguments(Map.of("list", List.of(" columns", "indexes", "", "columns")))
            .optionalStringList("list"));
    assertEquals(
        List.of("columns", "indexes"),
        arguments(Map.of("list", "columns, indexes")).optionalStringList("list"));
    assertEquals(List.of(), arguments(Map.of()).optionalStringList("list"));
    assertThrows(
        InvalidToolArgumentException.class,
        () -> arguments(Map.of("list", List.of(1))).optionalStringList("list"));
  }

  @Test
  @DisplayName("決まった値のいずれかを取る引数は、完全一致で受け付け、未指定は空文字にする")
  void readsChoices() {
    final List<String> choices = List.of("table", "view");

    assertEquals("view", arguments(Map.of("type", "view")).optionalChoice("type", choices));
    assertEquals("", arguments(Map.of()).optionalChoice("type", choices));
    final InvalidToolArgumentException e =
        assertThrows(
            InvalidToolArgumentException.class,
            () -> arguments(Map.of("type", "VIEW")).optionalChoice("type", choices));
    assertEquals("引数typeにはtable, viewのいずれかを指定してください。 [value=VIEW]", e.getMessage());
  }

  private static ToolArguments arguments(Map<String, Object> values) {
    return new ToolArguments(values);
  }
}
