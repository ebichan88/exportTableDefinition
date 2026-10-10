package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.TestCatalogs;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TableDefinitionResources}のテスト */
class TableDefinitionResourcesTest {

  private final SchemaCatalog catalog =
      TestCatalogs.of(
          List.of(
              table("department").logicalName("部署").description("組織のマスタ").build(),
              table("employee").logicalName("従業員").column("employee_id").build(),
              table("order_detail").logicalName("受注明細").build(),
              table("no_logical_name").build(),
              table("testdb", "archive", "employee").logicalName("従業員（過去）").build()));

  private final TableDefinitionResources resources = new TableDefinitionResources(catalog);

  @Test
  @DisplayName("全テーブルが具体的なリソースになり、名前には論理名と物理名の両方が入る")
  void listsEveryTableWithBothNames() {
    final List<SyncResourceSpecification> specs = resources.specifications();

    assertEquals(
        Set.of(
            "従業員（過去） (archive.employee)",
            "部署 (sample.department)",
            "従業員 (sample.employee)",
            "sample.no_logical_name",
            "受注明細 (sample.order_detail)"),
        specs.stream().map(spec -> spec.resource().name()).collect(Collectors.toSet()));
  }

  @Test
  @DisplayName("URIはDB名・スキーマ名・テーブル名の3区間で、MIMEタイプはJSON")
  void buildsUriAndMimeType() {
    final SyncResourceSpecification department = specOf("exporttable://testdb/sample/department");

    assertEquals("application/json", department.resource().mimeType());
    assertEquals(department.resource().name(), department.resource().title());
  }

  @Test
  @DisplayName("説明は先頭に表示名を入れ、続けてDB名・区分・テーブルの説明を並べる")
  void putsDisplayNameFirstInDescription() {
    assertEquals(
        "部署 (sample.department) / testdb / table / 組織のマスタ",
        specOf("exporttable://testdb/sample/department").resource().description());
    assertEquals(
        "sample.no_logical_name / testdb / table",
        specOf("exporttable://testdb/sample/no_logical_name").resource().description());
  }

  @Test
  @DisplayName("長い説明は切り詰め、表示名とDB名・区分は削らない")
  void truncatesLongDescription() {
    final SchemaCatalog longCatalog =
        TestCatalogs.of(List.of(table("t").logicalName("表").description("あ".repeat(500)).build()));

    final String description =
        new TableDefinitionResources(longCatalog).specifications().get(0).resource().description();

    assertTrue(description.startsWith("表 (sample.t) / testdb / table / あ"), description);
    assertTrue(description.endsWith("…"), description);
    assertEquals(121, description.length());
  }

  @Test
  @DisplayName("DB由来の名前・説明の制御文字・改行・書字方向の制御文字は、1行の表示用に空白へ置き換える")
  void removesControlCharactersFromDisplayStrings() {
    final SchemaCatalog unsafe =
        TestCatalogs.of(
            List.of(
                table("x")
                    .logicalName("受注\u001b[2J\n明細" + Character.toString(0x202E))
                    .description("説明\r\n\t2行目")
                    .build()));

    final SyncResourceSpecification spec =
        new TableDefinitionResources(unsafe).specifications().get(0);

    assertEquals("受注 [2J 明細 (sample.x)", spec.resource().name());
    assertEquals("受注 [2J 明細 (sample.x) / testdb / table / 説明 2行目", spec.resource().description());
  }

  @Test
  @DisplayName("リソースを読むと、get_tableと同じテーブル定義のJSONが返る")
  void readsTableDefinition() {
    final SyncResourceSpecification employee = specOf("exporttable://testdb/sample/employee");

    final ReadResourceResult result =
        employee.readHandler().apply(null, new ReadResourceRequest(employee.resource().uri()));

    final TextResourceContents contents =
        assertInstanceOf(TextResourceContents.class, result.contents().get(0));
    assertEquals("exporttable://testdb/sample/employee", contents.uri());
    assertEquals("application/json", contents.mimeType());
    assertTrue(contents.text().contains("\"name\":\"employee\""), contents.text());
  }

  @Test
  @DisplayName("同名のテーブルが別のスキーマにあっても、それぞれのテーブルの定義が返る")
  void readsEachTablesOwnDefinition() {
    final String sample = read("exporttable://testdb/sample/employee");
    final String archive = read("exporttable://testdb/archive/employee");

    assertTrue(sample.contains("\"schema\":\"sample\""), sample);
    assertTrue(archive.contains("\"schema\":\"archive\""), archive);
    assertFalse(archive.contains("\"schema\":\"sample\""), archive);
  }

  @Test
  @DisplayName("テーブルが無い場合は、リソースも無い")
  void hasNoResourcesWithoutTables() {
    assertTrue(new TableDefinitionResources(TestCatalogs.of(List.of())).specifications().isEmpty());
  }

  private SyncResourceSpecification specOf(String uri) {
    return resources.specifications().stream()
        .filter(spec -> spec.resource().uri().equals(uri))
        .findFirst()
        .orElseThrow();
  }

  private String read(String uri) {
    final ReadResourceResult result =
        specOf(uri).readHandler().apply(null, new ReadResourceRequest(uri));
    return ((TextResourceContents) result.contents().get(0)).text();
  }
}
