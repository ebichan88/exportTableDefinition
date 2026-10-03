package com.export_table_definition.mcp.catalog;

import static com.export_table_definition.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link SchemaCatalog}のテスト */
class SchemaCatalogTest {

  @Nested
  @DisplayName("テーブル検索")
  class SearchTables {

    @Test
    @DisplayName("一致の強い順に並べ、同点はDB名・スキーマ名・テーブル名の順にする")
    void ordersByScoreThenKey() {
      final SchemaCatalog catalog =
          SchemaCatalog.of(
              List.of(
                  table("b_user_log").build(),
                  table("user").build(),
                  table("a_user_log").build(),
                  table("department").build()));

      final SearchResult result = catalog.searchTables(SearchQuery.of("user"), SearchScope.ALL, 10);

      assertEquals(3, result.total());
      assertEquals(List.of("user", "a_user_log", "b_user_log"), names(result));
    }

    @Test
    @DisplayName("件数の上限で切り捨てても、総数は切り捨てる前の数を返す")
    void limitsHitsButReportsTotal() {
      final SchemaCatalog catalog =
          SchemaCatalog.of(
              List.of(table("user_a").build(), table("user_b").build(), table("user_c").build()));

      final SearchResult result = catalog.searchTables(SearchQuery.of("user"), SearchScope.ALL, 2);

      assertEquals(3, result.total());
      assertEquals(List.of("user_a", "user_b"), names(result));
    }

    @Test
    @DisplayName("DB名・スキーマ名で絞り込む（大文字小文字を区別しない）")
    void filtersByScope() {
      final SchemaCatalog catalog =
          SchemaCatalog.of(
              List.of(
                  table("db1", "sales", "user").build(),
                  table("db1", "hr", "user").build(),
                  table("db2", "sales", "user").build()));

      final SearchResult result =
          catalog.searchTables(SearchQuery.of("user"), new SearchScope("DB1", "SALES"), 10);

      assertEquals(List.of(new TableKey("db1", "sales", "user")), keys(result));
    }
  }

  @Nested
  @DisplayName("名前の解決")
  class Lookup {

    private final SchemaCatalog catalog =
        SchemaCatalog.of(
            List.of(
                table("db1", "sales", "orders").build(),
                table("db1", "hr", "employee").build(),
                table("db1", "archive", "employee").build(),
                table("db1", "hr", "employee_profile").build()));

    @Test
    @DisplayName("テーブル名を大文字小文字を区別せず完全一致で解決する")
    void findsByNameIgnoringCase() {
      final TableLookup lookup = catalog.lookup(TableReference.of(null, null, "ORDERS"));

      assertEquals(
          new TableKey("db1", "sales", "orders"),
          assertInstanceOf(TableLookup.Found.class, lookup).table().key());
    }

    @Test
    @DisplayName("同名のテーブルが複数のスキーマにある場合は、候補をスキーマ名の順に返す")
    void reportsAmbiguousNames() {
      final TableLookup lookup = catalog.lookup(TableReference.of(null, null, "employee"));

      assertEquals(
          List.of("archive.employee", "hr.employee"),
          assertInstanceOf(TableLookup.Ambiguous.class, lookup).candidates().stream()
              .map(table -> table.key().qualifiedName())
              .toList());
    }

    @Test
    @DisplayName("スキーマ名を引数で指定するか、スキーマ名.テーブル名の形で指定すると1つに定まる")
    void resolvesBySchema() {
      final TableKey expected = new TableKey("db1", "hr", "employee");

      assertEquals(
          expected,
          assertInstanceOf(
                  TableLookup.Found.class,
                  catalog.lookup(TableReference.of(null, "hr", "employee")))
              .table()
              .key());
      assertEquals(
          expected,
          assertInstanceOf(
                  TableLookup.Found.class,
                  catalog.lookup(TableReference.of(null, null, "hr.employee")))
              .table()
              .key());
    }

    @Test
    @DisplayName("見つからない場合は、スキーマの指定を外して名前の似たテーブルを候補に返す")
    void suggestsSimilarNames() {
      final TableLookup lookup = catalog.lookup(TableReference.of(null, "sales", "employe"));

      assertEquals(
          List.of("archive.employee", "hr.employee", "hr.employee_profile"),
          assertInstanceOf(TableLookup.NotFound.class, lookup).suggestions().stream()
              .map(table -> table.key().qualifiedName())
              .toList());
    }

    @Test
    @DisplayName("似た名前も無い場合は、候補を空で返す")
    void returnsNoSuggestions() {
      final TableLookup lookup = catalog.lookup(TableReference.of(null, null, "invoice"));

      assertTrue(assertInstanceOf(TableLookup.NotFound.class, lookup).suggestions().isEmpty());
    }
  }

  @Nested
  @DisplayName("関連のたどり")
  class RelatedTablesTest {

