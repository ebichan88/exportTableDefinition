package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.ToolSpecifications.DATABASE_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.booleanProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.enumArrayProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.enumProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.integerProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.namedObjectProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringArrayProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringProperty;

import com.export_table_definition.mcp.catalog.ColumnEntry;
import com.export_table_definition.mcp.catalog.ColumnHit;
import com.export_table_definition.mcp.catalog.ColumnQuery;
import com.export_table_definition.mcp.catalog.MatchMode;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.SearchQuery;
import com.export_table_definition.mcp.catalog.SearchResult;
import com.export_table_definition.mcp.catalog.SearchScope;
import com.export_table_definition.mcp.catalog.TableEntry;
import com.export_table_definition.mcp.catalog.TableHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * テーブルを探す・定義を返すツール（{@code search_tables}・{@code list_tables}・{@code get_table}・{@code
 * find_columns}）
 */
final class TableTools {

  static final String SEARCH_TABLES = "search_tables";
  static final String LIST_TABLES = "list_tables";
  static final String GET_TABLE = "get_table";
  static final String FIND_COLUMNS = "find_columns";

  /** {@code get_table}等でテーブルを指定する引数の説明 */
  static final String TABLE_DESCRIPTION = "テーブル名（大文字小文字を区別しない）。スキーマ名.テーブル名の形でもよい";

  private static final int DEFAULT_SEARCH_LIMIT = 20;
  private static final int MAX_SEARCH_LIMIT = 100;
  private static final int DEFAULT_LIST_LIMIT = 100;
  private static final int MAX_LIST_LIMIT = 500;
  private static final int DEFAULT_COLUMN_LIMIT = 50;
  private static final int MAX_COLUMN_LIMIT = 500;

  private static final List<String> TABLE_TYPES = List.of("table", "view", "materialized_view");

  private final SchemaCatalog catalog;

  TableTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧（{@code search_tables}・{@code list_tables}・{@code get_table}・{@code find_columns}の順） */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            SEARCH_TABLES,
            "テーブルをキーワードで検索する。テーブル名・論理名・説明・カラム名・カラムの論理名を部分一致で探し、"
                + "一致の強い順に返す（空白区切りの複数語はすべてを含むものだけ）。テーブル名が分からないときに最初に使う",
            objectSchema(
                Map.of(
                    "query", stringProperty("検索語（例: 従業員、user_id、受注 明細）"),
                    "schema", stringProperty("スキーマ名で絞り込む場合に指定する"),
                    "database", DATABASE_PROPERTY,
                    "limit",
                        integerProperty(
                            "返す件数の上限（既定" + DEFAULT_SEARCH_LIMIT + "）", 1, MAX_SEARCH_LIMIT)),
                List.of("query")),
            this::searchTables),
        readOnlyTool(
            LIST_TABLES,
            "テーブル（ビューを含む）の名前・論理名・区分を、DB名・スキーマ名・テーブル名の順に一覧で返す。"
                + "スキーマにどんなテーブルがあるか眺めるときに使う。キーワードで探す場合はsearch_tablesを使う",
            objectSchema(
                withPage(
                    Map.of(
                        "schema", stringProperty("スキーマ名で絞り込む場合に指定する"),
                        "database", DATABASE_PROPERTY,
                        "type", enumProperty("区分で絞り込む場合に指定する", TABLE_TYPES),
                        "includeDescription", booleanProperty("テーブルの説明も返す（既定false）")),
                    DEFAULT_LIST_LIMIT,
                    MAX_LIST_LIMIT),
                List.of()),
            this::listTables),
        readOnlyTool(
            GET_TABLE,
            "テーブル（ビューを含む）の定義を返す。カラム（型・PK・NOT NULL・デフォルト値・論理名・備考）、"
                + "インデックス、制約、外部キー、論理リレーション、トリガー、説明・備考を含む。"
                + "必要な項目だけをsections・columnsで指定すると結果が小さくなる",
            objectSchema(
                namedObjectProperties(
                    "table",
                    TABLE_DESCRIPTION,
                    Map.of(
                        "sections",
                        enumArrayProperty(
                            "返す項目（未指定の場合はすべて）。テーブル名・論理名・区分・説明・備考は常に返す", TableSection.fieldNames()),
                        "columns",
                        stringArrayProperty("返すカラムの名前（大文字小文字を区別しない）。指定するとcolumnsの項目はそのカラムだけになる"))),
                List.of("table")),
            this::getTable),
        readOnlyTool(
            FIND_COLUMNS,
            "カラム名（物理名・論理名）から、そのカラムを持つテーブルを逆引きする。"
                + "型・PK・NOT NULLと、外部キー・論理リレーションの参照先も返す。"
                + "同じ意味のカラムがどのテーブルにあるか、型が揃っているかを調べるときに使う",
            objectSchema(
                withPage(
                    Map.of(
                        "column", stringProperty("カラムの物理名または論理名（例: employee_id、従業員ID）"),
                        "match",
                            enumProperty(
                                "exact: 完全一致（既定）、partial: 部分一致",
                                ToolArguments.lowerNames(MatchMode.class)),
                        "schema", stringProperty("スキーマ名で絞り込む場合に指定する"),
                        "database", DATABASE_PROPERTY),
                    DEFAULT_COLUMN_LIMIT,
                    MAX_COLUMN_LIMIT),
                List.of("column")),
            this::findColumns));
  }

  private CallToolResult searchTables(ToolArguments arguments) {
    final SearchQuery query = SearchQuery.of(arguments.requiredString("query"));
    final int limit = arguments.optionalInt("limit", DEFAULT_SEARCH_LIMIT, 1, MAX_SEARCH_LIMIT);
    final SearchResult result = catalog.searchTables(query, scope(arguments), limit);
    return ToolResults.json(
        new SearchTablesOutput(
            result.total(), result.hits().stream().map(SearchTablesOutput.Hit::of).toList()));
  }

  private CallToolResult listTables(ToolArguments arguments) {
    final String type = arguments.optionalChoice("type", TABLE_TYPES);
    final boolean includeDescription = arguments.optionalBoolean("includeDescription", false);
    final Page page = Page.read(arguments, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
    final List<TableEntry> tables = catalog.listTables(scope(arguments), type);
    return ToolResults.json(
        new ListTablesOutput(
            tables.size(),
            page.nextOffset(tables.size()),
            page.apply(tables).stream()
                .map(table -> ListTablesOutput.Table.of(table, includeDescription))
                .toList()));
  }

  private CallToolResult getTable(ToolArguments arguments) {
    final TableEntry table = resolve(arguments);
    final Set<TableSection> sections = sections(arguments);
    final List<String> columns = arguments.optionalStringList("columns");
    final ObjectNode output = ToolResults.readObject(table.json());
    if (!sections.isEmpty()) {
      if (!columns.isEmpty()) {
        sections.add(TableSection.COLUMNS);
      }
      for (final TableSection section : TableSection.values()) {
        if (!sections.contains(section)) {
          output.remove(section.fieldName());
        }
      }
    }
    if (!columns.isEmpty()) {
      output.set("columns", selectColumns(table, output.path("columns"), columns));
    }
    return CallToolResult.builder().addTextContent(output.toString()).build();
  }

  private CallToolResult findColumns(ToolArguments arguments) {
    final ColumnQuery query =
        ColumnQuery.of(
            arguments.requiredString("column"),
            arguments.optionalEnum("match", MatchMode.class, MatchMode.EXACT));
    final Page page = Page.read(arguments, DEFAULT_COLUMN_LIMIT, MAX_COLUMN_LIMIT);
    final List<ColumnHit> hits = catalog.findColumns(query, scope(arguments));
    return ToolResults.json(
        new FindColumnsOutput(
            hits.size(),
            page.nextOffset(hits.size()),
            page.apply(hits).stream().map(FindColumnsOutput.Column::of).toList()));
  }

  private TableEntry resolve(ToolArguments arguments) {
    return ObjectResolver.resolve(arguments, "table", "テーブル", SEARCH_TABLES, catalog::lookupTable);
  }

  private static SearchScope scope(ToolArguments arguments) {
    return new SearchScope(
        arguments.optionalString("database"), arguments.optionalString("schema"));
  }

  private static Set<TableSection> sections(ToolArguments arguments) {
    final Set<TableSection> sections = EnumSet.noneOf(TableSection.class);
    for (final String name : arguments.optionalStringList("sections")) {
      sections.add(TableSection.of("sections", name));
    }
    return sections;
  }

  /**
   * カラムの項目を、指定された名前のカラムだけ（テーブル定義の並び順）に絞る
   *
   * @throws InvalidToolArgumentException テーブルに無いカラム名が含まれる場合
   */
  private static ArrayNode selectColumns(
      TableEntry table, JsonNode allColumns, List<String> names) {
    final Set<String> wanted =
        names.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    final ArrayNode selected = JsonNodeFactory.instance.arrayNode();
    final Set<String> found = new HashSet<>();
    for (final Iterator<JsonNode> it = allColumns.elements(); it.hasNext(); ) {
      final JsonNode column = it.next();
      final String name = column.path("name").asText().toLowerCase(Locale.ROOT);
      if (wanted.contains(name)) {
        selected.add(column);
        found.add(name);
      }
    }
    final List<String> missing =
        names.stream().filter(name -> !found.contains(name.toLowerCase(Locale.ROOT))).toList();
    if (!missing.isEmpty()) {
      throw new InvalidToolArgumentException(
          "テーブル"
              + table.key().qualifiedName()
              + "にカラム"
              + String.join(", ", missing)
              + "がありません。カラム: "
              + table.columns().stream().map(ColumnEntry::name).collect(Collectors.joining(", ")));
    }
    return selected;
  }

  private static Map<String, Object> withPage(
      Map<String, Object> properties, int defaultLimit, int maxLimit) {
    final Map<String, Object> merged = new LinkedHashMap<>(properties);
    merged.putAll(Page.properties(defaultLimit, maxLimit));
    return merged;
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
   * {@code list_tables}の結果
   *
   * @param nextOffset 続きを取得するときに指定する{@code offset}。続きが無い場合はnull
   */
  record ListTablesOutput(int total, Integer nextOffset, List<Table> tables) {

    /** 1テーブルの概要 */
    record Table(
        String database,
        String schema,
        String name,
        String logicalName,
        String type,
        String description) {

      static Table of(TableEntry table, boolean includeDescription) {
        return new Table(
            table.key().database(),
            table.key().schema(),
            table.key().name(),
            table.logicalName(),
            table.type(),
            includeDescription ? table.description() : null);
      }
    }
  }

  /**
   * {@code find_columns}の結果
   *
   * @param nextOffset 続きを取得するときに指定する{@code offset}。続きが無い場合はnull
   */
  record FindColumnsOutput(int total, Integer nextOffset, List<Column> columns) {

    /** 当てはまった1カラム */
    record Column(
        String database,
        String schema,
        String table,
        String tableLogicalName,
        String column,
        String logicalName,
        String type,
        boolean primaryKey,
        boolean notNull,
        String defaultValue,
        List<Reference> references) {

      static Column of(ColumnHit hit) {
        final TableEntry table = hit.table();
        return new Column(
            table.key().database(),
            table.key().schema(),
            table.key().name(),
            table.logicalName(),
            hit.column().name(),
            hit.column().logicalName(),
            hit.column().type(),
            hit.column().primaryKey(),
            hit.column().notNull(),
            hit.column().defaultValue(),
            hit.references().stream().map(Reference::of).toList());
      }
    }

    /**
     * カラムが参照している先
     *
     * @param table 参照先のテーブル（{@code スキーマ名.テーブル名}）
     * @param kind {@code foreignKey}または{@code logicalRelation}
     */
    record Reference(String table, String column, String kind) {

      static Reference of(ColumnHit.ColumnReference reference) {
        return new Reference(
            reference.table().qualifiedName(),
            reference.column(),
            RelationTools.kindName(reference.kind()));
      }
    }
  }
}
