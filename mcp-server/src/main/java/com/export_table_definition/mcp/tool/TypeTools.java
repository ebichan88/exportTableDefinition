package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.Page.withPageProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.DATABASE_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.SCHEMA_FILTER_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.enumProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.namedObjectProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringProperty;

import com.export_table_definition.mcp.catalog.NameFilter;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.TypeEntry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;

/** ユーザー定義型（ENUM等）を調べるツール（{@code list_types}・{@code get_type}） */
final class TypeTools {

  static final String LIST_TYPES = "list_types";
  static final String GET_TYPE = "get_type";

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  private static final List<String> CATEGORIES = List.of("ENUM", "COMPOSITE", "DOMAIN", "RANGE");

  private final SchemaCatalog catalog;

  TypeTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            LIST_TYPES,
            "ユーザー定義型（ENUM・複合型・ドメイン・範囲型）の名前と種別を、DB名・スキーマ名・名前の順に一覧で返す。"
                + "PostgreSQLのみ（Oracleのスナップショットでは0件）",
            objectSchema(
                withPageProperties(
                    Map.of(
                        "query",
                        stringProperty("名前の一部で絞り込む場合に指定する（大文字小文字を区別しない）"),
                        "category",
                        enumProperty("種別で絞り込む場合に指定する", CATEGORIES),
                        "schema",
                        SCHEMA_FILTER_PROPERTY,
                        "database",
                        DATABASE_PROPERTY),
                    DEFAULT_LIMIT,
                    MAX_LIMIT),
                List.of()),
            this::listTypes),
        readOnlyTool(
            GET_TYPE,
            "ユーザー定義型の定義を返す。ENUMは値の一覧、複合型は属性と型、ドメインは元の型と制約を含む。" + "アプリ側で列挙型・定数を書くときに使う",
            objectSchema(
                namedObjectProperties("type", "型名（大文字小文字を区別しない）。スキーマ名.型名の形でもよい", Map.of()),
                List.of("type")),
            this::getType));
  }

  private CallToolResult listTypes(ToolArguments arguments) {
    final NameFilter filter = NameFilter.of(arguments.optionalString("query"));
    final String category = arguments.optionalChoice("category", CATEGORIES);
    final Page page = Page.read(arguments, DEFAULT_LIMIT, MAX_LIMIT);
    final List<TypeEntry> types = catalog.listTypes(arguments.scope(), filter, category);
    return ToolResults.json(
        new ListTypesOutput(
            types.size(),
            page.nextOffset(types.size()),
            page.apply(types).stream().map(ListTypesOutput.Type::of).toList()));
  }

  private CallToolResult getType(ToolArguments arguments) {
    final TypeEntry type =
        ObjectResolver.resolve(arguments, "type", "型", LIST_TYPES, catalog::lookupType);
    return ToolResults.withUsedByColumns(type.json(), catalog.columnsUsingType(type));
  }

  /**
   * {@code list_types}の結果
   *
   * @param nextOffset 続きを取得するときに指定する{@code offset}。続きが無い場合はnull
   */
  record ListTypesOutput(int total, Integer nextOffset, List<Type> types) {

    /** 1ユーザー定義型の概要 */
    record Type(String database, String schema, String name, String category) {

      static Type of(TypeEntry type) {
        return new Type(
            type.key().database(), type.key().schema(), type.key().name(), type.category());
      }
    }
  }
}
