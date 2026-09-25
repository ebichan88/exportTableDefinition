package com.export_table_definition.domain.model.snapshot;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * エンティティの値をスナップショット向けの値へ変換する共通処理を集約したクラス<br>
 * エンティティにはMarkdownの表示都合で空白1文字等が入る項目があるため、値が無いことを表す表現をnullに揃える。
 * また区切り文字で連結された文字列（カラムリスト等）は、構造化データとして扱えるようリストへ分解する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
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

  /**
   * 区切り文字で連結された文字列をリストへ分解するメソッド
   *
   * @param value 区切り文字で連結された文字列
   * @param separator 区切り文字
   * @return 分解した値のリスト（前後の空白は除去し、空要素は含めない）。値が無い場合は空リスト
   */
  static List<String> split(String value, String separator) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(Pattern.quote(separator)))
        .map(String::strip)
        .filter(element -> !element.isEmpty())
        .toList();
  }
}
