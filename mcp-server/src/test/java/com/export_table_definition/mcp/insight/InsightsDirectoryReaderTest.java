package com.export_table_definition.mcp.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.UserCorrectableException;
import com.export_table_definition.mcp.catalog.ObjectKey;
import com.export_table_definition.mcp.catalog.ViewpointEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link InsightsDirectoryReader}のテスト */
class InsightsDirectoryReaderTest {

  @TempDir Path outputBaseDir;

  private final InsightsDirectoryReader reader = new InsightsDirectoryReader();

  /** {@code --snapshot}に指定されたディレクトリ（参考情報はこの親の兄弟から読む） */
  private Path snapshotDirectory() {
    return outputBaseDir.resolve("snapshot");
  }

  @Test
  @DisplayName("snapshotディレクトリの親の兄弟（insights）から、DBごとのviewpoints.jsonを読み込む")
  void readsViewpointsFromSiblingOfSnapshotDirectory() throws IOException {
    write(
        "insights/testdb/viewpoints.json",
        "{\"formatVersion\":1,\"viewpoints\":["
            + "{\"id\":\"order\",\"name\":\"受注管理\",\"description\":\"受注から出荷までを扱う\","
            + "\"tables\":[{\"schema\":\"sales\",\"name\":\"orders\"}]}]}");
    write(
        "insights/otherdb/viewpoints.json",
        "{\"formatVersion\":1,\"viewpoints\":[{\"id\":\"hr\",\"name\":\"人事\",\"tables\":[]}]}");

    final List<ViewpointEntry> viewpoints = reader.readViewpoints(snapshotDirectory());

    assertEquals(
        List.of(
            new ViewpointEntry("otherdb", "hr", "人事", "", List.of()),
            new ViewpointEntry(
                "testdb",
                "order",
                "受注管理",
                "受注から出荷までを扱う",
                List.of(new ObjectKey("testdb", "sales", "orders")))),
        viewpoints);
  }

  @Test
  @DisplayName("insightsディレクトリが無い場合は0件とする（観点を1つも宣言していないスナップショット）")
  void returnsEmptyWhenInsightsDirectoryIsMissing() {
    assertTrue(reader.readViewpoints(snapshotDirectory()).isEmpty());
  }

  @Test
  @DisplayName("DBのディレクトリにviewpoints.jsonが無い場合は、そのDB分は0件とする")
  void returnsEmptyWhenViewpointsFileIsMissingForDatabase() throws IOException {
    Files.createDirectories(outputBaseDir.resolve("insights").resolve("testdb"));

    assertTrue(reader.readViewpoints(snapshotDirectory()).isEmpty());
  }

  @Test
  @DisplayName("未知の項目は無視する（cliが項目を追加しても読み込める）")
  void ignoresUnknownFields() throws IOException {
    write(
        "insights/testdb/viewpoints.json",
        "{\"formatVersion\":1,\"generatedBy\":\"x\",\"viewpoints\":["
            + "{\"id\":\"order\",\"name\":\"受注管理\",\"newField\":1,"
            + "\"tables\":[{\"schema\":\"sales\",\"name\":\"orders\",\"newColumnField\":true}]}]}");

    final List<ViewpointEntry> viewpoints = reader.readViewpoints(snapshotDirectory());

    assertEquals("order", viewpoints.get(0).id());
  }

  @Test
  @DisplayName("対応していない新しい形式のバージョンの場合は、MCPサーバーの更新を促す")
  void failsOnNewerFormatVersion() throws IOException {
    write("insights/testdb/viewpoints.json", "{\"formatVersion\":2,\"viewpoints\":[]}");

    final UserCorrectableException e =
        assertThrows(
            UserCorrectableException.class, () -> reader.readViewpoints(snapshotDirectory()));

    assertTrue(e.getMessage().contains("formatVersion=2"), e.getMessage());
  }

  @Test
  @DisplayName("formatVersionが無い場合は失敗にする")
  void failsWhenFormatVersionIsMissing() throws IOException {
    write("insights/testdb/viewpoints.json", "{\"viewpoints\":[]}");

    assertThrows(UserCorrectableException.class, () -> reader.readViewpoints(snapshotDirectory()));
  }

  @Test
  @DisplayName("JSONとして読めない場合は、ファイルを示して失敗にする")
  void failsOnBrokenJson() throws IOException {
    final Path file = write("insights/testdb/viewpoints.json", "{\"formatVersion\":1,<<<<<<< HEAD");

    final UserCorrectableException e =
        assertThrows(
            UserCorrectableException.class, () -> reader.readViewpoints(snapshotDirectory()));

    assertTrue(e.getMessage().contains("file=" + file), e.getMessage());
  }

  private Path write(String relativePath, String content) throws IOException {
    final Path file = outputBaseDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    return Files.writeString(file, content);
  }
}
