package com.export_table_definition.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.SearchScope;
import com.export_table_definition.mcp.catalog.ViewpointEntry;
import com.export_table_definition.mcp.insight.InsightsDirectoryReader;
import com.export_table_definition.mcp.snapshot.SnapshotDirectoryReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 観点のER図が、cliの観点ページ（ベースライン）のER図と同じ記述になることを確かめる契約テスト<br>
 * mcp-serverはcliの組み立てを共有しないため、表記（多重度・線種・箱のカラム・エスケープ）のずれをこのテストで検知する
 */
class SampleErDiagramContractTest {

  private static Path snapshotDirectory;
  private static SchemaCatalog catalog;

  @BeforeAll
  static void readSample() {
    snapshotDirectory = Path.of(System.getProperty("sampleSnapshotDir"));
    catalog =
        new SnapshotDirectoryReader()
            .read(snapshotDirectory)
            .withViewpoints(new InsightsDirectoryReader().readViewpoints(snapshotDirectory));
  }

  /** cliは関連の無い所属テーブルを図に描かないため、全所属テーブルに関連がある観点だけを比べる */
  @ParameterizedTest
  @ValueSource(strings = {"personnel", "logistics"})
  @DisplayName("全所属テーブルに関連がある観点のER図は、cliの観点ページのER図と一致する")
  void matchesCliViewpointPage(String id) throws IOException {
    final ViewpointEntry viewpoint = catalog.findViewpoint(SearchScope.ALL, id).orElseThrow();

    assertEquals(cliDiagram(id), MermaidErDiagram.render(catalog.diagramOf(viewpoint)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"project"})
  @DisplayName("関連の無い所属テーブルは、cliの観点ページのER図に箱だけを加えて描く")
  void addsTablesWithoutRelations(String id) throws IOException {
    final ViewpointEntry viewpoint = catalog.findViewpoint(SearchScope.ALL, id).orElseThrow();

    final String mermaid = MermaidErDiagram.render(catalog.diagramOf(viewpoint));

    assertTrue(mermaid.contains("    sample_project_summary_mv[\"project_summary_mv"), mermaid);
    assertEquals(
        cliDiagram(id),
        mermaid.lines()
                .filter(line -> !line.startsWith("    sample_project_summary_mv["))
                .reduce("", (joined, line) -> joined + line + "\n"));
  }

  /** cliの観点ページから、Mermaidのコードブロックの中身を取り出す */
  private static String cliDiagram(String id) throws IOException {
    final String page =
        Files.readString(
            snapshotDirectory.resolveSibling("testdb").resolve("viewpoint_testdb_" + id + ".md"));
    final int start = page.indexOf("```mermaid\n") + "```mermaid\n".length();
    return page.substring(start, page.indexOf("```\n", start));
  }
}
