package com.export_table_definition.mcp.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.io.UncheckedIOException;

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
