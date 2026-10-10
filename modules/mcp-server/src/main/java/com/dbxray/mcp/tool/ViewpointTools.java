package com.dbxray.mcp.tool;

import static com.dbxray.mcp.tool.ToolSpecifications.DATABASE_PROPERTY;
import static com.dbxray.mcp.tool.ToolSpecifications.objectSchema;
import static com.dbxray.mcp.tool.ToolSpecifications.readOnlyTool;

import com.dbxray.mcp.catalog.SchemaCatalog;
import com.dbxray.mcp.catalog.ViewpointEntry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;

/** 観点（業務ドメイン別にテーブルをまとめる切り口）の参考情報を返すツール（{@code list_viewpoints}） */
final class ViewpointTools {

  static final String LIST_VIEWPOINTS = "list_viewpoints";

  private final SchemaCatalog catalog;

  ViewpointTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            LIST_VIEWPOINTS,
            "観点一覧",
            "観点（業務ドメイン別にテーブルをまとめる切り口。例: 受注管理）の識別子・表示名・説明・所属テーブル数を返す。"
                + "観点の所属テーブルはlist_tables・search_tablesのviewpoint引数（この識別子を指定する）で絞り込める。"
                + "テーブルが所属する観点はget_tableで分かる",
            objectSchema(Map.of("database", DATABASE_PROPERTY), List.of()),
            this::listViewpoints));
  }

  private CallToolResult listViewpoints(ToolArguments arguments) {
    final List<ViewpointEntry> viewpoints = catalog.viewpoints().list(arguments.scope());
    return ToolResults.json(
        new ListViewpointsOutput(
            viewpoints.stream().map(ListViewpointsOutput.Viewpoint::of).toList()));
  }

  /** {@code list_viewpoints}の結果 */
  record ListViewpointsOutput(List<Viewpoint> viewpoints) {

    /** 1観点 */
    record Viewpoint(String database, String id, String name, String description, int tableCount) {

      static Viewpoint of(ViewpointEntry entry) {
        return new Viewpoint(
            entry.database(), entry.id(), entry.name(), entry.description(), entry.tables().size());
      }
    }
  }
}
