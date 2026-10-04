package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.catalog.ColumnEntry;
import com.export_table_definition.mcp.catalog.DatabaseEntry;
import com.export_table_definition.mcp.catalog.FunctionEntry;
import com.export_table_definition.mcp.catalog.ObjectKey;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.SequenceEntry;
import com.export_table_definition.mcp.catalog.TypeEntry;
import com.export_table_definition.mcp.catalog.ViewpointEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TableDefinitionTools}のテスト */
class TableDefinitionToolsTest {

  private final TableDefinitionTools tools =
      new TableDefinitionTools(
          SchemaCatalog.of(
                  List.of(new DatabaseEntry("testdb", "PostgreSQL")),
                  List.of(
                      table("department").logicalName("部署").description("組織のマスタ").build(),
                      table("employee")
                          .logicalName("従業員")
                          .column(
                              new ColumnEntry(
                                  "employee_id",
                                  null,
                                  "integer",
                                  true,
                                  true,
                                  "nextval('sample.employee_id_seq'::regclass)",
                                  null))
                          .column(
                              new ColumnEntry(
                                  "status", null, "sample.status", false, true, null, null))
                          .column("department_id", "部署ID", null)
                          .foreignKey("department_id", "department", "department_id")
                          .trigger("trg_employee_audit", "sample.log_change")
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
                          .build()),
                  List.of(
                      function(
                          "calc_bonus",
                          "p_salary numeric",
                          "{\"schema\":\"sample\",\"name\":\"calc_bonus\",\"kind\":\"FUNCTION\","
                              + "\"arguments\":\"p_salary numeric\",\"result\":\"numeric\",\"language\":\"sql\","
                              + "\"definition\":\"CREATE FUNCTION ...\"}"),
                      function(
                          "calc_bonus",
                          "p_salary numeric, p_rate numeric",
                          "{\"schema\":\"sample\",\"name\":\"calc_bonus\",\"kind\":\"FUNCTION\","
                              + "\"arguments\":\"p_salary numeric, p_rate numeric\",\"result\":\"numeric\","
                              + "\"language\":\"sql\",\"definition\":\"CREATE FUNCTION ...\"}"),
                      new FunctionEntry(
                          new ObjectKey("testdb", "sample", "log_change"),
                          "FUNCTION",
                          "",
                          "trigger",
                          "plpgsql",
                          "{}"),
                      function(
                          "withhold_tax",
                          "p_price numeric",
                          "{\"schema\":\"sample\",\"name\":\"withhold_tax\",\"kind\":\"FUNCTION\","
                              + "\"arguments\":\"p_price numeric\",\"result\":\"numeric\",\"language\":\"sql\","
                              + "\"definition\":\"CREATE FUNCTION withhold_tax(p_price numeric) ...\"}"),
                      function(
                          "withhold_tax",
                          "p_price numeric, p_rate numeric",
                          "{\"schema\":\"sample\",\"name\":\"withhold_tax\",\"kind\":\"FUNCTION\","
                              + "\"arguments\":\"p_price numeric, p_rate numeric\",\"result\":\"numeric\","
                              + "\"language\":\"sql\",\"definition\":\""
                              + "A".repeat(4500)
                              + "\"}")),
                  List.of(
                      new SequenceEntry(
                          new ObjectKey("testdb", "sample", "employee_id_seq"),
                          "employee.employee_id",
                          "{\"schema\":\"sample\",\"name\":\"employee_id_seq\",\"ownedBy\":\"employee.employee_id\"}")),
                  List.of(
                      new TypeEntry(
                          new ObjectKey("testdb", "sample", "status"),
                          "ENUM",
                          "{\"schema\":\"sample\",\"name\":\"status\",\"category\":\"ENUM\",\"definition\":\"A, B\"}")))
              .withViewpoints(
                  List.of(
                      new ViewpointEntry(
                          "testdb",
                          "org",
                          "組織",
                          "",
                          List.of(
                              new ObjectKey("testdb", "sample", "department"),
                              new ObjectKey("testdb", "sample", "employee"))))));

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("全体像をつかむ・探す・詳細を見る、の順に、読み取り専用のツールとして登録する")
  void registersReadOnlyTools() {
    final List<SyncToolSpecification> specifications = tools.specifications();

    assertEquals(
        List.of(
            "list_schemas",
            "list_viewpoints",
            "search_tables",
            "list_tables",
            "get_table",
            "find_columns",
            "get_related_tables",
            "find_join_path",
            "list_functions",
            "get_function",
            "list_sequences",
            "get_sequence",
            "list_types",
            "get_type",
            "list_triggers"),
        specifications.stream().map(specification -> specification.tool().name()).toList());
    assertTrue(
        specifications.stream()
            .allMatch(specification -> specification.tool().annotations().readOnlyHint()));
    assertTrue(
        specifications.stream().allMatch(specification -> !specification.tool().title().isBlank()),
        "MCPクライアントの表示用に、全ツールにtitleを付ける");
    assertEquals(
        List.of("query"), specification("search_tables").tool().inputSchema().get("required"));
  }

