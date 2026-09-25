package com.export_table_definition.presentation.type;

/** 処理結果の種別をもつ列挙型クラス */
public enum ProcessResult {
  /** 処理成功 */
  SUCCESS,
  /** 処理失敗 */
  FAIL;

  /**
   * 処理結果の種別を先頭に付けた、コンソール出力用のメッセージを組み立てるメソッド
   *
   * @param message 処理結果のメッセージ
   * @return 処理結果の種別とメッセージを改行で連結した文字列
   */
  public String formatMessage(String message) {
    return "[result]:" + this + System.lineSeparator() + message;
  }
}
