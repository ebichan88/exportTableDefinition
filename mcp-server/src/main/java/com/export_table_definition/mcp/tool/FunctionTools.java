package com.export_table_definition.mcp.tool;

import static com.export_table_definition.mcp.tool.Page.withPageProperties;
import static com.export_table_definition.mcp.tool.ToolSpecifications.DATABASE_PROPERTY;
import static com.export_table_definition.mcp.tool.ToolSpecifications.SCHEMA_FILTER_PROPERTY;
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

/**
 * 関数・プロシージャを調べるツール（{@code list_functions}・{@code get_function}）<br>
 * 定義本体はAIのコンテキストを圧迫するため返さない。シグネチャ（引数・戻り値・言語）だけを返す
 */
final class FunctionTools {

  static final String LIST_FUNCTIONS = "list_functions";
  static final String GET_FUNCTION = "get_function";

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  /** 定義本体の項目名（スナップショットの項目名） */
  private static final String DEFINITION_FIELD = "definition";

  private final SchemaCatalog catalog;

  FunctionTools(SchemaCatalog catalog) {
    this.catalog = catalog;
  }

  /** ツールの一覧 */
  List<SyncToolSpecification> specifications() {
    return List.of(
        readOnlyTool(
            LIST_FUNCTIONS,
            "関数・プロシージャの名前・種別・引数・戻り値・言語を、DB名・スキーマ名・名前の順に一覧で返す（定義本体は返さない）。"
                + "同名の関数（オーバーロード）はそれぞれ返す。PostgreSQLのみ（Oracleのスナップショットでは0件）",
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
            "関数・プロシージャのシグネチャ（種別・引数・戻り値・言語）を返す（定義本体は返さない）。" + "同名の関数（オーバーロード）はまとめて返す",
            objectSchema(
                namedObjectProperties(
                    "function", "関数・プロシージャ名（大文字小文字を区別しない）。スキーマ名.関数名の形でもよい", Map.of()),
                List.of("function")),
            this::getFunction));
  }

  private CallToolResult listFunctions(ToolArguments arguments) {
    final NameFilter filter = NameFilter.of(arguments.optionalString("query"));
    final Page page = Page.read(arguments, DEFAULT_LIMIT, MAX_LIMIT);
    final List<FunctionEntry> functions = catalog.listFunctions(arguments.scope(), filter);
    return ToolResults.json(
        new ListFunctionsOutput(
            functions.size(),
            page.nextOffset(functions.size()),
            page.apply(functions).stream().map(ListFunctionsOutput.Function::of).toList()));
  }

  private CallToolResult getFunction(ToolArguments arguments) {
    final FunctionOverloads function =
        ObjectResolver.resolve(
            arguments, "function", "関数", LIST_FUNCTIONS, catalog::lookupFunction);
    return ToolResults.json(
        new GetFunctionOutput(
            function.key().database(),
            function.key().schema(),
            function.key().name(),
            function.overloads().stream().map(FunctionTools::signature).toList(),
            catalog.triggersCalling(function).stream().map(CallingTrigger::of).toList()));
  }

  /**
   * 関数を、オーバーロードを区別できる形で表すメソッド
   *
   * @return {@code スキーマ名.関数名(引数)}
   */
  static String signatureName(FunctionEntry function) {
    return function.key().qualifiedName() + "(" + function.arguments() + ")";
  }

  /** スナップショットの1行から、関数を識別する項目（呼び出し側で返す）と定義本体を除いたもの */
  private static ObjectNode signature(FunctionEntry function) {
    final ObjectNode signature = ToolResults.readObject(function.json());
    signature.remove(List.of("schema", "name", DEFINITION_FIELD));
    return signature;
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
   * @param overloads オーバーロードごとのシグネチャ（スナップショットの1行から定義本体を除いたもの。cliが項目を追加すれば、そのまま返る）
   * @param calledByTriggers この関数を実行するトリガー
   */
  record GetFunctionOutput(
      String database,
      String schema,
      String name,
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