    /**
     * department ← employee → parking_spot、employee → employee（自己参照）、audit_log ⇢
     * employee（論理）、assignment → employee・project
     */
    private final SchemaCatalog catalog =
        SchemaCatalog.of(
            List.of(
                table("department").build(),
                table("parking_spot").build(),
                table("employee")
                    .foreignKey("department_id", "department", "department_id")
                    .foreignKey("manager_id", "employee", "employee_id")
                    .foreignKey("parking_spot_id", "parking_spot", "parking_spot_id")
                    .build(),
                table("audit_log").logicalRelation("record_id", "employee", "employee_id").build(),
                table("project").build(),
                table("assignment")
                    .foreignKey("employee_id", "employee", "employee_id")
                    .foreignKey("project_id", "project", "project_id")
                    .build()));

    @Test
    @DisplayName("参照先へ向かう関連・参照元から来る関連の順に返し、自己参照は1回だけ返す")
    void returnsBothDirections() {
      final RelatedTables related = related("employee", 1, Direction.BOTH);

      assertEquals(
          List.of(
              "employee.department_id->department FOREIGN_KEY 1",
              "employee.manager_id->employee FOREIGN_KEY 1",
              "employee.parking_spot_id->parking_spot FOREIGN_KEY 1",
              "audit_log.record_id->employee LOGICAL_RELATION 1",
              "assignment.employee_id->employee FOREIGN_KEY 1"),
          describe(related));
      assertEquals(
          List.of("employee", "department", "parking_spot", "audit_log", "assignment"),
          related.tables().stream().map(table -> table.key().name()).toList());
    }

    @Test
    @DisplayName("向きを指定すると、その向きの関連だけをたどる")
    void followsDirection() {
      assertEquals(
          List.of(
              "employee.department_id->department FOREIGN_KEY 1",
              "employee.manager_id->employee FOREIGN_KEY 1",
              "employee.parking_spot_id->parking_spot FOREIGN_KEY 1"),
          describe(related("employee", 1, Direction.OUTGOING)));
      assertEquals(
          List.of(
              "employee.manager_id->employee FOREIGN_KEY 1",
              "audit_log.record_id->employee LOGICAL_RELATION 1",
              "assignment.employee_id->employee FOREIGN_KEY 1"),
          describe(related("employee", 1, Direction.INCOMING)));
    }

    @Test
    @DisplayName("段数を増やすと、たどった先のテーブルの関連も段数付きで返す")
    void followsDepth() {
      final List<String> relations = describe(related("department", 2, Direction.BOTH));

      assertEquals("employee.department_id->department FOREIGN_KEY 1", relations.get(0));
      assertTrue(relations.contains("assignment.employee_id->employee FOREIGN_KEY 2"));
      assertTrue(relations.contains("audit_log.record_id->employee LOGICAL_RELATION 2"));
      assertTrue(
          relations.stream().noneMatch(relation -> relation.startsWith("assignment.project_id")),
          "3段目（assignment → project）はたどらない");
    }

    @Test
    @DisplayName("参照先がスナップショットに無いテーブルは、関連は返すがその先はたどらない")
    void reportsMissingTables() {
      final SchemaCatalog withMissing =
          SchemaCatalog.of(
              List.of(
                  table("orders").foreignKey("customer_id", "customer", "customer_id").build()));
      final TableEntry orders = withMissing.tables().get(0);

      final RelatedTables related = withMissing.relatedTables(orders, 3, Direction.BOTH);

      assertEquals(List.of("orders.customer_id->customer FOREIGN_KEY 1"), describe(related));
      assertEquals(List.of(new TableKey("testdb", "sample", "customer")), related.missingTables());
      assertEquals(List.of(orders), related.tables());
    }

    @Test
    @DisplayName("参照先のスキーマが未設定の関連は、参照元と同じスキーマとみなす")
    void defaultsReferenceSchemaToOwnSchema() {
      final SchemaCatalog sameSchema =
          SchemaCatalog.of(
              List.of(
                  table("customer").build(),
                  table("orders")
                      .foreignKey(
                          new RelationEntry(
                              "fk",
                              List.of("customer_id"),
                              null,
                              "customer",
                              List.of("customer_id"),
                              null))
                      .build()));

      final RelatedTables related =
          sameSchema.relatedTables(sameSchema.tables().get(0), 1, Direction.INCOMING);

      assertEquals(List.of("orders.customer_id->customer FOREIGN_KEY 1"), describe(related));
    }

    private RelatedTables related(String name, int depth, Direction direction) {
      final TableEntry start =
          ((TableLookup.Found) catalog.lookup(TableReference.of(null, null, name))).table();
      return catalog.relatedTables(start, depth, direction);
    }
  }

  private static List<String> names(SearchResult result) {
    return result.hits().stream().map(hit -> hit.table().key().name()).toList();
  }

  private static List<TableKey> keys(SearchResult result) {
    return result.hits().stream().map(hit -> hit.table().key()).toList();
  }

  /** {@code 参照元.カラム->参照先 種別 段数}の形の一覧 */
  private static List<String> describe(RelatedTables related) {
    return related.relations().stream()
        .map(
            found ->
                found.relation().from().name()
                    + "."
                    + String.join(",", found.relation().fromColumns())
                    + "->"
                    + found.relation().to().name()
                    + " "
                    + found.relation().kind()
                    + " "
                    + found.depth())
        .toList();
  }
}
