package com.export_table_definition.mcp.catalog;

import static com.export_table_definition.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

      assertEquals(List.of(new ObjectKey("db1", "sales", "user")), keys(result));
    }
  }

  @Nested
  @DisplayName("一覧")
  class Listing {

    private final SchemaCatalog catalog =
        SchemaCatalog.of(
            List.of(new DatabaseEntry("db1", "PostgreSQL"), new DatabaseEntry("db2", "Oracle")),
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
              new SchemaSummary("db1", "PostgreSQL", "hr", 1, 0, 0, 0, 0, 0),
              new SchemaSummary("db1", "PostgreSQL", "sales", 1, 1, 0, 0, 0, 0),
              new SchemaSummary("db2", "Oracle", "hr", 1, 0, 0, 0, 0, 0)),
          catalog.schemas());
    }

    @Test
    @DisplayName("テーブルをDB名・スキーマ名・テーブル名の順に並べ、スコープ・区分で絞り込む")
    void listsTables() {
      assertEquals(
          List.of(
              "db1:hr.employee", "db1:sales.order_summary", "db1:sales.orders", "db2:hr.employee"),
          catalog.listTables(SearchScope.ALL, "").stream().map(t -> describe(t.key())).toList());
      assertEquals(
          List.of("db1:sales.orders"),
          catalog.listTables(new SearchScope(null, "SALES"), "table").stream()
              .map(t -> describe(t.key()))
              .toList());
    }

    private static String describe(ObjectKey key) {
      return key.database() + ":" + key.qualifiedName();
    }
  }

  @Nested
  @DisplayName("カラムの逆引き")
  class FindColumns {

    private final SchemaCatalog catalog =
        SchemaCatalog.of(
            List.of(
                table("department").column("department_id", "部署ID", null).build(),
                table("employee")
                    .column("employee_id", "従業員ID", null)
                    .column("department_id", "所属部署ID", null)
                    .foreignKey("department_id", "department", "department_id")
                    .build(),
                table("audit_log")
                    .column("employee_no", "従業員ID", null)
                    .logicalRelation("employee_no", "employee", "employee_id")
                    .build(),
                table("project").column("leader_employee_id").column("employee").build()));

    @Test
    @DisplayName("完全一致では、物理名・論理名のどちらかが一致するカラムを返す（大文字小文字を区別しない）")
    void findsExactMatches() {
      assertEquals(
          List.of("audit_log.employee_no", "employee.employee_id"),
          describe(catalog.findColumns(ColumnQuery.of("従業員ID", MatchMode.EXACT), SearchScope.ALL)));
      assertEquals(
          List.of("department.department_id", "employee.department_id"),
          describe(
              catalog.findColumns(
                  ColumnQuery.of("DEPARTMENT_ID", MatchMode.EXACT), SearchScope.ALL)));
    }

    @Test
    @DisplayName("部分一致では、完全一致・前方一致・部分一致の順に返す")
    void findsPartialMatches() {
      assertEquals(
          List.of(
              "project.employee",
              "audit_log.employee_no",
              "employee.employee_id",
              "project.leader_employee_id"),
          describe(
              catalog.findColumns(ColumnQuery.of("employee", MatchMode.PARTIAL), SearchScope.ALL)));
    }

    @Test
    @DisplayName("カラムが外部キー・論理リレーションで参照している先を返す")
    void returnsReferences() {
      final List<ColumnHit> hits =
          catalog.findColumns(ColumnQuery.of("department_id", MatchMode.EXACT), SearchScope.ALL);

      assertTrue(hits.get(0).references().isEmpty());
      assertEquals(
          List.of(
              new ColumnHit.ColumnReference(
                  new ObjectKey("testdb", "sample", "department"),
                  "department_id",
                  RelationKind.FOREIGN_KEY)),
          hits.get(1).references());
      assertEquals(
          RelationKind.LOGICAL_RELATION,
          catalog
              .findColumns(ColumnQuery.of("employee_no", MatchMode.EXACT), SearchScope.ALL)
              .get(0)
              .references()
              .get(0)
              .kind());
    }

    private static List<String> describe(List<ColumnHit> hits) {
      return hits.stream()
          .map(hit -> hit.table().key().name() + "." + hit.column().name())
          .toList();
    }
  }

  @Nested
  @DisplayName("関数・シーケンス・ユーザー定義型・トリガー")
  class OtherObjects {

    private final SchemaCatalog catalog =
        SchemaCatalog.of(
            List.of(new DatabaseEntry("db1", "PostgreSQL")),
            List.of(
                table("db1", "sales", "orders")
                    .trigger("trg_orders_audit", "sales.audit")
                    .trigger("trg_orders_touch", "sales.touch")
                    .build(),
                table("db1", "hr", "employee").trigger("trg_employee_audit", "sales.audit").build(),
                table("db1", "hr", "department").type("view").build()),
            List.of(
                function("db1", "sales", "calc_tax", "p_amount numeric"),
                function("db1", "sales", "audit", ""),
                function("db1", "sales", "calc_tax", "p_amount numeric, p_rate numeric"),
                function("db1", "hr", "calc_tax", "")),
            List.of(
                new SequenceEntry(
                    new ObjectKey("db1", "sales", "orders_id_seq"), "orders.id", "{}"),
                new SequenceEntry(new ObjectKey("db1", "sales", "invoice_no_seq"), null, "{}")),
            List.of(
                new TypeEntry(new ObjectKey("db1", "sales", "order_status"), "ENUM", "{}"),
                new TypeEntry(new ObjectKey("db1", "sales", "address"), "COMPOSITE", "{}")));

    @Test
    @DisplayName("スキーマごとの数に、関数（オーバーロードはそれぞれ）・シーケンス・型も数える")
    void summarizesAllKinds() {
      assertEquals(
          List.of(
              new SchemaSummary("db1", "PostgreSQL", "hr", 1, 1, 0, 1, 0, 0),
              new SchemaSummary("db1", "PostgreSQL", "sales", 1, 0, 0, 3, 2, 2)),
          catalog.schemas());
    }

    @Test
    @DisplayName("関数は名前の順に、オーバーロードは並び順のまま一覧にし、名前の一部で絞り込める")
    void listsFunctions() {
      assertEquals(
          List.of(
              "hr.calc_tax()",
              "sales.calc_tax(p_amount numeric)",
              "sales.calc_tax(p_amount numeric, p_rate numeric)"),
          catalog.listFunctions(SearchScope.ALL, NameFilter.of("CALC")).stream()
              .map(f -> f.key().qualifiedName() + "(" + f.arguments() + ")")
              .toList());
    }

    @Test
    @DisplayName("関数のオーバーロードは、名前の解決では1つとみなす")
    void resolvesOverloadsAsOne() {
      final FunctionOverloads calcTax =
          Lookups.found(catalog.lookupFunction(ObjectReference.of(null, null, "sales.calc_tax")));

      assertEquals(2, calcTax.overloads().size());
      assertEquals(
          List.of("hr.calc_tax", "sales.calc_tax"),
          Lookups.candidates(catalog.lookupFunction(ObjectReference.of(null, null, "calc_tax")))
              .stream()
              .map(f -> f.key().qualifiedName())
              .toList());
    }

    @Test
    @DisplayName("関数・シーケンス・型が見つからない場合は、名前の一部に指定を含むものを候補にする")
    void suggestsByPartialName() {
      assertEquals(
          List.of("sales.orders_id_seq"),
          Lookups.suggestions(catalog.lookupSequence(ObjectReference.of(null, null, "orders")))
              .stream()
              .map(sequence -> sequence.key().qualifiedName())
              .toList());
      assertTrue(
          Lookups.suggestions(catalog.lookupType(ObjectReference.of(null, null, "invoice")))
              .isEmpty());
    }

    @Test
    @DisplayName("シーケンス・型を名前の順に一覧にし、型は種別で絞り込める")
    void listsSequencesAndTypes() {
      assertEquals(
          List.of("invoice_no_seq", "orders_id_seq"),
          catalog.listSequences(SearchScope.ALL, NameFilter.ALL).stream()
              .map(sequence -> sequence.key().name())
              .toList());
      assertEquals(
          List.of("order_status"),
          catalog.listTypes(SearchScope.ALL, NameFilter.ALL, "enum").stream()
              .map(type -> type.key().name())
              .toList());
    }

    @Test
    @DisplayName("トリガーを、テーブル名の順にテーブルをまたいで一覧にする")
    void listsTriggers() {
      assertEquals(
          List.of(
              "employee.trg_employee_audit", "orders.trg_orders_audit", "orders.trg_orders_touch"),
          catalog.listTriggers(SearchScope.ALL).stream()
              .map(found -> found.table().key().name() + "." + found.trigger().name())
              .toList());
    }

    private static FunctionEntry function(
        String database, String schema, String name, String arguments) {
      return new FunctionEntry(
          new ObjectKey(database, schema, name), "FUNCTION", arguments, "void", "sql", "", "{}");
    }
  }

  @Nested
  @DisplayName("名前の解決")
  class LookupTable {

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
      assertEquals(
          new ObjectKey("db1", "sales", "orders"),
          found(ObjectReference.of(null, null, "ORDERS")).key());
    }

    @Test
    @DisplayName("同名のテーブルが複数のスキーマにある場合は、候補をスキーマ名の順に返す")
    void reportsAmbiguousNames() {
      final Lookup<TableEntry> lookup =
          catalog.lookupTable(ObjectReference.of(null, null, "employee"));

      assertEquals(
          List.of("archive.employee", "hr.employee"),
          Lookups.candidates(lookup).stream().map(table -> table.key().qualifiedName()).toList());
    }

    @Test
    @DisplayName("スキーマ名を引数で指定するか、スキーマ名.テーブル名の形で指定すると1つに定まる")
    void resolvesBySchema() {
      final ObjectKey expected = new ObjectKey("db1", "hr", "employee");

      assertEquals(expected, found(ObjectReference.of(null, "hr", "employee")).key());
      assertEquals(expected, found(ObjectReference.of(null, null, "hr.employee")).key());
    }

    @Test
    @DisplayName("見つからない場合は、スキーマの指定を外して名前の似たテーブルを候補に返す")
    void suggestsSimilarNames() {
      final Lookup<TableEntry> lookup =
          catalog.lookupTable(ObjectReference.of(null, "sales", "employe"));

      assertEquals(
          List.of("archive.employee", "hr.employee", "hr.employee_profile"),
          Lookups.suggestions(lookup).stream().map(table -> table.key().qualifiedName()).toList());
    }

    @Test
    @DisplayName("似た名前も無い場合は、候補を空で返す")
    void returnsNoSuggestions() {
      final Lookup<TableEntry> lookup =
          catalog.lookupTable(ObjectReference.of(null, null, "invoice"));

      assertTrue(Lookups.suggestions(lookup).isEmpty());
    }

    private TableEntry found(ObjectReference reference) {
      return Lookups.found(catalog.lookupTable(reference));
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
      assertEquals(List.of(new ObjectKey("testdb", "sample", "customer")), related.missingTables());
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
          Lookups.found(catalog.lookupTable(ObjectReference.of(null, null, name)));
      return catalog.relatedTables(start, depth, direction);
    }
  }

  private static List<String> names(SearchResult result) {
    return result.hits().stream().map(hit -> hit.table().key().name()).toList();
  }

  private static List<ObjectKey> keys(SearchResult result) {
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
