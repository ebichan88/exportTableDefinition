package com.dbxray.mcp.tool;

import com.dbxray.mcp.catalog.TableColumn;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.io.UncheckedIOException;
import java.util.List;

/** ツールの結果のJSON化。値が無い項目（null・空文字・空リスト）は出力しない */
final class ToolResults {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);

  private ToolResults() {}

  /** 結果をJSONの文字列にして返すメソッド */
  static CallToolResult json(Object output) {
    try {
      return CallToolResult.builder()
          .addTextContent(OBJECT_MAPPER.writeValueAsString(output))
          .build();
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * スナップショットの1行に、そのオブジェクトを使うカラムを加えて返すメソッド
   *
   * @return 使うカラムが無い場合は1行そのもの。ある場合は{@code usedByColumns}（{@code スキーマ名.テーブル名.カラム名}）を加えたもの
   */
  static CallToolResult withUsedByColumns(String json, List<TableColumn> columns) {
    if (columns.isEmpty()) {
      return CallToolResult.builder().addTextContent(json).build();
    }
    final ObjectNode output = readObject(json);
    final ArrayNode names = output.putArray("usedByColumns");
    columns.forEach(
        found -> names.add(found.table().key().qualifiedName() + "." + found.column().name()));
    return CallToolResult.builder().addTextContent(output.toString()).build();
  }

  /**
   * スナップショットの1行を、項目を加工するために読み込むメソッド
   *
   * @param json スナップショットの1行（読み込み時にJSONのオブジェクトであることを確かめたもの）
   */
  static ObjectNode readObject(String json) {
    try {
      return (ObjectNode) OBJECT_MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
