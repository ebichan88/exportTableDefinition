package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.TableEntry;
import com.export_table_definition.mcp.catalog.TestCatalogs;
import com.export_table_definition.mcp.tool.TableDefinitionResources.Mode;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest.CompleteArgument;
import io.modelcontextprotocol.spec.McpSchema.CompleteRequest.CompleteContext;
import io.modelcontextprotocol.spec.McpSchema.CompleteResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceReference;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TableDefinitionResources}のテスト */
class TableDefinitionResourcesTest {

  private static final ResourceReference REFERENCE =
      new ResourceReference(TableResourceUri.TEMPLATE);

  private final SchemaCatalog catalog =
      TestCatalogs.of(
          List.of(
              table("department").logicalName("部署").description("組織のマスタ").build(),
              table("employee").logicalName("従業員").column("employee_id").build(),
              table("order_detail").logicalName("受注明細").build(),
              table("no_logical_name").build(),
              table("testdb", "archive", "employee").logicalName("従業員（過去）").build()));

  private final TableDefinitionResources all =
      new TableDefinitionResources(catalog, EnumSet.allOf(Mode.class));

  @Test
  @DisplayName("出し方は list・template・none をカンマ区切りで指定でき、未指定は両方になる")
  void parsesModes() {
    assertEquals(EnumSet.allOf(Mode.class), Mode.parse(null));
    assertEquals(EnumSet.allOf(Mode.class), Mode.parse(" "));
    assertEquals(Set.of(Mode.LIST), Mode.parse("list"));
    assertEquals(Set.of(Mode.TEMPLATE), Mode.parse(" Template "));
    assertEquals(EnumSet.allOf(Mode.class), Mode.parse("list,template"));
    assertEquals(Set.of(), Mode.parse("none"));
    assertThrows(IllegalArgumentException.class, () -> Mode.parse("list,all"));
  }

  @Test
  @DisplayName("listだけの場合はテンプレートと補完を出さず、templateだけの場合は具体的なリソースを出さない")
  void switchesBetweenModes() {
    final TableDefinitionResources listOnly =
        new TableDefinitionResources(catalog, Set.of(Mode.LIST));
    final TableDefinitionResources templateOnly =
        new TableDefinitionResources(catalog, Set.of(Mode.TEMPLATE));

    assertEquals(5, listOnly.resourceSpecifications().size());
    assertTrue(listOnly.templateSpecifications().isEmpty());
    assertTrue(listOnly.completionSpecifications().isEmpty());
    assertTrue(templateOnly.resourceSpecifications().isEmpty());
    assertEquals(1, templateOnly.templateSpecifications().size());
    assertEquals(1, templateOnly.completionSpecifications().size());
  }

  @Test
  @DisplayName("全テーブルが具体的なリソースになり、名前には論理名と物理名の両方が入る")
  void listsEveryTableWithBothNames() {
    final List<SyncResourceSpecification> specs = all.resourceSpecifications();

    assertEquals(
        Set.of(
            "従業員（過去） (archive.employee)",
            "部署 (sample.department)",
            "従業員 (sample.employee)",
            "sample.no_logical_name",
            "受注明細 (sample.order_detail)"),
        specs.stream().map(spec -> spec.resource().name()).collect(Collectors.toSet()));
    final SyncResourceSpecification department =
        specs.stream()
            .filter(spec -> spec.resource().uri().endsWith("/sample/department"))
            .findFirst()
            .orElseThrow();
    assertEquals("exporttable://testdb/sample/department", department.resource().uri());
    assertEquals("application/json", department.resource().mimeType());
    assertEquals(
        "部署 (sample.department) / testdb / table / 組織のマスタ", department.resource().description());
  }

  @Test
  @DisplayName("リソースを読むと、get_tableと同じテーブル定義のJSONが返る")
  void readsTableDefinition() {
    final SyncResourceSpecification employee =
        all.resourceSpecifications().stream()
            .filter(spec -> spec.resource().uri().endsWith("/sample/employee"))
            .findFirst()
            .orElseThrow();

    final ReadResourceResult result =
        employee.readHandler().apply(null, new ReadResourceRequest(employee.resource().uri()));

    final TextResourceContents contents =
        assertInstanceOf(TextResourceContents.class, result.contents().get(0));
    assertEquals("exporttable://testdb/sample/employee", contents.uri());
    assertEquals("application/json", contents.mimeType());
    assertTrue(contents.text().contains("\"name\":\"employee\""), contents.text());
  }

