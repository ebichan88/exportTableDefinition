package com.export_table_definition.mcp.catalog;

import java.text.Normalizer;
import java.util.Locale;

/** 文字列の値の正規化 */
final class TextValues {

  private TextValues() {}

  /** null を空文字へ置き換えるメソッド */
  static String orEmpty(String value) {
    return value == null ? "" : value;
  }

  /**
   * 検索で比較するための形へ揃えるメソッド<br>
   * 全角英数字・半角カナの表記揺れを吸収するためNFKCで正規化し、大文字小文字を区別しないよう小文字にする
   */
  static String normalize(String value) {
    return Normalizer.normalize(orEmpty(value), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }
}
