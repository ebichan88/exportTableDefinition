package com.export_table_definition.presentation.dto;

import com.export_table_definition.presentation.type.ProcessResult;

/**
 * ドキュメント差分検知（{@code --check}モード）の処理結果に関するrecordクラス
 *
 * @param result 処理結果（比較処理自体の成否。差分の有無は{@link #hasDifference()}で判定する）
 * @param message 処理結果のメッセージ
 * @param hasDifference 生成ドキュメントとコミット済みドキュメントに差分が見つかったか
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record DiffCheckResultDto(ProcessResult result, String message, boolean hasDifference) {

  /**
   * 処理結果のメッセージを返却する
   *
   * @return 処理結果のメッセージ
   */
  public String getResultMessage() {
    return "[result]:" + result.toString() + "\r\n" + message;
  }
}
