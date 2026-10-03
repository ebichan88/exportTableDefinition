package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TableDefinitionTools}のテスト */
class TableDefinitionToolsTest {

  private final TableDefinitionTools tools =
      new TableDefinitionTools(
          SchemaCatalog.of(
              List.of(
                  table("department").logicalName("部署").description("組織のマスタ").build(),
                  table("employee")
                      .logicalName("従業員")
                      .column("department_id", "部署ID", null)
                      .foreignKey("department_id", "department", "department_id")
                      .build(),
                  table("audit_log")
                      .logicalRelation("record_id", "employee", "employee_id")
                      .build(),
                  table("testdb", "archive", "employee").build(),
                  table("project")
                      .type("table")
                      .json(
                          "{\"schema\":\"sample\",\"name\":\"project\",\"type\":\"table\","
                              + "\"columns\":[{\"name\":\"project_id\"},{\"name\":\"title\"},{\"name\":\"budget\"}],"
                              + "\"indexes\":[{\"name\":\"project_pkey\"}],"
                              + "\"triggers\":[{\"name\":\"trg_project\"}],"
                              + "\"addedByNewerCli\":1}")
                      .column("project_id")
                      .column("title")
                      .column("budget")
                      .build())));

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("全体像をつかむ・探す・詳細を見る、の順に、読み取り専用のツールとして登録する")
  void registersReadOnlyTools() {
    final List<SyncToolSpecification> specifications = tools.specifications();

    assertEquals(
        List.of(
            "list_schemas",
            "search_tables",
            "list_tables",
            "get_table",
            "find_columns",
            "get_related_tables"),
        specifications.stream().map(specification -> specification.tool().name()).toList());
    assertTrue(
        specifications.stream()
            .allMatch(specification -> specification.tool().annotations().readOnlyHint()));
    assertEquals(
        List.of("query"), specification("search_tables").tool().inputSchema().get("required"));
  }

  @Test
  @DisplayName("list_schemasは、DBごとにスキーマと、スキーマごとのテーブル・ビューの数を返す")
  void listSchemas() throws Exception {
    assertEquals(
        "{\"databases\":[{\"name\":\"testdb\",\"schemas\":["
            + "{\"name\":\"archive\",\"tables\":1,\"views\":0,\"materializedViews\":0},"
            + "{\"name\":\"sample\",\"tables\":4,\"views\":0,\"materializedViews\":0}]}]}",
        json(call("list_schemas", Map.of())).toString());
  }

  @Test
  @DisplayName("list_tablesは、テーブルの概要を名前の順に返し、続きがある場合はnextOffsetを返す")
  void listTables() throws Exception {
    final JsonNode first = json(call("list_tables", Map.of("schema", "sample", "limit", 2)));

    assertEquals(4, first.get("total").asInt());
    assertEquals(2, first.get("nextOffset").asInt());
    assertEquals(List.of("audit_log", "department"), first.get("tables").findValuesAsText("name"));
    assertFalse(first.get("tables").get(1).has("description"), "既定では説明を返さない");

    final JsonNode rest =
        json(
            call(
                "list_tables",
                Map.of("schema", "sample", "offset", 2, "limit", 2, "includeDescription", true)));
    assertEquals(List.of("employee", "project"), rest.get("tables").findValuesAsText("name"));
    assertFalse(rest.has("nextOffset"), "続きが無い場合はnextOffsetを返さない");

    final JsonNode described = json(call("list_tables", Map.of("includeDescription", "true")));
    assertEquals("組織のマスタ", described.get("tables").get(2).get("description").asText());
  }

  @Test
  @DisplayName("list_tablesは区分で絞り込め、未知の区分はエラーにする")
  void listTablesByType() throws Exception {
    assertEquals(0, json(call("list_tables", Map.of("type", "view"))).get("total").asInt());

    final CallToolResult invalid = call("list_tables", Map.of("type", "index"));
    assertTrue(invalid.isError());
    assertEquals(
        "引数typeにはtable, view, materialized_viewのいずれかを指定してください。 [value=index]", text(invalid));
  }

