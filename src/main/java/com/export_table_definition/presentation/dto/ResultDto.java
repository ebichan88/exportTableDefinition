package com.export_table_definition.presentation.dto;

import com.export_table_definition.presentation.type.ProcessResult;

/**
 * 処理結果に関するrecordクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ResultDto(ProcessResult result, String message) {

  /**
   * 処理結果のメッセージを返却する
   *
   * @return 処理結果のメッセージ
   */
  public String getResultMessage() {
    return result.formatMessage(message);
  }
}
