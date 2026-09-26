package com.export_table_definition.domain.model.snapshot;

/**
 * エンティティの値をスナップショット向けの値へ変換する共通処理を集約したクラス<br>
 * エンティティは値が無いことを空文字で表すが、スナップショットではnullで表す（JSONでは項目ごと省略される）
 */
final class SnapshotValues {

  private SnapshotValues() {}

  /**
   * 値が無いことを表す空文字・空白のみの文字列をnullへ正規化するメソッド
   *
   * @param value 変換対象の文字列
   * @return 空文字・空白のみ・nullの場合はnull。それ以外は元の文字列
   */
  static String text(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }
}
