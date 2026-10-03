package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.Page.withPageProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.DATABASE_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.SCHEMA_FILTER_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;

import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.TableTrigger;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;

/** テーブルをまたいでトリガーを調べるツール（{@code list_triggers}） */
final class TriggerTools {

  static final String LIST_TRIGGERS = "list_triggers";

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  private final SchemaCatalog catalog;

  TriggerTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            LIST_TRIGGERS,
            "トリガーを、テーブル・タイミング・イベント・実行単位・実行される関数とともに、テーブルをまたいで一覧で返す。"
                + "1テーブルのトリガーの定義はget_table（sections: triggers）で取得できる。"
                + "PostgreSQLのみ（Oracleのスナップショットでは0件）",
            objectSchema(
                withPageProperties(
                    Map.of("schema", SCHEMA_FILTER_PROPERTY, "database", DATABASE_PROPERTY),
                    DEFAULT_LIMIT,
                    MAX_LIMIT),
                List.of()),
            this::listTriggers));
  }

  private CallToolResult listTriggers(ToolArguments arguments) {
    final Page page = Page.read(arguments, DEFAULT_LIMIT, MAX_LIMIT);
    final List<TableTrigger> triggers = catalog.listTriggers(arguments.scope());
    return ToolResults.json(
        new ListTriggersOutput(
            triggers.size(),
            page.nextOffset(triggers.size()),
            page.apply(triggers).stream().map(ListTriggersOutput.Trigger::of).toList()));
  }

  /**
   * {@code list_triggers}の結果
   *
   * @param nextOffset 続きを取得するときに指定する{@code offset}。続きが無い場合はnull
   */
  record ListTriggersOutput(int total, Integer nextOffset, List<Trigger> triggers) {

    /**
     * 1トリガー
     *
     * @param table トリガーを持つテーブル名（スキーマ修飾しない）
     * @param function 実行される関数名（{@code スキーマ名.関数名}）
     */
    record Trigger(
        String database,
        String schema,
        String table,
        String name,
        String timing,
        List<String> events,
        String orientation,
        String function) {

      static Trigger of(TableTrigger found) {
        return new Trigger(
            found.table().key().database(),
            found.table().key().schema(),
            found.table().key().name(),
            found.trigger().name(),
            found.trigger().timing(),
            found.trigger().events(),
            found.trigger().orientation(),
            found.trigger().function());
      }
    }
  }
}
