package com.export_table_definition.domain.model.value;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.type.TableType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** TableTargetScope の絞り込み判定（matches）・isFilteredに関するテスト */
class TableTargetScopeTest {

  private static TableEntity table() {
    return new TableEntity("dbName", "test_schema", "テストテーブル", "testTable", TableType.TABLE, "");
  }

  @Nested
  class testMatches {

    static Stream<Arguments> testMatchesProvider() {
      return Stream.of(
          arguments("実施する　（テーブル定義出力対象のスキーマ／TBLリストが存在しない）", Arrays.asList(), Arrays.asList(), true),
          arguments(
              "実施する　（テーブル定義出力対象のTBLリストが存在 かつ 対象のTBLが存在する）",
              Arrays.asList(),
              Arrays.asList("testTable"),
              true),
          arguments(
              "実施しない（テーブル定義出力対象のTBLリストが存在 かつ 対象のTBLが存在しない）",
              Arrays.asList(),
              Arrays.asList("mismatchTable"),
              false),
          arguments(
              "実施する　（テーブル定義出力対象のスキーマリストが存在 かつ 対象のスキーマが存在する）",
              Arrays.asList("test_schema"),
              Arrays.asList(),
              true),
          arguments(
              "実施しない（テーブル定義出力対象のスキーマリストが存在 かつ 対象のスキーマが存在しない）",
              Arrays.asList("mismatch_schema"),
              Arrays.asList(),
              false),
          arguments(
              "実施する　（テーブル定義出力対象のスキーマ／TBLリストが存在 かつ 対象のスキーマ／TBLが存在する）",
              Arrays.asList("test_schema"),
              Arrays.asList("testTable"),
              true),
          arguments(
              "実施しない（テーブル定義出力対象のスキーマ／TBLリストが存在 かつ 対象のTBLが存在しない）",
              Arrays.asList("test_schema"),
              Arrays.asList("mismatchTable"),
              false),
          arguments(
              "実施しない（テーブル定義出力対象のスキーマ／TBLリストが存在 かつ 対象のスキーマが存在しない）",
              Arrays.asList("mismatch_schema"),
              Arrays.asList("testTable"),
              false),
          arguments(
              "実施しない（テーブル定義出力対象のスキーマ／TBLリストが存在 かつ 対象のスキーマ／TBLが存在しない）",
              Arrays.asList("mismatch_schema"),
              Arrays.asList("mismatchTable"),
              false),
          arguments(
              "実施する　（TBLリストにワイルドカードパターンが存在 かつ 対象のTBLがパターンに一致する）",
              Arrays.asList(),
              Arrays.asList("test*"),
              true),
          arguments(
              "実施しない（TBLリストにワイルドカードパターンが存在 かつ 対象のTBLがパターンに一致しない）",
              Arrays.asList(),
              Arrays.asList("other*"),
              false),
          arguments(
              "実施しない（TBLリストに除外パターンが存在 かつ 対象のTBLが除外パターンに一致する）",
              Arrays.asList(),
              Arrays.asList("!testTable"),
              false),
          arguments(
              "実施する　（TBLリストに除外パターンのみ存在 かつ 対象のTBLが除外パターンに一致しない）",
              Arrays.asList(),
              Arrays.asList("!mismatchTable"),
              true),
          arguments(
              "実施しない（TBLリストに包含・除外の両パターンが存在 かつ 除外パターンが優先される）",
              Arrays.asList(),
              Arrays.asList("test*", "!testTable"),
              false),
          arguments(
              "実施する　（TBLリストにスキーマ修飾パターンが存在 かつ 対象のスキーマ・TBLが一致する）",
              Arrays.asList(),
              Arrays.asList("test_schema.testTable"),
              true),
          arguments(
              "実施しない（TBLリストにスキーマ修飾パターンが存在 かつ 対象のスキーマが一致しない）",
              Arrays.asList(),
              Arrays.asList("other_schema.testTable"),
              false));
    }

    @DisplayName("【正常系】テーブルが出力対象の範囲に含まれるか判定する")
    @ParameterizedTest
    @MethodSource("testMatchesProvider")
    void success1(
        String definition,
        List<String> targetSchemaList,
        List<String> targetTableList,
        boolean expected) {
      TableTargetScope scope = TableTargetScope.of(targetSchemaList, targetTableList);
      assertEquals(expected, scope.matches(table()));
    }
  }

  @Nested
  class testIsFiltered {

    @Test
    @DisplayName("isFiltered: スキーマ・テーブルのいずれも指定されていない場合はfalse")
    void testIsFilteredFalseWhenBothEmpty() {
      assertFalse(TableTargetScope.of(List.of(), List.of()).isFiltered());
      assertFalse(TableTargetScope.of(null, null).isFiltered());
    }

    @Test
    @DisplayName("isFiltered: スキーマのみ指定されている場合はtrue")
    void testIsFilteredTrueWhenOnlySchemaSpecified() {
      assertTrue(TableTargetScope.of(List.of("test_schema"), List.of()).isFiltered());
    }

    @Test
    @DisplayName("isFiltered: テーブルのみ指定されている場合はtrue")
    void testIsFilteredTrueWhenOnlyTableSpecified() {
      assertTrue(TableTargetScope.of(List.of(), List.of("testTable")).isFiltered());
    }

    @Test
    @DisplayName("isFiltered: 空白のみのスキーマ名は指定されていないものとみなす")
    void testIsFilteredFalseWhenSchemaIsBlank() {
      assertFalse(TableTargetScope.of(List.of(" "), List.of()).isFiltered());
    }
  }

  @Nested
  class testSchemaNames {

    @Test
    @DisplayName("schemaNames: 前後の空白を除去し、空要素を除いたスキーマ名を返す")
    void testSchemaNamesAreStripped() {
      // 「schema=public, test_schema」のようにカンマの後へ空白を入れた場合を想定する
      TableTargetScope scope =
          TableTargetScope.of(Arrays.asList("public", " test_schema", ""), null);

      assertEquals(List.of("public", "test_schema"), scope.schemaNames());
      assertTrue(scope.matches(table()));
    }

    @Test
    @DisplayName("schemaNames: 未指定の場合は空リストを返す")
    void testSchemaNamesEmptyWhenNotSpecified() {
      assertEquals(List.of(), TableTargetScope.of(null, List.of("testTable")).schemaNames());
    }
  }
}
