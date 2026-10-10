package com.dbxray.mcp.catalog;

import static com.dbxray.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link SearchQuery}のテスト */
class SearchQueryTest {

  @Test
  @DisplayName("大文字小文字・全角半角の違いを無視して一致する")
  void matchesIgnoringCaseAndWidth() {
    final TableEntry table = table("order_detail").logicalName("ｼﾞｭﾁｭｳ明細").build();

    assertTrue(SearchQuery.of("ORDER").match(table).isPresent());
    assertTrue(SearchQuery.of("ｏｒｄｅｒ").match(table).isPresent());
    assertTrue(SearchQuery.of("ジュチュウ").match(table).isPresent());
  }

  @Test
  @DisplayName("空白区切りの語は、すべての語がいずれかの項目に含まれる場合だけ一致する（全角空白も区切りとみなす）")
  void requiresAllTerms() {
    final TableEntry table =
        table("order_detail").logicalName("受注明細").column("product_id", "商品ID", null).build();

    assertTrue(SearchQuery.of("受注 商品").match(table).isPresent());
    assertTrue(SearchQuery.of("受注　商品").match(table).isPresent());
    assertTrue(SearchQuery.of("受注 顧客").match(table).isEmpty());
  }

  @Test
  @DisplayName("一致した項目を、カラムはカラム名付きで重複なく返す")
  void reportsMatchedFields() {
    final TableEntry table =
        table("employee")
            .logicalName("従業員")
            .description("従業員のマスタ")
            .remarks("従業員の退職後も残す")
            .column("employee_id", "従業員ID", null)
            .column("note", null, "従業員についての補足")
            .build();

    final TableHit hit = SearchQuery.of("従業員").match(table).orElseThrow();

    assertEquals(
        List.of("logicalName", "description", "remarks", "column:employee_id", "column:note"),
        hit.matchedIn());
  }

  @Test
  @DisplayName("テーブル名の完全一致・前方一致・部分一致、論理名、カラム、説明の順に強く一致する")
  void scoresByField() {
    final int exact = score("user", table("user").build());
    final int prefix = score("user", table("user_role").build());
    final int partial = score("user", table("app_user").build());
    final int logicalName = score("user", table("account").logicalName("user account").build());
    final int column = score("user", table("orders").column("user").build());
    final int description = score("user", table("orders").description("user's orders").build());

    assertTrue(exact > prefix, "完全一致 > 前方一致");
    assertTrue(prefix > partial, "前方一致 > 部分一致");
    assertTrue(partial > logicalName, "テーブル名 > 論理名");
    assertTrue(logicalName > column, "論理名 > カラム");
    assertTrue(column > description, "カラム > 説明");
  }

  @Test
  @DisplayName("複数語の点数は、語ごとに最も強く一致した項目の点数の合計になる")
  void sumsScoresOfTerms() {
    final TableEntry table = table("user").logicalName("利用者").build();

    assertEquals(score("user", table) + score("利用者", table), score("user 利用者", table));
  }

  @Test
  @DisplayName("空白だけの検索語は受け付けない")
  void rejectsBlankQuery() {
    assertThrows(IllegalArgumentException.class, () -> SearchQuery.of(" 　"));
  }

  private static int score(String query, TableEntry table) {
    return SearchQuery.of(query).match(table).orElseThrow().score();
  }
}
