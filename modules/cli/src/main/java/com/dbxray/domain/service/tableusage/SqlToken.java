package com.dbxray.domain.service.tableusage;

/**
 * 定義本体の字句（トークン）1つ
 *
 * @param text 元の文字列のうちこの字句の部分（引用符・区切りを含む）
 * @param start 元の文字列での開始位置（含む）
 * @param end 元の文字列での終了位置（含まない）
 * @param line 開始位置の行番号（1始まり。LF・CRLF・CRのいずれも1つの改行として数える）
 */
record SqlToken(SqlTokenKind kind, String text, int start, int end, int line) {

  /** 引用符の無い単語で、ASCIIの英字の大文字小文字を区別せずにキーワードと一致するか */
  boolean isWord(String keyword) {
    return kind == SqlTokenKind.WORD && AsciiCase.equalsIgnoreCase(text, keyword);
  }

  /** 指定の演算子・区切り記号か */
  boolean isSymbol(String symbol) {
    return kind == SqlTokenKind.SYMBOL && text.equals(symbol);
  }

  /** テーブル名等の名前の部品になりうる字句（引用符の無い単語か、引用符付きの識別子）か */
  boolean isName() {
    return kind == SqlTokenKind.WORD || kind == SqlTokenKind.QUOTED_IDENTIFIER;
  }

  /**
   * ドル引用符の中身の開始位置を取得するメソッド
   *
   * @return 元の文字列での位置（開始の区切り{@code $tag$}の直後）
   */
  int dollarContentStart() {
    return start + dollarTagLength();
  }

  /**
   * ドル引用符の中身の終了位置を取得するメソッド
   *
   * @return 元の文字列での位置（閉じていない場合は字句の終わり）
   */
  int dollarContentEnd() {
    return isTerminatedDollarString() ? end - dollarTagLength() : end;
  }

  /** ドル引用符が閉じているか（字句の解析は最初に現れた閉じの区切りで止めるため、末尾が区切りで終わり、開始の区切りと重ならなければ閉じている） */
  boolean isTerminatedDollarString() {
    final int tagLength = dollarTagLength();
    return text.length() >= tagLength * 2 && text.endsWith(text.substring(0, tagLength));
  }

  private int dollarTagLength() {
    return text.indexOf('$', 1) + 1;
  }
}
