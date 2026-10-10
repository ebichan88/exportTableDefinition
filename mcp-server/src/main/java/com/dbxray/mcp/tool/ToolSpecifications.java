package com.dbxray.mcp.tool;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** ツールの定義（入力スキーマ・引数の検証・エラーの返し方）の組み立て */
final class ToolSpecifications {

  /** DB名の引数。全ツールで同じ説明にする */
  static final Map<String, Object> DATABASE_PROPERTY =
      stringProperty("DB名。スナップショットに複数のDBがあり、同名のオブジェクトを区別したい場合だけ指定する");

  /** 一覧・検索をスキーマ名で絞り込む引数 */
  static final Map<String, Object> SCHEMA_FILTER_PROPERTY = stringProperty("スキーマ名で絞り込む場合に指定する");

  private ToolSpecifications() {}

  /**
   * 読み取り専用のツールを組み立てるメソッド<br>
   * 引数の誤り（{@link InvalidToolArgumentException}）は、ツールのエラー（{@code isError}）としてメッセージを返す
   *
   * @param title MCPクライアントの表示に使う短い日本語の名前
   * @param inputSchema {@link #objectSchema}で組み立てた入力スキーマ。プロパティ以外の引数は受け付けない
   */
  static SyncToolSpecification readOnlyTool(
      String name,
      String title,
      String description,
      Map<String, Object> inputSchema,
      Function<ToolArguments, CallToolResult> handler) {
    return SyncToolSpecification.builder()
        .tool(
            Tool.builder(name, inputSchema)
                .title(title)
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
                return handler.apply(new ToolArguments(request.arguments()));
              } catch (InvalidToolArgumentException e) {
                return CallToolResult.builder()
                    .addTextContent(e.getMessage())
                    .isError(true)
                    .build();
              }
            })
        .build();
  }

  /** 引数のオブジェクトの入力スキーマ（未知の引数を受け付けない） */
  static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
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

  /**
   * 名前で1つのオブジェクトを指定する引数（名前・{@code schema}・{@code database}）に、ツール固有の引数を加えたプロパティ
   *
   * @param nameArgument オブジェクト名を受け取る引数名
   * @param nameDescription オブジェクト名の引数の説明
   */
  static Map<String, Object> namedObjectProperties(
      String nameArgument, String nameDescription, Map<String, Object> extra) {
    return namedObjectProperties(nameArgument, stringProperty(nameDescription), extra);
  }

  /**
   * 名前で1つ（または複数）のオブジェクトを指定する引数に、ツール固有の引数を加えたプロパティ
   *
   * @param nameArgument オブジェクト名を受け取る引数名
   * @param nameProperty オブジェクト名の引数のプロパティ（{@link #stringProperty}・{@link #stringOrArrayProperty}等）
   */
  static Map<String, Object> namedObjectProperties(
      String nameArgument, Map<String, Object> nameProperty, Map<String, Object> extra) {
    final Map<String, Object> properties = new LinkedHashMap<>();
    properties.put(nameArgument, nameProperty);
    properties.put("schema", stringProperty("スキーマ名。同名のものが複数のスキーマにある場合に指定する"));
    properties.put("database", DATABASE_PROPERTY);
    properties.putAll(extra);
    return properties;
  }

  static Map<String, Object> stringProperty(String description) {
    return Map.of("type", "string", "description", description);
  }

  static Map<String, Object> integerProperty(String description, int minimum, int maximum) {
    return Map.of(
        "type", "integer", "minimum", minimum, "maximum", maximum, "description", description);
  }

  static Map<String, Object> booleanProperty(String description) {
    return Map.of("type", "boolean", "description", description);
  }

  static Map<String, Object> enumProperty(String description, List<String> values) {
    return Map.of("type", "string", "enum", values, "description", description);
  }

  /** 決まった値のいずれかを要素に持つ配列 */
  static Map<String, Object> enumArrayProperty(String description, List<String> values) {
    return Map.of(
        "type",
        "array",
        "items",
        Map.of("type", "string", "enum", values),
        "description",
        description);
  }

  static Map<String, Object> stringArrayProperty(String description) {
    return Map.of("type", "array", "items", Map.of("type", "string"), "description", description);
  }

  /** 文字列1つ、または文字列の配列を受け付ける引数（名前を1件だけ・複数まとめて指定できる引数に使う） */
  static Map<String, Object> stringOrArrayProperty(String description) {
    return Map.of(
        "type",
        List.of("string", "array"),
        "items",
        Map.of("type", "string"),
        "description",
        description);
  }
}
