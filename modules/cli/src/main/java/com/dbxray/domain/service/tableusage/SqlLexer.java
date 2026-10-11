package com.dbxray.domain.service.tableusage;

import com.dbxray.domain.model.database.Dbms;
import java.util.ArrayList;
import java.util.List;

/**
 * 関数・プロシージャの定義本体を字句（トークン）に分けるクラス<br>
 * 先頭から1文字ずつ読み、今いる場所が文字列・コメント・識別子のどれかを状態として持つ。コメントと文字列は互いの中で効かない （文字列の中の{@code
 * --}はコメントにせず、コメントの中の{@code '}で文字列を始めない）ため、同時に左から読まないと区別できない。
 * 規則はDBごとに異なる（PostgreSQLはブロックコメントを入れ子にでき、ドル引用符を持つ。Oracleは入れ子にできず、{@code q'[...]'}を持つ）。
 * 閉じていない文字列・コメント等は範囲の終わりまでをその要素とし、例外にはしない（コンパイルに失敗した本体もDBに残りうるため）
 */
final class SqlLexer {

  /** ドル引用符のタグの長さの上限。超えるものはドル引用符とみなさない（閉じの区切りを探す手間が「本体の長さ×タグの長さ」になるため） */
  static final int MAX_DOLLAR_TAG_LENGTH = 64;

  /** 1つの字句として扱う2文字の演算子・区切り記号 */
  private static final List<String> TWO_CHAR_SYMBOLS =
      List.of("::", ":=", "=>", "..", "||", "<<", ">>", "<=", ">=", "<>", "!=");

  private final String source;
  private final int to;
  private final Dbms dbms;
  private final boolean keepTrivia;
  private final List<SqlToken> tokens = new ArrayList<>();
  private int pos;
  private int line;
  private boolean incomplete;

  private SqlLexer(String source, int from, int to, int firstLine, Dbms dbms, boolean keepTrivia) {
    this.source = source;
    this.to = to;
    this.dbms = dbms;
    this.keepTrivia = keepTrivia;
    this.pos = from;
    this.line = firstLine;
  }

  /** 文字列全体を字句に分ける */
  static SqlLexResult lex(String source, Dbms dbms) {
    return lex(source, 0, source.length(), 1, dbms);
  }

  /**
   * 文字列の一部の範囲を字句に分けるメソッド（ドル引用符の中身を、元の文字列での位置のまま分け直すため）
   *
   * @param from 範囲の開始位置（含む）
   * @param to 範囲の終了位置（含まない）
   * @param firstLine 範囲の開始位置の行番号
   */
  static SqlLexResult lex(String source, int from, int to, int firstLine, Dbms dbms) {
    return new SqlLexer(source, from, to, firstLine, dbms, false).run();
  }

  /**
   * 空白・コメントも含めて字句に分けるメソッド<br>
   * 返す字句をつなぐと入力に戻る（字句の分け方が入力を漏れなく重なりなく覆うことを確かめられる）
   */
  static List<SqlToken> lexWithTrivia(String source, Dbms dbms) {
    return new SqlLexer(source, 0, source.length(), 1, dbms, true).run().tokens();
  }

  private SqlLexResult run() {
    while (pos < to) {
      final int start = pos;
      final int startLine = line;
      final SqlTokenKind kind = scanOne();
      if (keepTrivia || !kind.isTrivia()) {
        tokens.add(new SqlToken(kind, source.substring(start, pos), start, pos, startLine));
      }
      countLines(start, pos);
    }
    return new SqlLexResult(tokens, incomplete);
  }

  /** 現在の位置から字句を1つ読み、位置を字句の終わりまで進める（必ず1文字以上進める） */
  private SqlTokenKind scanOne() {
    final char c = source.charAt(pos);
    if (isWhitespace(c)) {
      while (pos < to && isWhitespace(source.charAt(pos))) {
        pos++;
      }
      return SqlTokenKind.WHITESPACE;
    }
    if (c == '-' && peek(1) == '-') {
      scanLineComment();
      return SqlTokenKind.COMMENT;
    }
    if (c == '/' && peek(1) == '*') {
      scanBlockComment();
      return SqlTokenKind.COMMENT;
    }
    if (c == '\'') {
      scanQuoted('\'', false);
      return SqlTokenKind.STRING;
    }
    if (c == '"') {
      scanQuoted('"', false);
      return SqlTokenKind.QUOTED_IDENTIFIER;
    }
    final SqlTokenKind dialectKind =
        dbms == Dbms.POSTGRESQL ? scanPostgresSpecific(c) : scanOracleSpecific(c);
    if (dialectKind != null) {
      return dialectKind;
    }
    if (isIdentifierStart(c)) {
      pos++;
      while (pos < to && isIdentifierPart(source.charAt(pos))) {
        pos++;
      }
      return SqlTokenKind.WORD;
    }
    if (isDigit(c) || (c == '.' && isDigit(peek(1)))) {
      scanNumber();
      return SqlTokenKind.NUMBER;
    }
    return scanSymbol(c);
  }

