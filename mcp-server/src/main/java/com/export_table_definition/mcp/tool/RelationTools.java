package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.ToolSpecifications.enumProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.integerProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.namedObjectProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringProperty;

import com.export_table_definition.mcp.catalog.Direction;
import com.export_table_definition.mcp.catalog.JoinPath;
import com.export_table_definition.mcp.catalog.JoinPaths;
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

/** テーブル間の関連をたどるツール（{@code get_related_tables}・{@code find_join_path}） */
final class RelationTools {

  static final String GET_RELATED_TABLES = "get_related_tables";
  static final String FIND_JOIN_PATH = "find_join_path";

  /** 段数を増やすと結果が急に膨らみAIのコンテキストを圧迫するため、3段までに抑える */
  private static final int MAX_DEPTH = 3;

  /** 経路の探索は最短の経路だけを返すため結果は膨らまないが、長すぎる経路はJOINの候補として現実的でない */
  private static final int DEFAULT_MAX_LENGTH = 4;

  private static final int MAX_MAX_LENGTH = 6;
  private static final int DEFAULT_PATH_LIMIT = 5;
  private static final int MAX_PATH_LIMIT = 20;

  private final SchemaCatalog catalog;

  RelationTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            GET_RELATED_TABLES,
            "関連テーブル取得",
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
            this::getRelatedTables),
        readOnlyTool(
            FIND_JOIN_PATH,
            "JOIN経路探索",
            "2つのテーブルをつなぐ最短のJOIN経路を、外部キーと論理リレーションを向きを問わずたどって返す。"
                + "経路上の各段で、どのカラム同士でつながるかを返す。直接の関連が無いテーブル同士をJOINするときに使う。"
                + "同じ長さの経路が複数ある場合はすべて（limitまで）返す",
            objectSchema(
                Map.of(
                    "from", stringProperty("始点のテーブル名。スキーマ名.テーブル名の形でもよい"),
                    "to", stringProperty("終点のテーブル名。スキーマ名.テーブル名の形でもよい"),
                    "database", ToolSpecifications.DATABASE_PROPERTY,
                    "maxLength",
                        integerProperty(
                            "経路の関連の数の上限（既定" + DEFAULT_MAX_LENGTH + "）", 1, MAX_MAX_LENGTH),
                    "limit",
                        integerProperty(
                            "返す経路の数の上限（既定" + DEFAULT_PATH_LIMIT + "）", 1, MAX_PATH_LIMIT)),
                List.of("from", "to")),
            this::findJoinPath));
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
    final TableEntry table = resolveTable(arguments, "table");
    final int depth = arguments.optionalInt("depth", 1, 1, MAX_DEPTH);
    final Direction direction =
        arguments.optionalEnum("direction", Direction.class, Direction.BOTH);
    return ToolResults.json(RelatedTablesOutput.of(catalog.relatedTables(table, depth, direction)));
  }

  private CallToolResult findJoinPath(ToolArguments arguments) {
    final TableEntry from = resolveTable(arguments, "from");
    final TableEntry to = resolveTable(arguments, "to");
    if (from.key().equals(to.key())) {
      throw new InvalidToolArgumentException("fromとtoに同じテーブルが指定されています。");
    }
    final int maxLength = arguments.optionalInt("maxLength", DEFAULT_MAX_LENGTH, 1, MAX_MAX_LENGTH);
    final int limit = arguments.optionalInt("limit", DEFAULT_PATH_LIMIT, 1, MAX_PATH_LIMIT);
    final JoinPaths found = catalog.joinPaths(from, to, maxLength, limit);
    return ToolResults.json(
        new JoinPathOutput(
            from.key().qualifiedName(),
            to.key().qualifiedName(),
            found.paths().stream().map(JoinPathOutput.Path::of).toList(),
            found.hasMore() ? Boolean.TRUE : null,
            found.paths().isEmpty()
                ? maxLength + "段以内でつながる経路はありません。maxLengthを増やすか、get_related_tablesで関連を確認してください。"
                : null));
  }

  /** テーブル名を受け取る引数（スキーマ名は{@code スキーマ名.テーブル名}の形で指定する）から、テーブルを1つに解決する */
  private TableEntry resolveTable(ToolArguments arguments, String argumentName) {
    return ObjectResolver.resolve(
        arguments, argumentName, "テーブル", TableTools.SEARCH_TABLES, catalog::lookupTable);
  }

  /**
   * {@code find_join_path}の結果
   *
   * @param hasMore 件数の上限で切り捨てた同じ長さの経路が他にもある場合はtrue。無い場合はnull
   * @param message 経路が見つからない場合に、次に何をすればよいかを示す。見つかった場合はnull
   */
  record JoinPathOutput(String from, String to, List<Path> paths, Boolean hasMore, String message) {

    /**
     * 1つの経路
     *
     * @param tables 始点から終点まで、たどる順のテーブル（{@code スキーマ名.テーブル名}）
     * @param joins 隣り合うテーブルをつなぐ関連（{@code tables}の順）。from・toは関連の参照元・参照先で、たどる向きとは限らない
     */
    record Path(List<String> tables, List<RelationOutput> joins) {

      static Path of(JoinPath path) {
        return new Path(
            path.tables().stream().map(ObjectKey::qualifiedName).toList(),
            path.relations().stream().map(relation -> RelationOutput.of(relation, null)).toList());
      }
    }
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
