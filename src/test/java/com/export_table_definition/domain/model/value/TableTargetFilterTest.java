package com.export_table_definition.domain.model.value;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TableTargetFilter のパターンマッチングに関するテスト */
public class TableTargetFilterTest {

  @Test
  @DisplayName("isEmpty: パターンが1件もない場合はtrue")
  void testIsEmptyWhenNoPatterns() {
    assertTrue(TableTargetFilter.of(List.of()).isEmpty());
    assertTrue(TableTargetFilter.of(null).isEmpty());
  }

  @Test
  @DisplayName("isEmpty: パターンが1件でもある場合はfalse")
  void testIsNotEmptyWhenPatternsExist() {
    assertFalse(TableTargetFilter.of(List.of("employee")).isEmpty());
  }

  @Test
  @DisplayName("matches: パターンが1件もない場合はすべてのテーブルに一致する")
  void testMatchesAllWhenEmpty() {
    TableTargetFilter filter = TableTargetFilter.of(List.of());
    assertTrue(filter.matches("public", "employee"));
    assertTrue(filter.matches("other", "anything"));
  }

  @Test
  @DisplayName("of: 前後に空白があっても、除外パターンは除外として解釈される")
  void testExcludePatternWithSurroundingSpaces() {
    // 「table=!flyway_schema_history, !tmp_*」のようにカンマの後へ空白を入れた場合を想定する
    TableTargetFilter filter = TableTargetFilter.of(List.of("!flyway_schema_history", " !tmp_* "));
    assertTrue(filter.matches("public", "employee"));
    assertFalse(filter.matches("public", "tmp_work"));
    assertFalse(filter.matches("public", "flyway_schema_history"));
  }

  @Test
  @DisplayName("matches: 完全一致パターンは同名のテーブルのみに一致する")
  void testMatchesExactPattern() {
    TableTargetFilter filter = TableTargetFilter.of(List.of("employee"));
    assertTrue(filter.matches("public", "employee"));
    assertFalse(filter.matches("public", "employee_bk"));
  }

  @Test
  @DisplayName("matches: ワイルドカード（*）は任意の文字列に一致する")
  void testMatchesWildcardPattern() {
    TableTargetFilter filter = TableTargetFilter.of(List.of("*_bk"));
    assertTrue(filter.matches("public", "employee_bk"));
    assertTrue(filter.matches("public", "_bk"));
    assertFalse(filter.matches("public", "employee"));
  }

  @Test
  @DisplayName("matches: 除外パターン（!）に一致するテーブルは常に対象外")
  void testExcludePatternWins() {
    TableTargetFilter filter = TableTargetFilter.of(List.of("!flyway_schema_history"));
    assertFalse(filter.matches("public", "flyway_schema_history"));
    // 除外パターンのみの場合、それ以外のテーブルはすべて対象
    assertTrue(filter.matches("public", "employee"));
  }

  @Test
  @DisplayName("matches: 除外パターンのワイルドカードも一時テーブルの除外に使える")
  void testExcludeWildcardPattern() {
    TableTargetFilter filter = TableTargetFilter.of(List.of("!*_bk", "!*_20240101"));
    assertFalse(filter.matches("public", "employee_bk"));
    assertFalse(filter.matches("public", "employee_20240101"));
    assertTrue(filter.matches("public", "employee"));
  }

  @Test
  @DisplayName("matches: 包含・除外の両方が指定された場合、除外が優先される")
  void testExcludeTakesPrecedenceOverInclude() {
    TableTargetFilter filter = TableTargetFilter.of(List.of("employee*", "!employee_bk"));
    assertTrue(filter.matches("public", "employee"));
    assertFalse(filter.matches("public", "employee_bk"));
  }

  @Test
  @DisplayName("matches: スキーマ修飾パターンは指定したスキーマのテーブルにのみ一致する")
  void testSchemaQualifiedPattern() {
    TableTargetFilter filter = TableTargetFilter.of(List.of("sample.employee"));
    assertTrue(filter.matches("sample", "employee"));
    assertFalse(filter.matches("other", "employee"));
  }

  @Test
  @DisplayName("matches: スキーマ修飾なしのパターンは全スキーマのテーブルに一致する（従来互換）")
  void testUnqualifiedPatternMatchesAllSchemas() {
    TableTargetFilter filter = TableTargetFilter.of(List.of("employee"));
    assertTrue(filter.matches("sample", "employee"));
    assertTrue(filter.matches("other", "employee"));
  }

  @Test
  @DisplayName("matches: スキーマ修飾パターンとワイルドカードを組み合わせられる")
  void testSchemaQualifiedWildcardPattern() {
    TableTargetFilter filter = TableTargetFilter.of(List.of("sample.*_bk"));
    assertTrue(filter.matches("sample", "employee_bk"));
    assertFalse(filter.matches("other", "employee_bk"));
    assertFalse(filter.matches("sample", "employee"));
  }

  @Test
  @DisplayName("matches: 空白のみのパターンは無視される")
  void testBlankPatternsAreIgnored() {
    TableTargetFilter filter = TableTargetFilter.of(List.of("  ", "employee"));
    assertTrue(filter.matches("public", "employee"));
    assertFalse(filter.matches("public", "other"));
  }
}
