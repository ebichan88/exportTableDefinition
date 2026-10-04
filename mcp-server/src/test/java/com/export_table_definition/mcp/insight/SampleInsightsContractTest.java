package com.export_table_definition.mcp.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.catalog.ObjectKey;
import com.export_table_definition.mcp.catalog.ViewpointEntry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * cliが出力したサンプルの参考情報（ベースライン）を読み込めることを確かめる契約テスト<br>
 * cliの出力形式を変えるとベースラインも出力し直すため、このテストで読み込み側の追従漏れを検知する。 ベースラインは{@code
 * sampleSnapshotDir}（スナップショットのディレクトリ）の親の兄弟（{@code insights}）に 置かれているため、スナップショットの契約テスト（{@code
 * SampleSnapshotContractTest}）と同じシステムプロパティをそのまま使う
 */
class SampleInsightsContractTest {

  private static List<ViewpointEntry> viewpoints;

  @BeforeAll
  static void readSample() {
    viewpoints =
        new InsightsDirectoryReader()
            .readViewpoints(Path.of(System.getProperty("sampleSnapshotDir")));
  }

  @Test
  @DisplayName("サンプルの観点（宣言順）を、所属テーブルを含めて読み込む")
  void readsAllViewpoints() {
    assertEquals(
        List.of("personnel", "project", "logistics"),
        viewpoints.stream().map(ViewpointEntry::id).toList());
    assertTrue(viewpoints.stream().allMatch(viewpoint -> viewpoint.database().equals("testdb")));
  }

  @Test
  @DisplayName("観点の説明・所属テーブルを読み込む（説明が無い観点は空文字）")
  void readsDescriptionAndMembers() {
    final ViewpointEntry personnel = find("personnel");
    assertEquals("人事管理", personnel.name());
    assertTrue(personnel.description().contains("従業員と、その所属・付帯情報を扱うテーブル群。"));
    assertEquals(
        List.of(
            new ObjectKey("testdb", "sample", "department"),
            new ObjectKey("testdb", "sample", "employee"),
            new ObjectKey("testdb", "sample", "employee_profile"),
            new ObjectKey("testdb", "sample", "parking_spot")),
        personnel.tables());

    final ViewpointEntry logistics = find("logistics");
    assertEquals("", logistics.description());
    assertEquals(
        List.of(
            new ObjectKey("testdb", "sample", "shipment"),
            new ObjectKey("testdb", "sample", "warehouse_zone")),
        logistics.tables());
  }

  @Test
  @DisplayName("1つのテーブルが複数の観点に所属できる（employeeはpersonnel・projectの両方）")
  void tableCanBelongToMultipleViewpoints() {
    final ObjectKey employee = new ObjectKey("testdb", "sample", "employee");

    assertTrue(find("personnel").tables().contains(employee));
    assertTrue(find("project").tables().contains(employee));
  }

  private static ViewpointEntry find(String id) {
    return viewpoints.stream()
        .filter(viewpoint -> viewpoint.id().equals(id))
        .findFirst()
        .orElseThrow();
  }
}
