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
                  table("testdb", "archive", "employee").build())));

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("search_tables・get_table・get_related_tablesを、読み取り専用のツールとして登録する")
  void registersReadOnlyTools() {
    final List<SyncToolSpecification> specifications = tools.specifications();

    assertEquals(
        List.of("search_tables", "get_table", "get_related_tables"),
        specifications.stream().map(specification -> specification.tool().name()).toList());
    assertTrue(
        specifications.stream()
            .allMatch(specification -> specification.tool().annotations().readOnlyHint()));
    assertEquals(List.of("query"), specifications.get(0).tool().inputSchema().get("required"));
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
    assertEquals("未知の引数です: verbose。使える引数: database, schema, table", text(unknown));
  }

  private CallToolResult call(String name, Map<String, Object> arguments) {
    final SyncToolSpecification specification =
        tools.specifications().stream()
            .filter(candidate -> candidate.tool().name().equals(name))
            .findFirst()
            .orElseThrow();
    return specification
        .callHandler()
        .apply(null, CallToolRequest.builder(name).arguments(arguments).build());
  }

  private JsonNode json(CallToolResult result) throws Exception {
    assertFalse(Boolean.TRUE.equals(result.isError()), () -> text(result));
    return objectMapper.readTree(text(result));
  }

  private static String text(CallToolResult result) {
    return ((TextContent) result.content().get(0)).text();
  }
}
