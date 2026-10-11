package com.dbxray.mcp.catalog;

import static com.dbxray.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link SchemaCatalog}のテスト（複数の窓口にまたがる、スキーマごとの数・テーブルのまとまり） */
class SchemaCatalogTest {

  @Nested
  @DisplayName("一覧")
  class Listing {

    private final SchemaCatalog catalog =
        SchemaCatalog.of(
            List.of(
                new DatabaseEntry("db1", "PostgreSQL", 16), new DatabaseEntry("db2", "Oracle", 23)),
            List.of(
                table("db1", "sales", "orders").build(),
                table("db1", "sales", "order_summary").type("view").build(),
                table("db1", "hr", "employee").build(),
                table("db2", "hr", "employee").build()),
            List.of(),
            List.of(),
            List.of());

    @Test
    @DisplayName("スキーマごとにテーブル・ビューの数を、DB名・スキーマ名の順に返す")
    void summarizesSchemas() {
      assertEquals(
          List.of(
              new SchemaSummary("db1", "PostgreSQL", 16, "hr", 1, 0, 0, 0, 0, 0, 0, 0),
              new SchemaSummary("db1", "PostgreSQL", 16, "sales", 1, 1, 0, 0, 0, 0, 0, 0),
              new SchemaSummary("db2", "Oracle", 23, "hr", 1, 0, 0, 0, 0, 0, 0, 0)),
          catalog.schemas());
    }

    @Test
    @DisplayName("テーブルをDB名・スキーマ名・テーブル名の順に並べ、スコープ・区分で絞り込む")
    void listsTables() {
      assertEquals(
          List.of(
              "db1:hr.employee", "db1:sales.order_summary", "db1:sales.orders", "db2:hr.employee"),
          catalog.tables().list(TableFilter.ALL, TableOrder.NAME).stream()
              .map(t -> describe(t.key()))
              .toList());
      assertEquals(
          List.of("db1:sales.orders"),
          catalog
              .tables()
              .list(
                  TableFilter.of(new SearchScope(null, "SALES")).withType(TableType.TABLE),
                  TableOrder.NAME)
              .stream()
              .map(t -> describe(t.key()))
              .toList());
    }

    private static String describe(ObjectKey key) {
      return key.database() + ":" + key.qualifiedName();
    }
  }

  @Nested
  @DisplayName("関連のまとまり")
  class TableClustersTest {

    /**
     * employee（department → へ参照し、orders・payroll・attendance・leave_requestから参照される共通のマスタ）、受注の系統
     * （orders → customer、order_item → orders・product、shipment → orders）、給与の系統（payroll_item →
     * payroll）、関連の無いmemo
     */
    private final List<TableEntry> tables =
        List.of(
            table("department").build(),
            table("employee").foreignKey("department_id", "department", "department_id").build(),
            table("customer").build(),
            table("product").build(),
            table("orders")
                .foreignKey("employee_id", "employee", "employee_id")
                .foreignKey("customer_id", "customer", "customer_id")
                .build(),
            table("order_item")
                .foreignKey("order_id", "orders", "order_id")
                .foreignKey("product_id", "product", "product_id")
                .build(),
            table("shipment").logicalRelation("order_id", "orders", "order_id").build(),
            table("payroll").foreignKey("employee_id", "employee", "employee_id").build(),
            table("payroll_item").foreignKey("payroll_id", "payroll", "payroll_id").build(),
            table("attendance").foreignKey("employee_id", "employee", "employee_id").build(),
            table("leave_request").foreignKey("employee_id", "employee", "employee_id").build(),
            table("memo").build());

    private final SchemaCatalog catalog = TestCatalogs.of(tables);

    @Test
    @DisplayName("上限以内なら、関連でつながるテーブルを1つのまとまりとし、関連の無いテーブルは数だけを返す")
    void keepsConnectedTablesWithinLimit() {
      final TableClusters result = catalog.tableClusters(SearchScope.ALL, 30, false);

      assertEquals(1, result.clusters().size());
      assertEquals(11, result.clusters().get(0).tables().size());
      assertEquals("employee", result.clusters().get(0).representative().key().name());
      assertTrue(result.hubs().isEmpty());
      assertEquals(1, result.unrelatedTables());
    }

    @Test
    @DisplayName("上限を超えるまとまりは、被参照の最も多いテーブルをハブとして除いて分け、ハブだけと関連を持つテーブルはハブごとにまとめる")
    void splitsByRemovingHubs() {
      final TableClusters result = catalog.tableClusters(SearchScope.ALL, 5, false);

      assertEquals(List.of("employee"), names(result.hubs()));
      assertEquals(
          List.of(
              List.of("orders", "customer", "product", "order_item", "shipment"),
              List.of("department", "attendance", "leave_request"),
              List.of("payroll", "payroll_item")),
          result.clusters().stream().map(cluster -> names(cluster.tables())).toList());
      assertTrue(
          result.clusters().stream()
              .allMatch(cluster -> names(cluster.hubs()).equals(List.of("employee"))));
      assertEquals(1, result.unrelatedTables());
    }

    @Test
    @DisplayName("被参照の多いテーブルが無い（一続きの関連だけの）まとまりは、上限を超えても分けない")
    void keepsChainWithoutHub() {
      final SchemaCatalog chain =
          TestCatalogs.of(
              List.of(
                  table("t0").build(),
                  table("t1").foreignKey("t0_id", "t0", "id").build(),
                  table("t2").foreignKey("t1_id", "t1", "id").build(),
                  table("t3").foreignKey("t2_id", "t2", "id").build(),
                  table("t4").foreignKey("t3_id", "t3", "id").build()));

      final TableClusters result = chain.tableClusters(SearchScope.ALL, 2, false);

      assertEquals(1, result.clusters().size());
      assertEquals(5, result.clusters().get(0).tables().size());
      assertTrue(result.hubs().isEmpty());
    }

    @Test
    @DisplayName("範囲外のテーブル（別スキーマ・観点に所属するテーブル）との関連は無いものとみなす")
    void ignoresRelationsOutsideScope() {
      final SchemaCatalog scoped =
          TestCatalogs.of(
                  List.of(
                      table("department").build(),
                      table("employee")
                          .foreignKey("department_id", "department", "department_id")
                          .build(),
                      table("attendance")
                          .foreignKey("employee_id", "employee", "employee_id")
                          .build(),
                      table("testdb", "other", "outside").build()))
              .withViewpoints(
                  List.of(
                      new ViewpointEntry(
                          "testdb",
                          "org",
                          "組織",
                          "",
                          List.of(new ObjectKey("testdb", "sample", "department")))));

      final TableClusters all = scoped.tableClusters(new SearchScope("", "sample"), 30, false);
      assertEquals(
          List.of(List.of("department", "employee", "attendance")),
          all.clusters().stream().map(cluster -> names(cluster.tables())).toList());
      assertEquals(0, all.unrelatedTables());

      final TableClusters unassigned =
          scoped.tableClusters(new SearchScope("", "sample"), 30, true);
      assertEquals(
          List.of(List.of("employee", "attendance")),
          unassigned.clusters().stream().map(cluster -> names(cluster.tables())).toList());
    }

    private static List<String> names(List<TableEntry> tables) {
      return tables.stream().map(table -> table.key().name()).toList();
    }
  }
}