  @Test
  @DisplayName("search_tablesは、一致したテーブルの概要と一致した項目を、値の無い項目を省いて返す")
  void searchTables() throws Exception {
    final JsonNode result = json(call("search_tables", Map.of("query", "部署")));

    assertEquals(2, result.get("total").asInt());
    final JsonNode first = result.get("tables").get(0);
    assertEquals("department", first.get("name").asText());
    assertEquals("部署", first.get("logicalName").asText());
    assertEquals("組織のマスタ", first.get("description").asText());
    assertEquals("[\"logicalName\"]", first.get("matchedIn").toString());
    final JsonNode second = result.get("tables").get(1);
    assertEquals("employee", second.get("name").asText());
    assertFalse(second.has("description"), "値の無い項目は出力しない");
  }

  @Test
  @DisplayName("search_tablesはschema・limitで絞り込める")
  void searchTablesWithScopeAndLimit() throws Exception {
    assertEquals(
        1,
        json(call("search_tables", Map.of("query", "employee", "schema", "archive")))
            .get("tables")
            .size());
    final JsonNode limited = json(call("search_tables", Map.of("query", "employee", "limit", 1)));
    assertEquals(2, limited.get("total").asInt());
    assertEquals(1, limited.get("tables").size());
  }

  @Test
  @DisplayName("get_tableは、スナップショットの1行をそのまま返す")
  void getTable() {
    assertEquals(
        "{\"schema\":\"sample\",\"name\":\"department\"}",
        text(call("get_table", Map.of("table", "department"))));
  }

  @Test
  @DisplayName("get_tableは、sectionsで指定した項目と常に返す項目だけを返し、スナップショットの未知の項目も残す")
  void getTableWithSections() throws Exception {
    assertEquals(
        "{\"schema\":\"sample\",\"name\":\"project\",\"type\":\"table\","
            + "\"triggers\":[{\"name\":\"trg_project\"}],\"addedByNewerCli\":1}",
        json(call("get_table", Map.of("table", "project", "sections", List.of("triggers"))))
            .toString());
  }

  @Test
  @DisplayName("get_tableは、columnsで指定したカラムだけを定義の並び順で返す（sectionsに無くてもcolumnsの項目は返す）")
  void getTableWithColumns() throws Exception {
    final JsonNode onlyColumns =
        json(
            call(
                "get_table",
                Map.of("table", "project", "columns", List.of("BUDGET", "project_id"))));
    assertEquals(
        "[{\"name\":\"project_id\"},{\"name\":\"budget\"}]", onlyColumns.get("columns").toString());
    assertTrue(onlyColumns.has("indexes"), "sectionsを指定しなければ他の項目も返す");

    final JsonNode withSections =
        json(
            call(
                "get_table",
                Map.of("table", "project", "sections", "indexes", "columns", List.of("title"))));
    assertEquals("[{\"name\":\"title\"}]", withSections.get("columns").toString());
    assertTrue(withSections.has("indexes"));
    assertFalse(withSections.has("triggers"));
  }

  @Test
  @DisplayName("get_tableで、テーブルに無いカラム・未知の項目を指定した場合はエラーにする")
  void getTableWithInvalidSelection() {
    final CallToolResult column =
        call("get_table", Map.of("table", "project", "columns", List.of("title", "owner")));
    assertTrue(column.isError());
    assertEquals("テーブルsample.projectにカラムownerがありません。カラム: project_id, title, budget", text(column));

    final CallToolResult section =
        call("get_table", Map.of("table", "project", "sections", List.of("columns", "partitions")));
    assertTrue(section.isError());
    assertTrue(text(section).startsWith("引数sectionsにはcolumns, indexes,"), text(section));
  }

  @Test
  @DisplayName("find_columnsは、カラムを持つテーブルと、カラムの型・参照先を返す")
  void findColumns() throws Exception {
    final JsonNode result = json(call("find_columns", Map.of("column", "部署ID")));

    assertEquals(
        "{\"total\":1,\"columns\":[{\"database\":\"testdb\",\"schema\":\"sample\","
            + "\"table\":\"employee\",\"tableLogicalName\":\"従業員\",\"column\":\"department_id\","
            + "\"logicalName\":\"部署ID\",\"type\":\"integer\",\"primaryKey\":false,\"notNull\":false,"
            + "\"references\":[{\"table\":\"sample.department\",\"column\":\"department_id\","
            + "\"kind\":\"foreignKey\"}]}]}",
        result.toString());
    assertEquals(
        0, json(call("find_columns", Map.of("column", "project"))).get("total").asInt(), "既定は完全一致");
    assertEquals(
        1,
        json(call("find_columns", Map.of("column", "project", "match", "partial")))
            .get("total")
            .asInt());
  }

