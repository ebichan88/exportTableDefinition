package com.export_table_definition.mcp.tool;

import com.export_table_definition.mcp.catalog.Direction;
import com.export_table_definition.mcp.catalog.RelatedTables;
import com.export_table_definition.mcp.catalog.Relation;
import com.export_table_definition.mcp.catalog.RelationKind;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.SearchQuery;
import com.export_table_definition.mcp.catalog.SearchResult;
import com.export_table_definition.mcp.catalog.SearchScope;
import com.export_table_definition.mcp.catalog.TableEntry;
import com.export_table_definition.mcp.catalog.TableHit;
import com.export_table_definition.mcp.catalog.TableKey;
import com.export_table_definition.mcp.catalog.TableLookup;
import com.export_table_definition.mcp.catalog.TableReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * テーブル定義を調べるMCPのツール（{@code search_tables}・{@code get_table}・{@code get_related_tables}）<br>
 * 結果はJSONの文字列で返す。値が無い項目（空文字・空リスト）は出力しない
 */
public final class TableDefinitionTools {

  static final String SEARCH_TABLES = "search_tables";
  static final String GET_TABLE = "get_table";
  static final String GET_RELATED_TABLES = "get_related_tables";

  private static final int DEFAULT_SEARCH_LIMIT = 20;
  private static final int MAX_SEARCH_LIMIT = 100;

  /** 段数を増やすと結果が急に膨らみAIのコンテキストを圧迫するため、3段までに抑える */
  private static final int MAX_DEPTH = 3;

  private static final Map<String, Object> DATABASE_PROPERTY =
      stringProperty("DB名。スナップショットに複数のDBがあり、同名のテーブルを区別したい場合だけ指定する");

