package com.dbxray.mcp.catalog;

import static com.dbxray.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link ViewpointCatalog}のテスト（観点によるテーブルの絞り込みを含む） */
class ViewpointCatalogTest {

  @Nested
  @DisplayName("観点")
  class ViewpointsTest {

    private final ViewpointEntry order =
        new ViewpointEntry(
            "testdb",
            "order",
            "受注管理",
            "受注から出荷までを扱う",
            List.of(new ObjectKey("testdb", "sales", "orders")));
    private final ViewpointEntry inventory =
        new ViewpointEntry(
            "testdb", "inventory", "在庫管理", "", List.of(new ObjectKey("testdb", "sales", "stock")));
    private final ViewpointEntry otherDbOrder =
        new ViewpointEntry("otherdb", "order", "受注", "", List.of());

    private final SchemaCatalog catalog =
        TestCatalogs.of(
                List.of(
                    table("testdb", "sales", "orders").build(),
                    table("testdb", "sales", "stock").build(),
                    table("testdb", "sales", "store").build(),
                    table("testdb", "hr", "employee").build()))
            .withViewpoints(List.of(order, inventory, otherDbOrder));

    @Test
    @DisplayName("宣言順に返し、DBで絞り込める（スキーマは無視する）")
    void listsInDeclarationOrderAndFiltersByDatabase() {
      assertEquals(
          List.of("testdb:order", "testdb:inventory", "otherdb:order"),
          catalog.viewpoints().list(SearchScope.ALL).stream()
              .map(viewpoint -> viewpoint.database() + ":" + viewpoint.id())
              .toList());
      assertEquals(
          List.of("order"),
          catalog.viewpoints().list(new SearchScope("otherdb", "")).stream()
              .map(ViewpointEntry::id)
              .toList());
    }

    @Test
    @DisplayName("識別子を大文字小文字を区別せず解決し、DBで絞り込める")
    void findsViewpointIgnoringCase() {
      assertEquals(order, catalog.viewpoints().find(SearchScope.ALL, "ORDER").orElseThrow());
      assertEquals(
          otherDbOrder,
          catalog.viewpoints().find(new SearchScope("otherdb", ""), "order").orElseThrow());
      assertTrue(catalog.viewpoints().find(new SearchScope("nodb", ""), "order").isEmpty());
      assertTrue(catalog.viewpoints().find(SearchScope.ALL, "unknown").isEmpty());
    }

    @Test
    @DisplayName("listTablesは、観点を指定すると所属テーブルだけに絞り込む")
    void listTablesFiltersByViewpoint() {
      assertEquals(
          List.of("orders"),
          catalog.tables().list(TableFilter.ALL.withViewpoint(order), TableOrder.NAME).stream()
              .map(table -> table.key().name())
              .toList());
    }

    @Test
    @DisplayName("viewpointsOfは、テーブルが所属する観点を宣言順に返す。所属しない場合や、別DBの同名テーブルだけを含む観点は含めない")
    void findsViewpointsOfTable() {
      final ViewpointEntry audit =
          new ViewpointEntry(
              "testdb",
              "audit",
              "監査",
              "",
              List.of(
                  new ObjectKey("testdb", "sales", "orders"),
                  new ObjectKey("testdb", "sales", "stock")));
      final ViewpointEntry otherDbOrders =
          new ViewpointEntry(
              "otherdb", "legacy", "旧受注", "", List.of(new ObjectKey("otherdb", "sales", "orders")));
      final SchemaCatalog multiple =
          catalog.withViewpoints(List.of(audit, order, inventory, otherDbOrders));

      assertEquals(
          List.of(audit, order),
          multiple
              .viewpoints()
              .containing(
                  Lookups.found(
                      multiple.tables().lookup(ObjectReference.of("testdb", "sales", "orders")))));
      assertEquals(
          List.of(audit, inventory),
          multiple
              .viewpoints()
              .containing(
                  Lookups.found(
                      multiple.tables().lookup(ObjectReference.of("testdb", "sales", "stock")))));
      assertEquals(
          List.of(),
          multiple
              .viewpoints()
              .containing(
                  Lookups.found(
                      multiple.tables().lookup(ObjectReference.of("testdb", "sales", "store")))));
    }

    @Test
    @DisplayName("searchTablesは、観点による絞り込みをtotal・limitへ正しく反映する（絞り込みは件数算出より前に行う）")
    void searchTablesFiltersByViewpointBeforeCountingTotal() {
      final SearchResult withoutViewpoint =
          catalog.tables().search(SearchQuery.of("st"), TableFilter.ALL, 10);
      assertEquals(2, withoutViewpoint.total());

      final SearchResult withViewpoint =
          catalog
              .tables()
              .search(SearchQuery.of("st"), TableFilter.ALL.withViewpoint(inventory), 10);
      assertEquals(1, withViewpoint.total());
      assertEquals(List.of("stock"), names(withViewpoint));
    }
  }

  private static List<String> names(SearchResult result) {
    return result.hits().stream().map(hit -> hit.table().key().name()).toList();
  }
}