  @Test
  @DisplayName("get_tableで同名のテーブルが複数ある場合は、候補を示すエラーを返す")
  void getTableAmbiguous() {
    final CallToolResult result = call("get_table", Map.of("table", "employee"));

    assertTrue(result.isError());
    assertEquals(
        "テーブルemployeeが複数あります。schema（DBが異なる場合はdatabase）を指定してください。候補: testdb:archive.employee, testdb:sample.employee",
        text(result));
  }

  @Test
  @DisplayName("get_tableで見つからない場合は、名前で検索した候補（カラム名での一致を含む）、無ければsearch_tablesを案内するエラーを返す")
  void getTableNotFound() {
    final CallToolResult similar = call("get_table", Map.of("table", "depart"));
    assertTrue(similar.isError());
    assertEquals(
        "テーブルdepartが見つかりません。名前の似たテーブル: testdb:sample.department, testdb:sample.employee",
        text(similar));

    final CallToolResult none = call("get_table", Map.of("table", "invoice"));
    assertTrue(none.isError());
    assertEquals("テーブルinvoiceが見つかりません。search_tablesで探してください。", text(none));
  }

  @Test
  @DisplayName("get_related_tablesは、関連と、関連に現れたテーブルの概要を返す")
  void getRelatedTables() throws Exception {
    final JsonNode result = json(call("get_related_tables", Map.of("table", "sample.employee")));

    assertEquals("sample.employee", result.get("table").asText());
    assertEquals(
        "[{\"from\":\"sample.employee\",\"fromColumns\":[\"department_id\"],\"to\":\"sample.department\","
            + "\"toColumns\":[\"department_id\"],\"kind\":\"foreignKey\",\"cardinality\":\"ONE_TO_MANY\","
            + "\"name\":\"employee_department_id_fkey\",\"depth\":1},"
            + "{\"from\":\"sample.audit_log\",\"fromColumns\":[\"record_id\"],\"to\":\"sample.employee\","
            + "\"toColumns\":[\"employee_id\"],\"kind\":\"logicalRelation\",\"cardinality\":\"OPTIONAL_ONE_TO_MANY\","
            + "\"name\":\"record_id\",\"depth\":1}]",
        result.get("relations").toString());
    assertEquals(
        List.of("sample.employee", "sample.department", "sample.audit_log"),
        result.get("tables").findValuesAsText("name"));
    assertFalse(result.has("missingTables"));
  }

  @Test
  @DisplayName("get_related_tablesは、direction・depthを指定できる")
  void getRelatedTablesWithDirection() throws Exception {
    final JsonNode result =
        json(
            call(
                "get_related_tables",
                Map.of("table", "department", "direction", "incoming", "depth", 2)));

    assertEquals(
        List.of("sample.employee", "sample.audit_log"),
        result.get("relations").findValuesAsText("from"));
    assertEquals(
        List.of(1, 2),
        result.get("relations").findValues("depth").stream().map(JsonNode::asInt).toList());
  }

  @Test
  @DisplayName("引数の誤りは、ツールのエラーとして直し方を返す")
  void returnsArgumentErrors() {
    final CallToolResult missing = call("search_tables", Map.of());
    assertTrue(missing.isError());
    assertEquals("引数queryを指定してください。", text(missing));

    final CallToolResult depth =
        call("get_related_tables", Map.of("table", "department", "depth", 5));
    assertTrue(depth.isError());
    assertTrue(text(depth).contains("1〜3の整数"), text(depth));

    final CallToolResult unknown =
        call("get_table", Map.of("table", "department", "verbose", true));
    assertTrue(unknown.isError());
    assertEquals(
        "未知の引数です: verbose。使える引数: columns, database, schema, sections, table", text(unknown));
  }

  private CallToolResult call(String name, Map<String, Object> arguments) {
    return specification(name)
        .callHandler()
        .apply(null, CallToolRequest.builder(name).arguments(arguments).build());
  }

  private SyncToolSpecification specification(String name) {
    return tools.specifications().stream()
        .filter(candidate -> candidate.tool().name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private JsonNode json(CallToolResult result) throws Exception {
    assertFalse(Boolean.TRUE.equals(result.isError()), () -> text(result));
    return objectMapper.readTree(text(result));
  }

  private static String text(CallToolResult result) {
    return ((TextContent) result.content().get(0)).text();
  }
}