  private final SchemaCatalog catalog;
  private final ObjectMapper objectMapper =
      new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);

  /**
   * @param catalog 検索の対象にする全テーブル
   */
  public TableDefinitionTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /**
   * MCPサーバーへ登録するツールの一覧を返すメソッド
   *
   * @return {@code search_tables}・{@code get_table}・{@code get_related_tables}の順のツール
   */
  public List<SyncToolSpecification> specifications() {
    return List.of(
        specification(
            SEARCH_TABLES,
            "テーブルをキーワードで検索する。テーブル名・論理名・説明・カラム名・カラムの論理名を部分一致で探し、"
                + "一致の強い順に返す（空白区切りの複数語はすべてを含むものだけ）。テーブル名が分からないときに最初に使う",
            schema(
                Map.of(
                    "query", stringProperty("検索語（例: 従業員、user_id、受注 明細）"),
                    "schema", stringProperty("スキーマ名で絞り込む場合に指定する"),
                    "database", DATABASE_PROPERTY,
                    "limit",
                        Map.of(
                            "type",
                            "integer",
                            "minimum",
                            1,
                            "maximum",
                            MAX_SEARCH_LIMIT,
                            "description",
                            "返す件数の上限（既定" + DEFAULT_SEARCH_LIMIT + "）")),
                List.of("query")),
            this::searchTables),
        specification(
            GET_TABLE,
            "テーブル（ビューを含む）の定義をすべて返す。カラム（型・PK・NOT NULL・デフォルト値・論理名・備考）、"
                + "インデックス、制約、外部キー、論理リレーション、トリガー、説明・備考を含む",
            schema(tableProperties(Map.of()), List.of("table")),
            this::getTable),
        specification(
            GET_RELATED_TABLES,
            "テーブルとつながるテーブルを、外部キーと論理リレーション（DBに制約が無い関連）の両方からたどって返す。"
                + "JOINの条件（どのカラム同士でつながるか）を調べるときに使う。"
                + "cardinalityは参照先（to）1行に対する参照元（from）の行数を表す（例: ONE_TO_MANY）",
            schema(
                tableProperties(
                    Map.of(
                        "depth",
                        Map.of(
                            "type",
                            "integer",
                            "minimum",
                            1,
                            "maximum",
                            MAX_DEPTH,
                            "description",
                            "たどる段数（既定1）"),
                        "direction",
                        Map.of(
                            "type",
                            "string",
                            "enum",
                            ToolArguments.lowerNames(Direction.class),
                            "description",
                            "outgoing: 参照先へ、incoming: 参照元へ、both: 両方（既定both）"))),
                List.of("table")),
            this::getRelatedTables));
  }

  /** {@code search_tables}の処理 */
  CallToolResult searchTables(ToolArguments arguments) {
    final SearchQuery query = SearchQuery.of(arguments.requiredString("query"));
    final SearchScope scope =
        new SearchScope(arguments.optionalString("database"), arguments.optionalString("schema"));
    final int limit = arguments.optionalInt("limit", DEFAULT_SEARCH_LIMIT, 1, MAX_SEARCH_LIMIT);
    final SearchResult result = catalog.searchTables(query, scope, limit);
    return success(
        new SearchTablesOutput(
            result.total(), result.hits().stream().map(SearchTablesOutput.Hit::of).toList()));
  }

  /** {@code get_table}の処理 */
  CallToolResult getTable(ToolArguments arguments) {
    return CallToolResult.builder().addTextContent(resolve(arguments).json()).build();
  }

  /** {@code get_related_tables}の処理 */
  CallToolResult getRelatedTables(ToolArguments arguments) {
    final TableEntry table = resolve(arguments);
    final int depth = arguments.optionalInt("depth", 1, 1, MAX_DEPTH);
    final Direction direction =
        arguments.optionalEnum("direction", Direction.class, Direction.BOTH);
    return success(RelatedTablesOutput.of(catalog.relatedTables(table, depth, direction)));
  }

  /**
   * 引数のテーブルの指定を1つのテーブルへ解決する
   *
   * @throws InvalidToolArgumentException 見つからない場合、複数に当てはまる場合（候補をメッセージに含める）
   */
  private TableEntry resolve(ToolArguments arguments) {
    final TableReference reference =
        TableReference.of(
            arguments.optionalString("database"),
            arguments.optionalString("schema"),
            arguments.requiredString("table"));
    return switch (catalog.lookup(reference)) {
      case TableLookup.Found found -> found.table();
      case TableLookup.Ambiguous ambiguous ->
          throw new InvalidToolArgumentException(
              "テーブル"
                  + reference.table()
                  + "が複数あります。schema（DBが異なる場合はdatabase）を指定してください。候補: "
                  + describe(ambiguous.candidates()));
      case TableLookup.NotFound notFound ->
          throw new InvalidToolArgumentException(
              "テーブル"
                  + reference.table()
                  + "が見つかりません。"
                  + (notFound.suggestions().isEmpty()
                      ? SEARCH_TABLES + "で探してください。"
                      : "名前の似たテーブル: " + describe(notFound.suggestions())));
    };
  }

  private static String describe(List<TableEntry> tables) {
    return tables.stream()
        .map(table -> table.key().database() + ":" + table.key().qualifiedName())
        .collect(Collectors.joining(", "));
  }

  private SyncToolSpecification specification(
      String name,
      String description,
      Map<String, Object> inputSchema,
      Function<ToolArguments, CallToolResult> handler) {
    @SuppressWarnings("unchecked")
    final Set<String> argumentNames =
        ((Map<String, Object>) inputSchema.get("properties")).keySet();
    return SyncToolSpecification.builder()
        .tool(
            Tool.builder(name, inputSchema)
                .description(description)
                .annotations(
                    ToolAnnotations.builder()
                        .readOnlyHint(true)
                        .destructiveHint(false)
                        .idempotentHint(true)
                        .openWorldHint(false)
                        .build())
                .build())
        .callHandler(
            (exchange, request) -> {
              try {
                return handler.apply(new ToolArguments(request.arguments(), argumentNames));
              } catch (InvalidToolArgumentException e) {
                return CallToolResult.builder()
                    .addTextContent(e.getMessage())
                    .isError(true)
                    .build();
              }
            })
        .build();
  }

  private CallToolResult success(Object output) {
    try {
      return CallToolResult.builder()
          .addTextContent(objectMapper.writeValueAsString(output))
          .build();
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, Object> tableProperties(Map<String, Object> extra) {
    final Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("table", stringProperty("テーブル名（大文字小文字を区別しない）。スキーマ名.テーブル名の形でもよい"));
    properties.put("schema", stringProperty("スキーマ名。同名のテーブルが複数のスキーマにある場合に指定する"));
    properties.put("database", DATABASE_PROPERTY);
    properties.putAll(extra);
    return properties;
  }

  private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
    return Map.of(
        "type",
        "object",
        "properties",
        properties,
        "required",
        required,
        "additionalProperties",
        false);
  }

  private static Map<String, Object> stringProperty(String description) {
    return Map.of("type", "string", "description", description);
  }

  /** {@code search_tables}の結果 */
  record SearchTablesOutput(int total, List<Hit> tables) {

    /** 一致した1テーブル（定義の全体は{@code get_table}で取得する前提で、概要だけを返す） */
    record Hit(
        String database,
        String schema,
        String name,
        String logicalName,
        String type,
        String description,
        List<String> matchedIn) {

      static Hit of(TableHit hit) {
        final TableEntry table = hit.table();
        return new Hit(
            table.key().database(),
            table.key().schema(),
            table.key().name(),
            table.logicalName(),
            table.type(),
            table.description(),
            hit.matchedIn());
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
          related.relations().stream().map(RelationOutput::of).toList(),
          related.tables().stream().map(TableSummary::of).toList(),
          related.missingTables().stream().map(TableKey::qualifiedName).toList());
    }
  }

  /**
   * 1つの関連
   *
   * @param kind {@code foreignKey}（外部キー）または{@code logicalRelation}（論理リレーション）。スナップショットの項目名に揃える
   */
  record RelationOutput(
      String from,
      List<String> fromColumns,
      String to,
      List<String> toColumns,
      String kind,
      String cardinality,
      String name,
      int depth) {

    static RelationOutput of(RelatedTables.RelationAtDepth found) {
      final Relation relation = found.relation();
      return new RelationOutput(
          relation.from().qualifiedName(),
          relation.fromColumns(),
          relation.to().qualifiedName(),
          relation.toColumns(),
          relation.kind() == RelationKind.FOREIGN_KEY ? "foreignKey" : "logicalRelation",
          relation.cardinality(),
          relation.name(),
          found.depth());
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
