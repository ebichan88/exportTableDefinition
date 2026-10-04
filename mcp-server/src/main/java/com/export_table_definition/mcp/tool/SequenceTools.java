package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.Page.withPageProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.DATABASE_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.SCHEMA_FILTER_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.namedObjectProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringProperty;

import com.export_table_definition.mcp.catalog.NameFilter;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.SequenceEntry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;

/** シーケンスを調べるツール（{@code list_sequences}・{@code get_sequence}） */
final class SequenceTools {

  static final String LIST_SEQUENCES = "list_sequences";
  static final String GET_SEQUENCE = "get_sequence";

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  private final SchemaCatalog catalog;

  SequenceTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            LIST_SEQUENCES,
            "シーケンスの名前と所有カラム（テーブル名.カラム名）を、DB名・スキーマ名・名前の順に一覧で返す。"
                + "PostgreSQLのみ（Oracleのスナップショットでは0件）",
            objectSchema(
                withPageProperties(
                    Map.of(
                        "query", stringProperty("名前の一部で絞り込む場合に指定する（大文字小文字を区別しない）"),
                        "schema", SCHEMA_FILTER_PROPERTY,
                        "database", DATABASE_PROPERTY),
                    DEFAULT_LIMIT,
                    MAX_LIMIT),
                List.of()),
            this::listSequences),
        readOnlyTool(
            GET_SEQUENCE,
            "シーケンスの定義（増分・最小値・最大値・キャッシュ・開始値・循環の有無・所有カラム）と、"
                + "デフォルト値（nextval）で採番に使うカラム（usedByColumns）を返す",
            objectSchema(
                namedObjectProperties(
                    "sequence", "シーケンス名（大文字小文字を区別しない）。スキーマ名.シーケンス名の形でもよい", Map.of()),
                List.of("sequence")),
            this::getSequence));
  }

  private CallToolResult listSequences(ToolArguments arguments) {
    final NameFilter filter = NameFilter.of(arguments.optionalString("query"));
    final Page page = Page.read(arguments, DEFAULT_LIMIT, MAX_LIMIT);
    final List<SequenceEntry> sequences = catalog.listSequences(arguments.scope(), filter);
    return ToolResults.json(
        new ListSequencesOutput(
            sequences.size(),
            page.nextOffset(sequences.size()),
            page.apply(sequences).stream().map(ListSequencesOutput.Sequence::of).toList()));
  }

  private CallToolResult getSequence(ToolArguments arguments) {
    final SequenceEntry sequence =
        ObjectResolver.resolve(
            arguments, "sequence", "シーケンス", LIST_SEQUENCES, catalog::lookupSequence);
    return ToolResults.withUsedByColumns(sequence.json(), catalog.columnsUsingSequence(sequence));
  }

  /**
   * {@code list_sequences}の結果
   *
   * @param nextOffset 続きを取得するときに指定する{@code offset}。続きが無い場合はnull
   */
  record ListSequencesOutput(int total, Integer nextOffset, List<Sequence> sequences) {

    /**
     * 1シーケンスの概要
     *
     * @param ownedBy 所有カラム（{@code テーブル名.カラム名}）
     */
    record Sequence(String database, String schema, String name, String ownedBy) {

      static Sequence of(SequenceEntry sequence) {
        return new Sequence(
            sequence.key().database(),
            sequence.key().schema(),
            sequence.key().name(),
            sequence.ownedBy());
      }
    }
  }
}
