package com.export_table_definition.mcp.tool;

/**
 * ツールの引数の誤りを表す例外<br>
 * MCPのツールエラー（{@code isError}）としてメッセージをAIへ返し、AIが引数を直して呼び直せるようにする
 */
final class InvalidToolArgumentException extends RuntimeException {

  /**
   * @param message AIに何を直せばよいかを伝えるメッセージ
   */
  InvalidToolArgumentException(String message) {
    super(message);
  }
}
