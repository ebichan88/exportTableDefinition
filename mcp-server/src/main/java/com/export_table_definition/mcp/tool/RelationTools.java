package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.ToolSpecifications.enumProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.integerProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.namedObjectProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;

import com.export_table_definition.mcp.catalog.Direction;
import com.export_table_definition.mcp.catalog.ObjectKey;
import com.export_table_definition.mcp.catalog.RelatedTables;
import com.export_table_definition.mcp.catalog.Relation;
import com.export_table_definition.mcp.catalog.RelationKind;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.TableEntry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;

/** テーブル間の関連をたどるツール（{@code get_related_tables}） */
final class RelationTools {

  static final String GET_RELATED_TABLES = "get_related_tables";

  /** 段数を増やすと結果が急に膨らみAIのコンテキストを圧迫するため、3段までに抑える */
  private static final int MAX_DEPTH = 3;

  private final SchemaCatalog catalog;

  RelationTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            GET_RELATED_TABLES,
            "テーブルとつながるテーブルを、外部キーと論理リレーション（DBに制約が無い関連）の両方からたどって返す。"
                + "JOINの条件（どのカラム同士でつながるか）を調べるときに使う。"
                + "cardinalityは参照先（to）1行に対する参照元（from）の行数を表す（例: ONE_TO_MANY）",
            objectSchema(
                namedObjectProperties(
                    "table",
                    TableTools.TABLE_DESCRIPTION,
                    Map.of(
                        "depth", integerProperty("たどる段数（既定1）", 1, MAX_DEPTH),
                        "direction",
                            enumProperty(
                                "outgoing: 参照先へ、incoming: 参照元へ、both: 両方（既定both）",
                                ToolArguments.lowerNames(Direction.class)))),
                List.of("table")),
            this::getRelatedTables));
  }

  /**
   * 関連の由来を、結果のJSONに出す名前にするメソッド
   *
   * @return スナップショットの項目名に揃えた{@code foreignKey}・{@code logicalRelation}
   */
  static String kindName(RelationKind kind) {
    return kind == RelationKind.FOREIGN_KEY ? "foreignKey" : "logicalRelation";
  }

  private CallToolResult getRelatedTables(ToolArguments arguments) {
    final TableEntry table =
        ObjectResolver.resolve(
            arguments, "table", "テーブル", TableTools.SEARCH_TABLES, catalog::lookupTable);
    final int depth = arguments.optionalInt("depth", 1, 1, MAX_DEPTH);
    final Direction direction =
        arguments.optionalEnum("direction", Direction.class, Direction.BOTH);
    return ToolResults.json(RelatedTablesOutput.of(catalog.relatedTables(table, depth, direction)));
  }

  /**
   * {@code get_related_tables}の結果
   *
   * @param table たどり始めたテーブル（{@code スキーマ名.テーブル名}）
   * @param tables 関連に現れたテーブルの概要（たどり始めたテーブルを含む）
   * @param missingTables 参照先として現れたが、スナップショットに含まれないテーブル
   */
  record RelatedTablesOutput(
      String table,
      List<RelationOutput> relations,
      List<TableSummary> tables,
      List<String> missingTables) {

    static RelatedTablesOutput of(RelatedTables related) {
      return new RelatedTablesOutput(
          related.start().key().qualifiedName(),
          related.relations().stream()
              .map(found -> RelationOutput.of(found.relation(), found.depth()))
              .toList(),
          related.tables().stream().map(TableSummary::of).toList(),
          related.missingTables().stream().map(ObjectKey::qualifiedName).toList());
    }
  }

  /**
   * 1つの関連
   *
   * @param kind {@code foreignKey}（外部キー）または{@code logicalRelation}（論理リレーション）
   * @param depth たどり始めたテーブルから何段目の関連か。段数を持たない結果ではnull
   */
  record RelationOutput(
      String from,
      List<String> fromColumns,
      String to,
      List<String> toColumns,
      String kind,
      String cardinality,
      String name,
      Integer depth) {

    static RelationOutput of(Relation relation, Integer depth) {
      return new RelationOutput(
          relation.from().qualifiedName(),
          relation.fromColumns(),
          relation.to().qualifiedName(),
          relation.toColumns(),
          kindName(relation.kind()),
          relation.cardinality(),
          relation.name(),
          depth);
    }
  }

  /** 関連に現れたテーブルの概要 */
  record TableSummary(String name, String logicalName, String type, String description) {

    static TableSummary of(TableEntry table) {
      return new TableSummary(
          table.key().qualifiedName(), table.logicalName(), table.type(), table.description());
    }
  }
}
