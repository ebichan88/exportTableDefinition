package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.Page.withPageProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.DATABASE_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.SCHEMA_FILTER_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.booleanProperty;
import static com.export_table_definition.mcp.tool.ToolSpecifications.namedObjectProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.objectSchema;
import static com.export_table_definition.mcp.tool.ToolSpecifications.readOnlyTool;
import static com.export_table_definition.mcp.tool.ToolSpecifications.stringProperty;

import com.export_table_definition.mcp.catalog.FunctionEntry;
import com.export_table_definition.mcp.catalog.FunctionOverloads;
import com.export_table_definition.mcp.catalog.NameFilter;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.TableTrigger;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 関数・プロシージャを調べるツール（{@code list_functions}・{@code get_function}）<br>
 * 定義本体はAIのコンテキストを圧迫するため、既定では返さない（{@code get_function}の{@code includeDefinition}で返す）
 */
final class FunctionTools {

  static final String LIST_FUNCTIONS = "list_functions";
  static final String GET_FUNCTION = "get_function";

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  /** 定義本体の項目名（スナップショットの項目名） */
  private static final String DEFINITION_FIELD = "definition";

  /** Oracleのパッケージ内サブプログラムは数千行になり得るため、AIのコンテキストを圧迫しないよう切り詰める文字数 */
  private static final int MAX_DEFINITION_LENGTH = 4_000;

  private final SchemaCatalog catalog;

  FunctionTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            LIST_FUNCTIONS,
            "関数一覧",
            "関数・プロシージャの名前・種別・引数・戻り値・言語を、DB名・スキーマ名・名前の順に一覧で返す（定義本体は返さない）。" + "同名の関数（オーバーロード）はそれぞれ返す",
            objectSchema(
                withPageProperties(
                    Map.of(
                        "query", stringProperty("名前の一部で絞り込む場合に指定する（大文字小文字を区別しない）"),
                        "schema", SCHEMA_FILTER_PROPERTY,
                        "database", DATABASE_PROPERTY),
                    DEFAULT_LIMIT,
                    MAX_LIMIT),
                List.of()),
            this::listFunctions),
        readOnlyTool(
            GET_FUNCTION,
            "関数定義取得",
            "関数・プロシージャのシグネチャ（種別・引数・戻り値・言語）を返す。同名の関数（オーバーロード）はまとめて返す。"
                + "定義本体は既定では返さない。includeDefinitionを指定すると返す"
                + "（オーバーロードの本体がすべて同じ場合は1つにまとめ、長い場合は切り詰める）",
            objectSchema(
                namedObjectProperties(
                    "function",
                    "関数・プロシージャ名（大文字小文字を区別しない）。スキーマ名.関数名の形でもよい",
                    Map.of("includeDefinition", booleanProperty("定義本体も返す（既定false）"))),
                List.of("function")),
            this::getFunction));
  }

  private CallToolResult listFunctions(ToolArguments arguments) {
    final NameFilter filter = NameFilter.of(arguments.optionalString("query"));
    final Page page = Page.read(arguments, DEFAULT_LIMIT);
    final List<FunctionEntry> functions = catalog.listFunctions(arguments.scope(), filter);
    return ToolResults.json(
        new ListFunctionsOutput(
            functions.size(),
            page.nextOffset(functions.size()),
            page.apply(functions).stream().map(ListFunctionsOutput.Function::of).toList()));
  }

  private CallToolResult getFunction(ToolArguments arguments) {
    final boolean includeDefinition = arguments.optionalBoolean("includeDefinition", false);
    final FunctionOverloads function =
        ObjectResolver.resolve(
            arguments, "function", "関数", LIST_FUNCTIONS, catalog::lookupFunction);
    final List<ObjectNode> overloads =
        function.overloads().stream().map(entry -> signature(entry, includeDefinition)).toList();
    return ToolResults.json(
        new GetFunctionOutput(
            function.key().database(),
            function.key().schema(),
            function.key().name(),
            includeDefinition ? extractSharedDefinition(overloads) : null,
            overloads,
            catalog.triggersCalling(function).stream().map(CallingTrigger::of).toList()));
  }

  /** スナップショットの1行から、関数を識別する項目（呼び出し側で返す）を除いたもの */
  private static ObjectNode signature(FunctionEntry function, boolean includeDefinition) {
    final ObjectNode signature = ToolResults.readObject(function.json());
    signature.remove(List.of("schema", "name"));
    if (!includeDefinition) {
      signature.remove(DEFINITION_FIELD);
    }
    return signature;
  }

  /**
   * 全オーバーロードの定義本体が同じ場合（Oracleのパッケージ内サブプログラム等）、重複して持たせず1つにまとめるメソッド<br>
   * 本体が異なる場合は{@code overloads}側にそれぞれ残したまま、長い場合だけ切り詰める
   *
   * @return まとめた定義本体。本体が異なる場合・いずれも無い場合はnull
   */
  private static String extractSharedDefinition(List<ObjectNode> overloads) {
    final Set<String> definitions =
        overloads.stream()
            .map(node -> node.path(DEFINITION_FIELD).asText(""))
            .filter(text -> !text.isEmpty())
            .collect(Collectors.toSet());
    if (definitions.size() != 1) {
      for (final ObjectNode node : overloads) {
        final String definition = node.path(DEFINITION_FIELD).asText(null);
        if (definition != null && definition.length() > MAX_DEFINITION_LENGTH) {
          node.put(DEFINITION_FIELD, truncateDefinition(definition));
        }
      }
      return null;
    }
    overloads.forEach(node -> node.remove(DEFINITION_FIELD));
    return truncateDefinition(definitions.iterator().next());
  }

  private static String truncateDefinition(String definition) {
    if (definition.length() <= MAX_DEFINITION_LENGTH) {
      return definition;
    }
    return definition.substring(0, MAX_DEFINITION_LENGTH)
        + "\n...(切り詰め。全"
        + definition.length()
        + "文字中"
        + MAX_DEFINITION_LENGTH
        + "文字を表示)";
  }

  /**
   * {@code list_functions}の結果
   *
   * @param nextOffset 続きを取得するときに指定する{@code offset}。続きが無い場合はnull
   */
  record ListFunctionsOutput(int total, Integer nextOffset, List<Function> functions) {

    /** 1関数・プロシージャのシグネチャ */
    record Function(
        String database,
        String schema,
        String name,
        String kind,
        String arguments,
        String result,
        String language) {

      static Function of(FunctionEntry function) {
        return new Function(
            function.key().database(),
            function.key().schema(),
            function.key().name(),
            function.kind(),
            function.arguments(),
            function.result(),
            function.language());
      }
    }
  }

  /**
   * {@code get_function}の結果
   *
   * @param definition {@code includeDefinition}指定時、全オーバーロードの定義本体が同じ場合にまとめた本体。 本体が異なる場合・{@code
   *     includeDefinition}未指定の場合はnull（その場合、本体は{@code overloads}側に残る）
   * @param overloads オーバーロードごとのシグネチャ（スナップショットの1行から名前を除いたもの。cliが項目を追加すれば、そのまま返る）
   * @param calledByTriggers この関数を実行するトリガー
   */
  record GetFunctionOutput(
      String database,
      String schema,
      String name,
      String definition,
      List<ObjectNode> overloads,
      List<CallingTrigger> calledByTriggers) {}

  /**
   * 関数を実行するトリガー
   *
   * @param table トリガーを持つテーブル（{@code スキーマ名.テーブル名}）
   */
  record CallingTrigger(String table, String trigger, String timing, List<String> events) {

    static CallingTrigger of(TableTrigger found) {
      return new CallingTrigger(
          found.table().key().qualifiedName(),
          found.trigger().name(),
          found.trigger().timing(),
          found.trigger().events());
    }
  }
}
