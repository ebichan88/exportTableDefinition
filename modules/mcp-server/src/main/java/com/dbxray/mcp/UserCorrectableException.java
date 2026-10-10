package com.dbxray.mcp;

/**
 * 利用者が起動引数・スナップショットを見直せば解消する失敗を表す例外<br>
 * メッセージには何を直せばよいかを書く。起動時に標準エラーへ出力し、終了コード2で終了する
 */
public class UserCorrectableException extends RuntimeException {

  /**
   * @param message 利用者に何を直せばよいかを伝えるメッセージ
   */
  public UserCorrectableException(String message) {
    super(message);
  }

  /**
   * @param message 利用者に何を直せばよいかを伝えるメッセージ
   * @param cause 原因となった例外
   */
  public UserCorrectableException(String message, Throwable cause) {
    super(message, cause);
  }
}
