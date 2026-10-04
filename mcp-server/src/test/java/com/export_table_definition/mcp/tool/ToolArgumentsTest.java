package com.export_table_definition.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.catalog.Direction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link ToolArguments}のテスト */
class ToolArgumentsTest {

  @Test
  @DisplayName("受け付けない引数名がある場合は、使える引数を示して失敗にする")
  void rejectsUnknownArgument() {
    final InvalidToolArgumentException e =
        assertThrows(
            InvalidToolArgumentException.class,
            () -> new ToolArguments(Map.of("tabel", "x"), Set.of("table", "schema")));

    assertEquals("未知の引数です: tabel。使える引数: schema, table", e.getMessage());
  }

  @Test
  @DisplayName("引数がnullの場合は引数なしとみなす")
  void treatsNullAsNoArguments() {
    assertEquals("", new ToolArguments(null, Set.of("schema")).optionalString("schema"));
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
  @DisplayName("整数は数値・小数部が0の数値・数字の文字列を受け付け、未指定なら既定値を返す")
  void readsIntegers() {
    assertEquals(3, arguments(Map.of("depth", 3)).optionalInt("depth", 1, 1, 3));
    assertEquals(3, arguments(Map.of("depth", 3L)).optionalInt("depth", 1, 1, 3));
    assertEquals(3, arguments(Map.of("depth", 3.0)).optionalInt("depth", 1, 1, 3));
    assertEquals(3, arguments(Map.of("depth", "3")).optionalInt("depth", 1, 1, 3));
    assertEquals(1, arguments(Map.of()).optionalInt("depth", 1, 1, 3));
    final Map<String, Object> nullValue = new HashMap<>();
    nullValue.put("depth", null);
    assertEquals(1, arguments(nullValue).optionalInt("depth", 1, 1, 3));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "4", "1.5", "abc"})
  @DisplayName("範囲外・整数でない値は、範囲を示して失敗にする")
  void rejectsInvalidIntegers(String value) {
    final Object argument = value.equals("1.5") ? (Object) 1.5 : value;
    final InvalidToolArgumentException e =
        assertThrows(
            InvalidToolArgumentException.class,
            () -> arguments(Map.of("depth", argument)).optionalInt("depth", 1, 1, 3));

    assertTrue(e.getMessage().contains("1〜3の整数"), e.getMessage());
  }

  @Test
  @DisplayName("列挙値は小文字の定数名を大文字小文字を区別せず受け付け、該当しない値は候補を示して失敗にする")
  void readsEnums() {
    assertEquals(
        Direction.INCOMING,
        arguments(Map.of("direction", "Incoming"))
            .optionalEnum("direction", Direction.class, Direction.BOTH));
    assertEquals(
        Direction.BOTH,
        arguments(Map.of()).optionalEnum("direction", Direction.class, Direction.BOTH));

    final InvalidToolArgumentException e =
        assertThrows(
            InvalidToolArgumentException.class,
            () ->
                arguments(Map.of("direction", "up"))
                    .optionalEnum("direction", Direction.class, Direction.BOTH));
    assertTrue(e.getMessage().contains("outgoing, incoming, both"), e.getMessage());
  }

  @Test
  @DisplayName("真偽値は、JSONの真偽値とtrue・falseの文字列を受け付け、それ以外は失敗にする")
  void readsBooleans() {
    assertTrue(arguments(Map.of("flag", true)).optionalBoolean("flag", false));
    assertFalse(arguments(Map.of("flag", " FALSE ")).optionalBoolean("flag", true));
    assertTrue(arguments(Map.of()).optionalBoolean("flag", true));
    assertThrows(
        InvalidToolArgumentException.class,
        () -> arguments(Map.of("flag", "yes")).optionalBoolean("flag", false));
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
  @DisplayName("決まった値のいずれかを取る引数は、大文字小文字を区別せず受け付け、未指定は空文字にする")
  void readsChoices() {
    final List<String> choices = List.of("table", "view");

    assertEquals("view", arguments(Map.of("type", "VIEW")).optionalChoice("type", choices));
    assertEquals("", arguments(Map.of()).optionalChoice("type", choices));
    final InvalidToolArgumentException e =
        assertThrows(
            InvalidToolArgumentException.class,
            () -> arguments(Map.of("type", "index")).optionalChoice("type", choices));
    assertEquals("引数typeにはtable, viewのいずれかを指定してください。 [value=index]", e.getMessage());
  }

  private static ToolArguments arguments(Map<String, Object> values) {
    return new ToolArguments(
        values, Set.of("table", "schema", "depth", "direction", "flag", "list", "type"));
  }
}
