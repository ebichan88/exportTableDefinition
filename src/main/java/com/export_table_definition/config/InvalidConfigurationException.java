package com.export_table_definition.config;

/**
 * 設定ファイルの誤り（設定ファイル・キーが見つからない、値が不正等）を表す例外クラス<br>
 * 利用者が設定を見直せば解消する誤りだけを表す。呼び出し側は、設定の読み込みで起きる個々の例外（{@code
 * ResourceBundle}の例外等）を知らなくても、この例外だけで設定誤りを判別できる
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class InvalidConfigurationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * コンストラクタ
   *
   * @param message 誤りの内容（利用者向けのメッセージ）
   */
  public InvalidConfigurationException(String message) {
    super(message);
  }

  /**
   * コンストラクタ
   *
   * @param message 誤りの内容（利用者向けのメッセージ）
   * @param cause 誤りの原因となった例外
   */
  public InvalidConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