  /**
   * PostgreSQLに固有の字句（{@code E'...'}・{@code U&'...'}・{@code B'...'}等の接頭辞付きの文字列、ドル引用符、位置パラメータ）を読む
   *
   * @return 読んだ字句の種類。当てはまらない場合はnull（位置は進めない）
   */
  private SqlTokenKind scanPostgresSpecific(char c) {
    if ((c == 'E' || c == 'e') && peek(1) == '\'') {
      pos++;
      scanQuoted('\'', true);
      return SqlTokenKind.STRING;
    }
    if ((c == 'U' || c == 'u') && peek(1) == '&' && (peek(2) == '\'' || peek(2) == '"')) {
      final char quote = peek(2);
      pos += 2;
      scanQuoted(quote, false);
      return quote == '"' ? SqlTokenKind.QUOTED_IDENTIFIER : SqlTokenKind.STRING;
    }
    if ("BbXxNn".indexOf(c) >= 0 && peek(1) == '\'') {
      pos++;
      scanQuoted('\'', false);
      return SqlTokenKind.STRING;
    }
    if (c == '$') {
      return scanPostgresDollar();
    }
    return null;
  }

  /**
   * Oracleに固有の字句（{@code q'[...]'}・{@code N'...'}の文字列、条件付きコンパイル・問い合わせ指令、バインド変数）を読む
   *
   * @return 読んだ字句の種類。当てはまらない場合はnull（位置は進めない）
   */
  private SqlTokenKind scanOracleSpecific(char c) {
    if ((c == 'Q' || c == 'q') && peek(1) == '\'' && scanAlternativeQuote(pos + 2)) {
      return SqlTokenKind.STRING;
    }
    if ((c == 'N' || c == 'n')
        && (peek(1) == 'Q' || peek(1) == 'q')
        && peek(2) == '\''
        && scanAlternativeQuote(pos + 3)) {
      return SqlTokenKind.STRING;
    }
    if ((c == 'N' || c == 'n') && peek(1) == '\'') {
      pos++;
      scanQuoted('\'', false);
      return SqlTokenKind.STRING;
    }
    if (c == '$') {
      final int nameStart = peek(1) == '$' ? pos + 2 : pos + 1;
      if (nameStart < to && isIdentifierStart(source.charAt(nameStart))) {
        pos = nameStart;
        while (pos < to && isIdentifierPart(source.charAt(pos))) {
          pos++;
        }
        return SqlTokenKind.DIRECTIVE;
      }
      return null;
    }
    if (c == ':' && (isIdentifierStart(peek(1)) || isDigit(peek(1)))) {
      pos++;
      while (pos < to && isIdentifierPart(source.charAt(pos))) {
        pos++;
      }
      return SqlTokenKind.PARAMETER;
    }
    return null;
  }

  private void scanLineComment() {
    pos += 2;
    while (pos < to && source.charAt(pos) != '\n' && source.charAt(pos) != '\r') {
      pos++;
    }
  }

  /** PostgreSQLは入れ子の深さを数え、Oracleは最初の{@code *}{@code /}で閉じる */
  private void scanBlockComment() {
    pos += 2;
    int depth = 1;
    while (pos < to) {
      if (source.charAt(pos) == '*' && peek(1) == '/') {
        pos += 2;
        depth--;
        if (depth == 0) {
          return;
        }
      } else if (dbms == Dbms.POSTGRESQL && source.charAt(pos) == '/' && peek(1) == '*') {
        pos += 2;
        depth++;
      } else {
        pos++;
      }
    }
    incomplete = true;
  }

  /**
   * 引用符で囲んだ文字列・識別子を読む（位置は開始の引用符）<br>
   * 引用符を2つ重ねたものは中身の引用符とみなす
   *
   * @param backslashEscapes {@code \}の直後の1文字を中身とみなすか（PostgreSQLの{@code E'...'}）
   */
  private void scanQuoted(char quote, boolean backslashEscapes) {
    pos++;
    while (pos < to) {
      final char c = source.charAt(pos);
      if (backslashEscapes && c == '\\') {
        pos = Math.min(pos + 2, to);
      } else if (c == quote && peek(1) == quote) {
        pos += 2;
      } else if (c == quote) {
        pos++;
        return;
      } else {
        pos++;
      }
    }
    incomplete = true;
  }

  /**
   * Oracleの代替引用符（{@code q'[...]'}）を読む
   *
   * @param delimiterPos 区切り文字の位置
   * @return 代替引用符として読んだ場合はtrue。区切り文字が無い・空白の場合はfalse（位置は進めない）
   */
  private boolean scanAlternativeQuote(int delimiterPos) {
    if (delimiterPos >= to || isWhitespace(source.charAt(delimiterPos))) {
      return false;
    }
    final char close = closingDelimiter(source.charAt(delimiterPos));
    pos = delimiterPos + 1;
    while (pos < to) {
      if (source.charAt(pos) == close && peek(1) == '\'') {
        pos += 2;
        return true;
      }
      pos++;
    }
    incomplete = true;
    return true;
  }