  @Test
  @DisplayName("list_schemasは、DBごとにスキーマと、スキーマごとのオブジェクトの数を返す")
  void listSchemas() throws Exception {
    assertEquals(
        "{\"databases\":[{\"name\":\"testdb\",\"dbms\":\"PostgreSQL\",\"schemas\":["
            + "{\"name\":\"archive\",\"tables\":1,\"views\":0,\"materializedViews\":0,"
            + "\"functions\":0,\"sequences\":0,\"types\":0},"
            + "{\"name\":\"sample\",\"tables\":4,\"views\":0,\"materializedViews\":0,"
            + "\"functions\":5,\"sequences\":1,\"types\":1}]}]}",
        json(call("list_schemas", Map.of())).toString());
  }

  @Test
  @DisplayName("list_viewpointsは、識別子・表示名・説明・所属テーブル数を返す")
  void listViewpoints() throws Exception {
    assertEquals(
        "{\"viewpoints\":[{\"database\":\"testdb\",\"id\":\"org\",\"name\":\"組織\",\"tableCount\":2}]}",
        json(call("list_viewpoints", Map.of())).toString());
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
  @DisplayName("list_tablesは、viewpointで指定した観点の所属テーブルだけに絞り込める")
  void listTablesByViewpoint() throws Exception {
    final JsonNode result = json(call("list_tables", Map.of("viewpoint", "org")));

    assertEquals(List.of("department", "employee"), result.get("tables").findValuesAsText("name"));
  }

  @Test
  @DisplayName("list_tablesは、存在しない観点を指定すると、観点の一覧を示すエラーを返す")
  void listTablesByUnknownViewpoint() {
    final CallToolResult result = call("list_tables", Map.of("viewpoint", "nope"));

    assertTrue(result.isError());
    assertEquals("観点nopeが見つかりません。観点: org", text(result));
  }

  @Test
  @DisplayName("search_tablesは、viewpointで指定した観点の所属テーブルだけに絞り込む（観点外の一致は除く）")
  void searchTablesByViewpoint() throws Exception {
    assertEquals(2, json(call("search_tables", Map.of("query", "employee"))).get("total").asInt());

    final JsonNode result =
        json(call("search_tables", Map.of("query", "employee", "viewpoint", "org")));
    assertEquals(1, result.get("total").asInt());
    final JsonNode hit = result.get("tables").get(0);
    assertEquals("sample", hit.get("schema").asText());
    assertEquals("employee", hit.get("name").asText());
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
  @DisplayName("list_functionsは、関数のシグネチャを定義本体を除いて返し、名前の一部で絞り込める")
  void listFunctions() throws Exception {
    final JsonNode result = json(call("list_functions", Map.of("query", "BONUS")));

    assertEquals(2, result.get("total").asInt());
    assertEquals(
        "{\"database\":\"testdb\",\"schema\":\"sample\",\"name\":\"calc_bonus\",\"kind\":\"FUNCTION\","
            + "\"arguments\":\"p_salary numeric\",\"result\":\"void\",\"language\":\"sql\"}",
        result.get("functions").get(0).toString());
  }

  @Test
  @DisplayName("get_functionは、オーバーロードをまとめて、定義本体を除いたシグネチャを返す")
  void getFunction() throws Exception {
    assertEquals(
        "{\"database\":\"testdb\",\"schema\":\"sample\",\"name\":\"calc_bonus\",\"overloads\":["
            + "{\"kind\":\"FUNCTION\",\"arguments\":\"p_salary numeric\",\"result\":\"numeric\",\"language\":\"sql\"},"
            + "{\"kind\":\"FUNCTION\",\"arguments\":\"p_salary numeric, p_rate numeric\",\"result\":\"numeric\","
            + "\"language\":\"sql\"}]}",
        json(call("get_function", Map.of("function", "sample.calc_bonus"))).toString());

    final CallToolResult notFound = call("get_function", Map.of("function", "calc"));
    assertTrue(notFound.isError());
    assertEquals("関数calcが見つかりません。名前の似た関数: testdb:sample.calc_bonus", text(notFound));
  }

  @Test
  @DisplayName("get_functionは、トリガー関数を実行するトリガーも返す")
  void getFunctionWithTriggers() throws Exception {
    assertEquals(
        "[{\"table\":\"sample.employee\",\"trigger\":\"trg_employee_audit\",\"timing\":\"AFTER\","
            + "\"events\":[\"INSERT\"]}]",
        json(call("get_function", Map.of("function", "log_change")))
            .get("calledByTriggers")
            .toString());
  }

  @Test
  @DisplayName("get_functionは、includeDefinitionを指定すると定義本体を返す。" + "オーバーロードの本体が同じ場合は1つにまとめる")
  void getFunctionWithSharedDefinition() throws Exception {
    final JsonNode result =
        json(call("get_function", Map.of("function", "calc_bonus", "includeDefinition", true)));
    assertEquals("CREATE FUNCTION ...", result.get("definition").asText());
    assertFalse(result.get("overloads").get(0).has("definition"));
    assertFalse(result.get("overloads").get(1).has("definition"));

    final JsonNode withoutDefinition = json(call("get_function", Map.of("function", "calc_bonus")));
    assertFalse(withoutDefinition.has("definition"), "includeDefinition未指定では返さない");
  }

  @Test
  @DisplayName("get_functionは、includeDefinition指定時にオーバーロードの本体が異なる場合はそれぞれ残し、" + "長い場合は切り詰める")
  void getFunctionWithDifferingDefinitions() throws Exception {
    final JsonNode result =
        json(call("get_function", Map.of("function", "withhold_tax", "includeDefinition", true)));
    assertFalse(result.has("definition"), "本体が異なる場合はオーバーロードごとに返す");

    final JsonNode overloads = result.get("overloads");
    assertEquals(
        "CREATE FUNCTION withhold_tax(p_price numeric) ...",
        overloads.get(0).get("definition").asText());

    final String truncated = overloads.get(1).get("definition").asText();
    assertTrue(truncated.length() < 4500, "長い本体は切り詰められる");
    assertTrue(truncated.contains("切り詰め"), truncated);
  }

  @Test
  @DisplayName("list_sequences・get_sequenceは、シーケンスの所有カラム・定義と、採番に使うカラムを返す")
  void sequences() throws Exception {
    assertEquals(
        "{\"total\":1,\"sequences\":[{\"database\":\"testdb\",\"schema\":\"sample\","
            + "\"name\":\"employee_id_seq\",\"ownedBy\":\"employee.employee_id\"}]}",
        json(call("list_sequences", Map.of())).toString());
    assertEquals(
        "{\"schema\":\"sample\",\"name\":\"employee_id_seq\",\"ownedBy\":\"employee.employee_id\","
            + "\"usedByColumns\":[\"sample.employee.employee_id\"]}",
        text(call("get_sequence", Map.of("sequence", "EMPLOYEE_ID_SEQ"))));

    final CallToolResult notFound = call("get_sequence", Map.of("sequence", "invoice_seq"));
    assertTrue(notFound.isError());
    assertEquals("シーケンスinvoice_seqが見つかりません。list_sequencesで探してください。", text(notFound));
  }

  @Test
  @DisplayName("list_types・get_typeは、ユーザー定義型の種別・定義と型を使うカラムを返し、種別で絞り込める")
  void types() throws Exception {
    assertEquals(1, json(call("list_types", Map.of("category", "enum"))).get("total").asInt());
    assertEquals(0, json(call("list_types", Map.of("category", "DOMAIN"))).get("total").asInt());
    assertEquals(
        "{\"schema\":\"sample\",\"name\":\"status\",\"category\":\"ENUM\",\"definition\":\"A, B\","
            + "\"usedByColumns\":[\"sample.employee.status\"]}",
        text(call("get_type", Map.of("type", "status"))));
  }

  @Test
  @DisplayName("list_triggersは、テーブルをまたいでトリガーと実行される関数を返す")
  void listTriggers() throws Exception {
    assertEquals(
        "{\"total\":1,\"triggers\":[{\"database\":\"testdb\",\"schema\":\"sample\",\"table\":\"employee\","
            + "\"name\":\"trg_employee_audit\",\"timing\":\"AFTER\",\"events\":[\"INSERT\"],"
            + "\"orientation\":\"ROW\",\"function\":\"sample.log_change\"}]}",
        json(call("list_triggers", Map.of())).toString());
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
  @DisplayName("get_tableはtableに配列を指定すると、複数のテーブルをまとめて返す")
  void getTableMultiple() throws Exception {
    final JsonNode result =
        json(call("get_table", Map.of("table", List.of("department", "project"))));

    final JsonNode tables = result.get("tables");
    assertEquals(2, tables.size());
    assertEquals("department", tables.get(0).get("name").asText());
    assertEquals("project", tables.get(1).get("name").asText());
  }

  @Test
  @DisplayName("get_tableで配列のうち1つでも解決できない名前があれば、まとめてエラーを返す")
  void getTableMultipleWithError() {
    final CallToolResult result =
        call("get_table", Map.of("table", List.of("department", "invoice", "employee")));

    assertTrue(result.isError());
    assertTrue(text(result).contains("テーブルinvoiceが見つかりません"), text(result));
    assertTrue(text(result).contains("テーブルemployeeが複数あります"), text(result));
  }

  @Test
  @DisplayName("get_tableでtableを複数指定した場合、columnsは使えない")
  void getTableMultipleWithColumns() {
    final CallToolResult result =
        call(
            "get_table",
            Map.of("table", List.of("department", "project"), "columns", List.of("title")));

    assertTrue(result.isError());
    assertEquals("引数columnsは、tableを1件指定した場合だけ使えます。", text(result));
  }

  @Test
  @DisplayName("get_tableでtableの件数が上限を超える場合はエラーにする")
  void getTableTooMany() {
    final List<String> names = IntStream.range(0, 11).mapToObj(i -> "t" + i).toList();

    final CallToolResult result = call("get_table", Map.of("table", names));

    assertTrue(result.isError());
    assertEquals("引数tableは10件までにしてください。 [count=11]", text(result));
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
  @DisplayName("find_join_pathは、最短の経路をたどる順のテーブルと、各段の関連で返す")
  void findJoinPath() throws Exception {
    final JsonNode result =
        json(call("find_join_path", Map.of("from", "audit_log", "to", "sample.department")));

    assertEquals("sample.audit_log", result.get("from").asText());
    assertEquals(1, result.get("paths").size());
    final JsonNode path = result.get("paths").get(0);
    assertEquals(
        "[\"sample.audit_log\",\"sample.employee\",\"sample.department\"]",
        path.get("tables").toString());
    assertEquals(
        List.of("logicalRelation", "foreignKey"), path.get("joins").findValuesAsText("kind"));
    assertFalse(path.get("joins").get(0).has("depth"));
    assertFalse(result.has("hasMore"));
  }

  @Test
  @DisplayName("find_join_pathでつながらない場合は、次に何をすればよいかを返し、同じテーブルはエラーにする")
  void findJoinPathUnreachable() throws Exception {
    final JsonNode result =
        json(call("find_join_path", Map.of("from", "project", "to", "department")));
    assertFalse(result.has("paths"));
    assertTrue(result.get("message").asText().contains("maxLength"), result.toString());

    final CallToolResult same =
        call("find_join_path", Map.of("from", "department", "to", "sample.department"));
    assertTrue(same.isError());
    assertEquals("fromとtoに同じテーブルが指定されています。", text(same));
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

  private static FunctionEntry function(String name, String arguments, String json) {
    return new FunctionEntry(
        new ObjectKey("testdb", "sample", name), "FUNCTION", arguments, "void", "sql", json);
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
