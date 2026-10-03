package com.export_table_definition.mcp.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.catalog.Direction;
import com.export_table_definition.mcp.catalog.RelatedTables;
import com.export_table_definition.mcp.catalog.RelationKind;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.SearchQuery;
import com.export_table_definition.mcp.catalog.SearchScope;
import com.export_table_definition.mcp.catalog.TableEntry;
import com.export_table_definition.mcp.catalog.TableLookup;
import com.export_table_definition.mcp.catalog.TableReference;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * cliが出力したサンプルのスナップショット（ベースライン）を読み込めることを確かめる契約テスト<br>
 * cliの出力形式を変えるとベースラインも出力し直すため、このテストで読み込み側の追従漏れを検知する
 */
class SampleSnapshotContractTest {

  private static SchemaCatalog catalog;

  @BeforeAll
  static void readSample() {
    catalog = new SnapshotDirectoryReader().read(Path.of(System.getProperty("sampleSnapshotDir")));
  }

  @Test
  @DisplayName("サンプルの全テーブル（ビュー・マテリアライズドビューを含む）を読み込む")
  void readsAllTables() {
    assertEquals(11, catalog.tables().size());
    final TableEntry employee = find("employee");
    assertEquals("従業員", employee.logicalName());
    assertEquals("table", employee.type());
    assertTrue(employee.json().startsWith("{\"schema\":\"sample\",\"name\":\"employee\""));
    assertEquals("view", find("employee_directory_view").type());
    assertEquals("materialized_view", find("project_summary_mv").type());
  }

  @Test
  @DisplayName("サイドカーYAML由来の説明・カラム備考も検索の対象になる")
  void searchesSidecarText() {
    assertEquals(
        List.of("department"),
        catalog.searchTables(SearchQuery.of("組織単位"), SearchScope.ALL, 10).hits().stream()
            .map(hit -> hit.table().key().name())
            .toList());
    assertTrue(
        catalog.searchTables(SearchQuery.of("変更対象のテーブル名"), SearchScope.ALL, 10).hits().stream()
            .anyMatch(hit -> hit.matchedIn().contains("column:table_name")));
  }

  @Test
  @DisplayName("外部キー（複合キー・自己参照を含む）と論理リレーションを、被参照側からもたどれる")
  void followsRelationsOfSample() {
    final RelatedTables employee = catalog.relatedTables(find("employee"), 1, Direction.BOTH);
    final List<String> relations =
        employee.relations().stream()
            .map(
                found ->
                    found.relation().from().name()
                        + "->"
                        + found.relation().to().name()
                        + " "
                        + found.relation().kind()
                        + " "
                        + found.relation().cardinality())
            .toList();

    assertTrue(
        relations.contains("employee->department FOREIGN_KEY ONE_TO_MANY"), relations::toString);
    assertTrue(
        relations.contains("employee->employee FOREIGN_KEY OPTIONAL_ONE_TO_MANY"),
        relations::toString);
    assertTrue(
        relations.contains("audit_log->employee LOGICAL_RELATION OPTIONAL_ONE_TO_MANY"),
        relations::toString);
    assertTrue(
        relations.contains("employee_profile->employee FOREIGN_KEY ONE_TO_ONE"),
        relations::toString);
    assertTrue(
        relations.contains("project_assignment->employee FOREIGN_KEY ONE_TO_MANY"),
        relations::toString);

    final RelatedTables shipment = catalog.relatedTables(find("shipment"), 1, Direction.OUTGOING);
    assertEquals(
        List.of("warehouse_code", "zone_code"),
        shipment.relations().get(0).relation().fromColumns());
    assertEquals(RelationKind.FOREIGN_KEY, shipment.relations().get(0).relation().kind());
    assertTrue(shipment.missingTables().isEmpty());
  }

  private static TableEntry find(String name) {
    return ((TableLookup.Found) catalog.lookup(TableReference.of(null, null, name))).table();
  }
}
