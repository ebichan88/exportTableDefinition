package com.dbxray.domain.model.table;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TableNamePatterns のパターンマッチングに関するテスト */
public class TableNamePatternsTest {

  @Test
  @DisplayName("isEmpty: パターンが1件もない場合はtrue")
  void testIsEmptyWhenNoPatterns() {
    assertTrue(TableNamePatterns.of(List.of()).isEmpty());
    assertTrue(TableNamePatterns.of(null).isEmpty());
  }

  @Test
  @DisplayName("isEmpty: パターンが1件でもある場合はfalse")
  void testIsNotEmptyWhenPatternsExist() {
    assertFalse(TableNamePatterns.of(List.of("employee")).isEmpty());
  }

  @Test
  @DisplayName("matches: パターンが1件もない場合はすべてのテーブルに一致する")
  void testMatchesAllWhenEmpty() {
    TableNamePatterns filter = TableNamePatterns.of(List.of());
    assertTrue(filter.matches(TableKey.of("public", "employee")));
    assertTrue(filter.matches(TableKey.of("other", "anything")));
  }

  @Test
  @DisplayName("of: 前後に空白があっても、除外パターンは除外として解釈される")
  void testExcludePatternWithSurroundingSpaces() {
    // 「table=!flyway_schema_history, !tmp_*」のようにカンマの後へ空白を入れた場合を想定する
    TableNamePatterns filter = TableNamePatterns.of(List.of("!flyway_schema_history", " !tmp_* "));
    assertTrue(filter.matches(TableKey.of("public", "employee")));
    assertFalse(filter.matches(TableKey.of("public", "tmp_work")));
    assertFalse(filter.matches(TableKey.of("public", "flyway_schema_history")));
  }

  @Test
  @DisplayName("matches: 完全一致パターンは同名のテーブルのみに一致する")
  void testMatchesExactPattern() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("employee"));
    assertTrue(filter.matches(TableKey.of("public", "employee")));
    assertFalse(filter.matches(TableKey.of("public", "employee_bk")));
  }

  @Test
  @DisplayName("matches: ワイルドカード（*）は任意の文字列に一致する")
  void testMatchesWildcardPattern() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("*_bk"));
    assertTrue(filter.matches(TableKey.of("public", "employee_bk")));
    assertTrue(filter.matches(TableKey.of("public", "_bk")));
    assertFalse(filter.matches(TableKey.of("public", "employee")));
  }

  @Test
  @DisplayName("matches: 除外パターン（!）に一致するテーブルは常に対象外")
  void testExcludePatternWins() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("!flyway_schema_history"));
    assertFalse(filter.matches(TableKey.of("public", "flyway_schema_history")));
    // 除外パターンのみの場合、それ以外のテーブルはすべて対象
    assertTrue(filter.matches(TableKey.of("public", "employee")));
  }

  @Test
  @DisplayName("matches: 除外パターンのワイルドカードも一時テーブルの除外に使える")
  void testExcludeWildcardPattern() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("!*_bk", "!*_20240101"));
    assertFalse(filter.matches(TableKey.of("public", "employee_bk")));
    assertFalse(filter.matches(TableKey.of("public", "employee_20240101")));
    assertTrue(filter.matches(TableKey.of("public", "employee")));
  }

  @Test
  @DisplayName("matches: 包含・除外の両方が指定された場合、除外が優先される")
  void testExcludeTakesPrecedenceOverInclude() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("employee*", "!employee_bk"));
    assertTrue(filter.matches(TableKey.of("public", "employee")));
    assertFalse(filter.matches(TableKey.of("public", "employee_bk")));
  }

  @Test
  @DisplayName("matches: スキーマ修飾パターンは指定したスキーマのテーブルにのみ一致する")
  void testSchemaQualifiedPattern() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("sample.employee"));
    assertTrue(filter.matches(TableKey.of("sample", "employee")));
    assertFalse(filter.matches(TableKey.of("other", "employee")));
  }

  @Test
  @DisplayName("matches: スキーマ修飾なしのパターンは全スキーマのテーブルに一致する（従来互換）")
  void testUnqualifiedPatternMatchesAllSchemas() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("employee"));
    assertTrue(filter.matches(TableKey.of("sample", "employee")));
    assertTrue(filter.matches(TableKey.of("other", "employee")));
  }

  @Test
  @DisplayName("matches: スキーマ修飾パターンとワイルドカードを組み合わせられる")
  void testSchemaQualifiedWildcardPattern() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("sample.*_bk"));
    assertTrue(filter.matches(TableKey.of("sample", "employee_bk")));
    assertFalse(filter.matches(TableKey.of("other", "employee_bk")));
    assertFalse(filter.matches(TableKey.of("sample", "employee")));
  }

  @Test
  @DisplayName("matches: 空白のみのパターンは無視される")
  void testBlankPatternsAreIgnored() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("  ", "employee"));
    assertTrue(filter.matches(TableKey.of("public", "employee")));
    assertFalse(filter.matches(TableKey.of("public", "other")));
  }

  @Test
  @DisplayName("of: テーブル名・スキーマ名の部分が空のパターンは、該当するものをすべて示して誤りとする")
  void testOfRejectsPatternsWithEmptyParts() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> TableNamePatterns.of(List.of("!", "sample.", ".employee", "employee")));

    // 正しいパターン（employee）は含めず、誤りのあるパターンだけを示す
    assertTrue(e.getMessage().startsWith("Invalid table pattern: !, sample., .employee ("));
  }

  @Test
  @DisplayName("of: 除外の!やスキーマ修飾の.の前後に空白があっても、名前が空でなければ受け入れる")
  void testOfAcceptsPatternsWithSpacesAroundSeparators() {
    TableNamePatterns filter = TableNamePatterns.of(List.of("! tmp_*", "sample . employee"));

    assertFalse(filter.matches(TableKey.of("sample", "tmp_work")));
    assertTrue(filter.matches(TableKey.of("sample", "employee")));
    assertFalse(filter.matches(TableKey.of("public", "employee")));
  }

  @Test
  @DisplayName("hasInclusion: 包含パターンが1件以上ある場合のみtrue（除外パターンのみの場合はfalse）")
  void testHasInclusion() {
    assertTrue(TableNamePatterns.of(List.of("orders", "!orders_bk")).hasInclusion());
    assertFalse(TableNamePatterns.of(List.of("!orders_bk")).hasInclusion());
    assertFalse(TableNamePatterns.of(List.of()).hasInclusion());
  }

  @Test
  @DisplayName("unmatchedInclusions: どのテーブルにも一致しない包含パターンを、指定された文字列のまま指定順に返す（除外パターンは対象外）")
  void testUnmatchedInclusions() {
    TableNamePatterns filter =
        TableNamePatterns.of(
            List.of("sales.order*", " sales.custmer ", "!sales.missing", "other.orders"));

    List<String> unmatched =
        filter.unmatchedInclusions(
            Tables.of(
                List.of(
                    new TableEntity("testdb", "sales", "", "orders", TableType.TABLE, ""),
                    new TableEntity("testdb", "sales", "", "customer", TableType.TABLE, ""))));

    assertEquals(List.of("sales.custmer", "other.orders"), unmatched);
  }
}
