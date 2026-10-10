package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.ToolSpecifications.enumProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.integerProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.namedObjectProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringProperty;

import com.export_table_definition.mcp.catalog.DiagramScope;
import com.export_table_definition.mcp.catalog.Direction;
import com.export_table_definition.mcp.catalog.ObjectKey;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.TableEntry;
import com.export_table_definition.mcp.catalog.ViewpointEntry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** ER図をMermaid記法で返すツール（{@code get_er_diagram}） */
final class DiagramTools {

  static final String GET_ER_DIAGRAM = "get_er_diagram";

  /** 関連のたどりと同じく、段数を増やすと図が急に膨らむため3段までに抑える */
  private static final int MAX_DEPTH = 3;

  /** Mermaidが描画できる大きさに収めるための、図に描くテーブル数の上限（cliの{@code erDiagramMaxNodes}の既定値に揃える） */
  static final int MAX_NODES = 80;

  /** {@code table}を指定した場合だけ使える引数 */
  private static final List<String> TABLE_ONLY_ARGUMENTS = List.of("schema", "depth", "direction");

  private final SchemaCatalog catalog;

  DiagramTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            GET_ER_DIAGRAM,
            "ER図（Mermaid）",
            "ER図をMermaid記法（erDiagram）で返す。tableを指定するとそのテーブルから関連をたどった範囲を、"
                + "viewpoint（list_viewpointsのid）を指定すると観点の所属テーブルとその間の関連を描く。"
                + "外部キーは実線、論理リレーション（DBに制約が無い関連）は破線で、箱には関連をつなぐカラムを載せる。"
                + "利用者に図を見せるときは、mermaidの値を手を加えずに```mermaidのコードブロックに入れて示す"
                + "（Mermaidを描画できるクライアントでは図として表示される）",
            objectSchema(
                namedObjectProperties(
                    "table",
                    TableTools.TABLE_DESCRIPTION + "。viewpointとどちらか一方を指定する",
                    Map.of(
                        "viewpoint",
                            stringProperty("観点の識別子（list_viewpointsのid）。tableとどちらか一方を指定する"),
                        "depth",
                            integerProperty("tableからたどる段数（既定1）。tableを指定した場合だけ使える", 1, MAX_DEPTH),
                        "direction",
                            enumProperty(
                                "outgoing: 参照先へ、incoming: 参照元へ、both: 両方（既定both）。tableを指定した場合だけ使える",
                                ToolArguments.lowerNames(Direction.class)))),
                List.of()),
            this::getErDiagram));
  }

  private CallToolResult getErDiagram(ToolArguments arguments) {
    final boolean byTable = arguments.isPresent("table");
    final Optional<ViewpointEntry> viewpoint = ViewpointResolver.resolve(catalog, arguments);
    if (byTable == viewpoint.isPresent()) {
      throw new InvalidToolArgumentException("引数tableとviewpointは、どちらか一方を指定してください。");
    }
    if (viewpoint.isPresent()) {
      final List<String> unusable = TABLE_ONLY_ARGUMENTS.stream().filter(arguments::isPresent).toList();
      if (!unusable.isEmpty()) {
        throw new InvalidToolArgumentException(
            "引数" + String.join("・", unusable) + "は、tableを指定した場合だけ使えます。");
      }
      return render(catalog.diagramOf(viewpoint.get()), null, viewpoint.get().id());
    }
    final TableEntry table =
        ObjectResolver.resolve(
            arguments, "table", "テーブル", TableTools.SEARCH_TABLES, catalog::lookupTable);
    final int depth = arguments.optionalInt("depth", 1);
    final Direction direction =
        arguments.optionalEnum("direction", Direction.class, Direction.BOTH);
    return render(
        DiagramScope.of(catalog.relatedTables(table, depth, direction)),
        table.key().qualifiedName(),
        null);
  }

  private static CallToolResult render(DiagramScope scope, String table, String viewpoint) {
    final List<ObjectKey> nodes = scope.nodes();
    if (nodes.size() > MAX_NODES) {
      throw new InvalidToolArgumentException(
          "図に描くテーブルが"
              + nodes.size()
              + "件となり、上限（"
              + MAX_NODES
              + "件）を超えます。"
              + (table != null
                  ? "depthを小さくするか、directionで向きを絞ってください。"
                  : "観点の中の個別のテーブルをtableに指定して、周辺だけを描いてください。"));
    }
    return ToolResults.json(
        new ErDiagramOutput(
            table,
            viewpoint,
            MermaidErDiagram.render(scope),
            nodes.stream().map(ObjectKey::qualifiedName).toList(),
            scope.missingTables().stream().map(ObjectKey::qualifiedName).toList(),
            scope.relations().isEmpty() ? "図に描くテーブルの間に、外部キー・論理リレーションはありません。" : null));
  }

  /**
   * {@code get_er_diagram}の結果
   *
   * @param table 起点にしたテーブル（{@code スキーマ名.テーブル名}）。観点を指定した場合はnull
   * @param viewpoint 指定した観点の識別子。テーブルを指定した場合はnull
   * @param mermaid Mermaidの{@code erDiagram}の記述（コードブロックの囲みを含まない）
   * @param tables 図に描いたテーブル（{@code スキーマ名.テーブル名}）
   * @param missingTables 図の範囲に現れたが、スナップショットに含まれないテーブル。関連の参照先の場合は、カラムの無い箱として描く
   * @param message 関連が1つも無い場合に、その旨を示す。ある場合はnull
   */
  record ErDiagramOutput(
      String table,
      String viewpoint,
      String mermaid,
      List<String> tables,
      List<String> missingTables,
      String message) {}
}
