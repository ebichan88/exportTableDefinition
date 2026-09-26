package com.export_table_definition.presentation.dto;

import com.export_table_definition.presentation.type.ProcessResult;

/**
 * 通常実行の処理結果に関するrecordクラス<br>
 * 失敗した場合の報告は{@link com.export_table_definition.presentation.FailureReporter}が組み立てるため、成功した場合の結果のみを表す
 *
 * @param message 処理結果のメッセージ
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ResultDto(String message) {

  /**
   * 処理結果のメッセージを返却する
   *
   * @return 処理結果の種別（成功）を先頭に付けた処理結果のメッセージ
   */
  public String getResultMessage() {
    return ProcessResult.SUCCESS.formatMessage(message);
  }
}
