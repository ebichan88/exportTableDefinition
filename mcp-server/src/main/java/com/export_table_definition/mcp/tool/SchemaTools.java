package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;

import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.SchemaSummary;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** スナップショットの全体像を返すツール（{@code list_schemas}） */
final class SchemaTools {

  static final String LIST_SCHEMAS = "list_schemas";

  private final SchemaCatalog catalog;

  SchemaTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            LIST_SCHEMAS,
            "スナップショットに含まれるDB（DBMS種別）とスキーマの一覧を、スキーマごとのオブジェクトの数とともに返す。"
                + "どのDB・スキーマがあるか、全体像をつかむときに最初に使う",
            objectSchema(Map.of(), List.of()),
            this::listSchemas));
  }

  private CallToolResult listSchemas(ToolArguments arguments) {
    final Map<String, List<SchemaSummary>> byDatabase =
        catalog.schemas().stream()
            .collect(
                Collectors.groupingBy(
                    SchemaSummary::database, LinkedHashMap::new, Collectors.toList()));
    return ToolResults.json(
        new ListSchemasOutput(
            byDatabase.values().stream().map(ListSchemasOutput.Database::of).toList()));
  }

  /** {@code list_schemas}の結果 */
  record ListSchemasOutput(List<Database> databases) {

    /** 1DB */
    record Database(String name, String dbms, List<Schema> schemas) {

      static Database of(List<SchemaSummary> schemas) {
        return new Database(
            schemas.get(0).database(),
            schemas.get(0).dbms(),
            schemas.stream().map(Schema::of).toList());
      }
    }

    /**
     * 1スキーマと、含まれるオブジェクトの数
     *
     * @param functions 関数・プロシージャの数（オーバーロードはそれぞれ数える）
     */
    record Schema(
        String name,
        int tables,
        int views,
        int materializedViews,
        int functions,
        int sequences,
        int types) {

      static Schema of(SchemaSummary summary) {
        return new Schema(
            summary.schema(),
            summary.tables(),
            summary.views(),
            summary.materializedViews(),
            summary.functions(),
            summary.sequences(),
            summary.types());
      }
    }
  }
}