  private static char closingDelimiter(char open) {
    return switch (open) {
      case '[' -> ']';
      case '{' -> '}';
      case '<' -> '>';
      case '(' -> ')';
      default -> open;
    };
  }

  /** PostgreSQLの{@code $}で始まる字句（位置パラメータ・ドル引用符）を読む。どちらでもなければ{@code $}1文字の記号とする */
  private SqlTokenKind scanPostgresDollar() {
    if (isDigit(peek(1))) {
      pos++;
      while (pos < to && isDigit(source.charAt(pos))) {
        pos++;
      }
      return SqlTokenKind.PARAMETER;
    }
    final int tagEnd = dollarTagEnd();
    if (tagEnd < 0) {
      pos++;
      return SqlTokenKind.SYMBOL;
    }
    final int tagLength = tagEnd - pos;
    final int tagStart = pos;
    for (int i = tagEnd; i < to; i++) {
      if (source.charAt(i) == '$'
          && i + tagLength <= to
          && source.regionMatches(i, source, tagStart, tagLength)) {
        pos = i + tagLength;
        return SqlTokenKind.DOLLAR_STRING;
      }
    }
    pos = to;
    incomplete = true;
    return SqlTokenKind.DOLLAR_STRING;
  }

  /**
   * 現在の位置からドル引用符の開始の区切り（{@code $$}・{@code $tag$}）を読む
   *
   * @return 区切りの直後の位置。区切りでない場合は-1
   */
  private int dollarTagEnd() {
    int i = pos + 1;
    if (i < to && isIdentifierStart(source.charAt(i))) {
      i++;
      while (i < to && i - pos - 1 <= MAX_DOLLAR_TAG_LENGTH && isDollarTagPart(source.charAt(i))) {
        i++;
      }
    }
    if (i < to && source.charAt(i) == '$' && i - pos - 1 <= MAX_DOLLAR_TAG_LENGTH) {
      return i + 1;
    }
    return -1;
  }

  /** {@code 1..10}（PL/SQL・PL/pgSQLの範囲）の{@code ..}は数値に含めない */
  private void scanNumber() {
    while (pos < to && isDigit(source.charAt(pos))) {
      pos++;
    }
    if (pos < to && source.charAt(pos) == '.' && peek(1) != '.') {
      pos++;
      while (pos < to && isDigit(source.charAt(pos))) {
        pos++;
      }
    }
    if (pos < to && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
      final int digitOffset = peek(1) == '+' || peek(1) == '-' ? 2 : 1;
      if (isDigit(peek(digitOffset))) {
        pos += digitOffset;
        while (pos < to && isDigit(source.charAt(pos))) {
          pos++;
        }
      }
    }
  }

  private SqlTokenKind scanSymbol(char c) {
    final String twoChars = String.valueOf(c) + peek(1);
    pos += TWO_CHAR_SYMBOLS.contains(twoChars) ? 2 : 1;
    return SqlTokenKind.SYMBOL;
  }

  /** 範囲の外は{@code \0}として扱う（比べる相手はいずれも{@code \0}以外の文字） */
  private char peek(int offset) {
    final int index = pos + offset;
    return index < to ? source.charAt(index) : '\0';
  }

  /** CRLFは1つの改行として数える（CRの直後がLFなら、LFの方で数える） */
  private void countLines(int start, int end) {
    for (int i = start; i < end; i++) {
      final char c = source.charAt(i);
      if (c == '\n' || (c == '\r' && (i + 1 >= to || source.charAt(i + 1) != '\n'))) {
        line++;
      }
    }
  }

  /**
   * 空白（区切りとして読み飛ばす文字）か<br>
   * PostgreSQLは全角空白・NBSPを空白として扱わないが、全角空白を含む本体はPL/pgSQLでは実行時まで誤りにならずDBに残りうるため、
   * 区切りとみなして書き手の意図したテーブルを示す。BOMも読み飛ばす
   */
  private static boolean isWhitespace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '﻿';
  }

  private static boolean isIdentifierStart(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || isNonAsciiLetter(c);
  }

  /** Oracleは{@code #}も識別子に使える（{@code A#B}） */
  private boolean isIdentifierPart(char c) {
    return isIdentifierStart(c) || isDigit(c) || c == '$' || (dbms == Dbms.ORACLE && c == '#');
  }

  /** ドル引用符のタグは、識別子と異なり{@code $}を含まない */
  private static boolean isDollarTagPart(char c) {
    return isIdentifierStart(c) || isDigit(c);
  }

  private static boolean isNonAsciiLetter(char c) {
    return c >= 0x80 && !isWhitespace(c);
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }
}
