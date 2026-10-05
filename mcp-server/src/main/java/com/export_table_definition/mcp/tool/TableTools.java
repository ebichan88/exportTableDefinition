package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.Page.withPageProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.DATABASE_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.SCHEMA_FILTER_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.booleanProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.enumArrayProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.enumProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.integerProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.namedObjectProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringArrayProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringOrArrayProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringProperty;

import com.export_table_definition.mcp.catalog.ColumnEntry;
import com.export_table_definition.mcp.catalog.ColumnHit;
import com.export_table_definition.mcp.catalog.ColumnQuery;
import com.export_table_definition.mcp.catalog.MatchMode;
import com.export_table_definition.mcp.catalog.ObjectReference;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.SearchQuery;
import com.export_table_definition.mcp.catalog.SearchResult;
import com.export_table_definition.mcp.catalog.TableEntry;
import com.export_table_definition.mcp.catalog.TableHit;
import com.export_table_definition.mcp.catalog.ViewpointEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

  /** {@code list_tables}・{@code search_tables}で観点に絞り込む引数のプロパティ */
  private static final Map<String, Object> VIEWPOINT_PROPERTY =
      stringProperty("指定した観点（list_viewpointsのid）の所属テーブルだけに絞り込む場合に指定する");

  private static final int DEFAULT_SEARCH_LIMIT = 20;
  private static final int MAX_SEARCH_LIMIT = 100;
  private static final int DEFAULT_LIST_LIMIT = 100;
  private static final int MAX_LIST_LIMIT = 500;
  private static final int DEFAULT_COLUMN_LIMIT = 50;
  private static final int MAX_COLUMN_LIMIT = 500;

  /** {@code get_table}の{@code table}に配列で指定できる件数の上限 */
  private static final int MAX_TABLES = 10;

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
            "テーブル検索",
            "テーブルをキーワードで検索する。テーブル名・論理名・説明・カラム名・カラムの論理名を部分一致で探し、"
                + "一致の強い順に返す（空白区切りの複数語はすべてを含むものだけ）。テーブル名が分からないときに最初に使う",
            objectSchema(
                Map.of(
                    "query",
                    stringProperty("検索語（例: 従業員、user_id、受注 明細）"),
                    "schema",
                    SCHEMA_FILTER_PROPERTY,
                    "database",
                    DATABASE_PROPERTY,
                    "limit",
                    integerProperty("返す件数の上限（既定" + DEFAULT_SEARCH_LIMIT + "）", 1, MAX_SEARCH_LIMIT),
                    "viewpoint",
                    VIEWPOINT_PROPERTY),
                List.of("query")),
            this::searchTables),
        readOnlyTool(
            LIST_TABLES,
            "テーブル一覧",
            "テーブル（ビューを含む）の名前・論理名・区分を、DB名・スキーマ名・テーブル名の順に一覧で返す。"
                + "スキーマにどんなテーブルがあるか眺めるときに使う。キーワードで探す場合はsearch_tablesを使う",
            objectSchema(
                withPageProperties(
                    Map.of(
                        "schema",
                        SCHEMA_FILTER_PROPERTY,
                        "database",
                        DATABASE_PROPERTY,
                        "type",
                        enumProperty("区分で絞り込む場合に指定する", TABLE_TYPES),
                        "includeDescription",
                        booleanProperty("テーブルの説明も返す（既定false）"),
                        "viewpoint",
                        VIEWPOINT_PROPERTY),
                    DEFAULT_LIST_LIMIT,
                    MAX_LIST_LIMIT),
                List.of()),
            this::listTables),
        readOnlyTool(
            GET_TABLE,
            "テーブル定義取得",
            "テーブル（ビューを含む）の定義を返す。カラム（型・PK・NOT NULL・デフォルト値・論理名・備考）、"
                + "インデックス、制約、外部キー、論理リレーション、トリガー、説明・備考を含む。"
                + "テーブルが観点（list_viewpoints）に所属する場合は、所属する観点（viewpoints。id・表示名）も返す。"
                + "必要な項目だけをsections・columnsで指定すると結果が小さくなる。"
                + "複数のテーブルをまとめて取得する場合はtableに配列を指定する（最大"
                + MAX_TABLES
                + "件。columnsは1件指定したときだけ使える）",
            objectSchema(
                namedObjectProperties(
                    "table",
                    stringOrArrayProperty(TABLE_DESCRIPTION + "。配列で複数指定できる（最大" + MAX_TABLES + "件）"),
                    Map.of(
                        "sections",
                        enumArrayProperty(
                            "返す項目（未指定の場合はすべて）。テーブル名・論理名・区分・説明・備考・所属する観点は常に返す",
                            TableSection.fieldNames()),
                        "columns",
                        stringArrayProperty(
                            "返すカラムの名前（大文字小文字を区別しない）。指定するとcolumnsの項目はそのカラムだけになる。"
                                + "tableを複数指定した場合は使えない"))),
                List.of("table")),
            this::getTable),
        readOnlyTool(
            FIND_COLUMNS,
            "カラム逆引き",
            "カラム名（物理名・論理名）から、そのカラムを持つテーブルを逆引きする。"
                + "型・PK・NOT NULLと、外部キー・論理リレーションの参照先も返す。"
                + "同じ意味のカラムがどのテーブルにあるか、型が揃っているかを調べるときに使う",
            objectSchema(
                withPageProperties(
                    Map.of(
                        "column",
                        stringProperty("カラムの物理名または論理名（例: employee_id、従業員ID）"),
                        "match",
                        enumProperty(
                            "exact: 完全一致（既定）、partial: 部分一致",
                            ToolArguments.lowerNames(MatchMode.class)),
                        "schema",
                        SCHEMA_FILTER_PROPERTY,
                        "database",
                        DATABASE_PROPERTY),
                    DEFAULT_COLUMN_LIMIT,
                    MAX_COLUMN_LIMIT),
                List.of("column")),
            this::findColumns));
  }

  private CallToolResult searchTables(ToolArguments arguments) {
    final SearchQuery query = SearchQuery.of(arguments.requiredString("query"));
    final int limit = arguments.optionalInt("limit", DEFAULT_SEARCH_LIMIT);
    final SearchResult result =
        catalog.searchTables(query, arguments.scope(), limit, resolveViewpoint(arguments));
    return ToolResults.json(
        new SearchTablesOutput(
            result.total(), result.hits().stream().map(SearchTablesOutput.Hit::of).toList()));
  }

  private CallToolResult listTables(ToolArguments arguments) {
    final String type = arguments.optionalChoice("type", TABLE_TYPES);
    final boolean includeDescription = arguments.optionalBoolean("includeDescription", false);
    final Page page = Page.read(arguments, DEFAULT_LIST_LIMIT);
    final List<TableEntry> tables =
        catalog.listTables(arguments.scope(), type, resolveViewpoint(arguments));
    return ToolResults.json(
        new ListTablesOutput(
            tables.size(),
            page.nextOffset(tables.size()),
            page.apply(tables).stream()
                .map(table -> ListTablesOutput.Table.of(table, includeDescription))
                .toList()));
  }

  /**
   * 引数{@code viewpoint}（観点のid）を解決するメソッド
   *
   * @throws InvalidToolArgumentException 指定した観点が見つからない場合
   */
  private Optional<ViewpointEntry> resolveViewpoint(ToolArguments arguments) {
    final String id = arguments.optionalString("viewpoint");
    if (id.isEmpty()) {
      return Optional.empty();
    }
    final String database = arguments.optionalString("database");
    return Optional.of(
        catalog.findViewpoint(database, id).orElseThrow(() -> viewpointNotFound(id, arguments)));
  }

  private InvalidToolArgumentException viewpointNotFound(String id, ToolArguments arguments) {
    final List<String> ids =
        catalog.listViewpoints(arguments.scope()).stream().map(ViewpointEntry::id).toList();
    if (ids.isEmpty()) {
      return new InvalidToolArgumentException("観点" + id + "が見つかりません。観点は1件も宣言されていません。");
    }
    return new InvalidToolArgumentException("観点" + id + "が見つかりません。観点: " + String.join(", ", ids));
  }

  private CallToolResult getTable(ToolArguments arguments) {
    final List<String> names = arguments.requiredStringList("table", MAX_TABLES);
    final Set<TableSection> sections = sections(arguments);
    final List<String> columns = arguments.optionalStringList("columns");
    if (names.size() > 1 && !columns.isEmpty()) {
      throw new InvalidToolArgumentException("引数columnsは、tableを1件指定した場合だけ使えます。");
    }
    final List<TableEntry> tables = resolveTables(arguments, names);
    final List<ObjectNode> outputs =
        tables.stream()
            .map(table -> withViewpoints(buildTableOutput(table, sections, columns), table))
            .toList();
    if (outputs.size() == 1) {
      return CallToolResult.builder().addTextContent(outputs.get(0).toString()).build();
    }
    return ToolResults.json(new GetTableOutput(outputs));
  }

  /**
   * 指定された名前をすべて解決するメソッド
   *
   * @throws InvalidToolArgumentException 1件でも解決できない名前があれば、その名前すべての失敗（候補を含む）をまとめて返す
   */
  private List<TableEntry> resolveTables(ToolArguments arguments, List<String> names) {
    final String database = arguments.optionalString("database");
    final String schema = arguments.optionalString("schema");
    final List<TableEntry> tables = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    for (final String name : names) {
      try {
        tables.add(
            ObjectResolver.resolve(
                ObjectReference.of(database, schema, name),
                "テーブル",
                SEARCH_TABLES,
                catalog::lookupTable));
      } catch (InvalidToolArgumentException e) {
        errors.add(e.getMessage());
      }
    }
    if (!errors.isEmpty()) {
      throw new InvalidToolArgumentException(String.join("\n", errors));
    }
    return tables;
  }

  /** 1テーブルの結果に、sections・columnsによる絞り込みを反映する */
  private static ObjectNode buildTableOutput(
      TableEntry table, Set<TableSection> sections, List<String> columns) {
    final ObjectNode output = ToolResults.readObject(table.json());
    if (!sections.isEmpty()) {
      final Set<TableSection> effective = EnumSet.copyOf(sections);
      if (!columns.isEmpty()) {
        effective.add(TableSection.COLUMNS);
      }
      for (final TableSection section : TableSection.values()) {
        if (!effective.contains(section)) {
          output.remove(section.fieldName());
        }
      }
    }
    if (!columns.isEmpty()) {
      output.set("columns", selectColumns(table, output.path("columns"), columns));
    }
    return output;
  }

  /**
   * 1テーブルの結果に、所属する観点（{@code id}・{@code name}）を宣言順に加える<br>
   * 観点はスナップショットの項目ではない（参考情報）ため{@code sections}では絞り込まず、所属する観点があれば常に加える
   */
  private ObjectNode withViewpoints(ObjectNode output, TableEntry table) {
    final List<ViewpointEntry> viewpoints = catalog.viewpointsOf(table);
    if (!viewpoints.isEmpty()) {
      final ArrayNode entries = output.putArray("viewpoints");
      for (final ViewpointEntry viewpoint : viewpoints) {
        final ObjectNode entry = entries.addObject().put("id", viewpoint.id());
        if (!viewpoint.name().isEmpty()) {
          entry.put("name", viewpoint.name());
        }
      }
    }
    return output;
  }

  private CallToolResult findColumns(ToolArguments arguments) {
    final ColumnQuery query =
        ColumnQuery.of(
            arguments.requiredString("column"),
            arguments.optionalEnum("match", MatchMode.class, MatchMode.EXACT));
    final Page page = Page.read(arguments, DEFAULT_COLUMN_LIMIT);
    final List<ColumnHit> hits = catalog.findColumns(query, arguments.scope());
    return ToolResults.json(
        new FindColumnsOutput(
            hits.size(),
            page.nextOffset(hits.size()),
            page.apply(hits).stream().map(FindColumnsOutput.Column::of).toList()));
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

  /** {@code get_table}で{@code table}を複数指定した場合の結果（1件の場合はスナップショットの1行をそのまま返す） */
  record GetTableOutput(List<ObjectNode> tables) {}

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