  @Test
  @DisplayName("テンプレートに当てはまるURIは、一覧に無くても読める。見つからないURIはリソースが無いエラーになる")
  void readsByTemplateUri() {
    final var template = all.templateSpecifications().get(0);

    final ReadResourceResult found =
        template
            .readHandler()
            .apply(null, new ReadResourceRequest("exporttable://testdb/archive/employee"));
    assertTrue(
        ((TextResourceContents) found.contents().get(0)).text().contains("\"schema\":\"archive\""));

    final McpError notFound =
        assertThrows(
            McpError.class,
            () ->
                template
                    .readHandler()
                    .apply(null, new ReadResourceRequest("exporttable://testdb/sample/nothing")));
    assertEquals(
        McpError.RESOURCE_NOT_FOUND.apply("x").getJsonRpcError().code(),
        notFound.getJsonRpcError().code());
    assertThrows(
        IllegalArgumentException.class,
        () -> template.readHandler().apply(null, new ReadResourceRequest("exporttable://x")));
  }

  @Test
  @DisplayName("テーブル名の補完は、物理名でも論理名でも候補が出る")
  void completesByPhysicalAndLogicalName() {
    assertEquals(List.of("order_detail"), complete("table", "order_det", Map.of()));
    assertEquals(List.of("order_detail"), complete("table", "受注", Map.of()));
    assertEquals(List.of("employee"), complete("table", "従業員", Map.of()));
  }

  @Test
  @DisplayName("テーブル名の補完は、物理名の前方一致、部分一致、論理名のみの一致の順に並ぶ")
  void ranksPhysicalPrefixFirst() {
    final SchemaCatalog ranked =
        TestCatalogs.of(
            List.of(
                table("x_order").build(),
                table("order_header").build(),
                table("billing").logicalName("order関連").build()));
    final TableDefinitionResources resources =
        new TableDefinitionResources(ranked, Set.of(Mode.TEMPLATE));

    final CompleteResult result =
        resources
            .completionSpecifications()
            .get(0)
            .completionHandler()
            .apply(null, new CompleteRequest(REFERENCE, new CompleteArgument("table", "order")));

    assertEquals(List.of("order_header", "x_order", "billing"), result.completion().values());
  }

  @Test
  @DisplayName("テーブル名の補完は、確定済みのスキーマで絞り込める")
  void narrowsByResolvedSchema() {
    assertEquals(List.of("employee"), complete("table", "emp", Map.of("schema", "archive")));
    assertEquals(List.of("employee"), complete("table", "emp", Map.of("schema", "sample")));
    assertEquals(List.of(), complete("table", "emp", Map.of("schema", "no_such")));
  }

  @Test
  @DisplayName("DB名・スキーマ名の補完は、重複を除いて名前順に返す")
  void completesDatabaseAndSchema() {
    assertEquals(List.of("testdb"), complete("database", "", Map.of()));
    assertEquals(List.of("archive", "sample"), complete("schema", "", Map.of()));
    assertEquals(List.of("sample"), complete("schema", "sam", Map.of("database", "testdb")));
    assertEquals(List.of(), complete("unknown", "", Map.of()));
  }

  @Test
  @DisplayName("補完で返す名前は、URIの区間に使えるようエンコードされる")
  void encodesCompletionValues() {
    final SchemaCatalog special =
        TestCatalogs.of(List.of(table("受注/明細").logicalName("受注明細").build()));
    final TableDefinitionResources resources =
        new TableDefinitionResources(special, Set.of(Mode.TEMPLATE));

    final CompleteResult result =
        resources
            .completionSpecifications()
            .get(0)
            .completionHandler()
            .apply(null, new CompleteRequest(REFERENCE, new CompleteArgument("table", "")));

    assertEquals(List.of("%E5%8F%97%E6%B3%A8%2F%E6%98%8E%E7%B4%B0"), result.completion().values());
  }

  @Test
  @DisplayName("補完の候補は100件までで、残りがあれば合計件数とhasMoreを返す")
  void limitsCompletions() {
    final List<TableEntry> tables = new ArrayList<>();
    IntStream.range(0, 150)
        .forEach(i -> tables.add(table("t_" + String.format("%03d", i)).build()));
    final TableDefinitionResources resources =
        new TableDefinitionResources(TestCatalogs.of(tables), Set.of(Mode.TEMPLATE));

    final CompleteResult result =
        resources
            .completionSpecifications()
            .get(0)
            .completionHandler()
            .apply(null, new CompleteRequest(REFERENCE, new CompleteArgument("table", "t_")));

    assertEquals(100, result.completion().values().size());
    assertEquals(150, result.completion().total());
    assertTrue(result.completion().hasMore());
    assertEquals("t_000", result.completion().values().get(0));
    assertFalse(result.completion().values().contains("t_100"));
  }

  private List<String> complete(String variable, String typed, Map<String, String> resolved) {
    final CompleteResult result =
        all.completionSpecifications()
            .get(0)
            .completionHandler()
            .apply(
                null,
                new CompleteRequest(
                    REFERENCE,
                    new CompleteArgument(variable, typed),
                    new CompleteContext(resolved)));
    return result.completion().values();
  }
}
