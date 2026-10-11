package com.dbxray.domain.service.tableusage;

import com.dbxray.domain.model.database.Dbms;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Oracleのパッケージ本体から、最上位で定義されたサブプログラムの範囲を求めるクラス<br>
 * {@code BEGIN}・{@code CASE}と{@code END}の対応をスタック（ヒープ上）で数える。{@code IF}・{@code LOOP}は{@code END
 * IF}・{@code END LOOP}で閉じるため数えない。宣言部の中の入れ子のサブプログラムは、外側のサブプログラムの範囲に含める
 */
final class PackageSubprograms {

  private PackageSubprograms() {}

  /**
   * パッケージ本体の最上位のサブプログラム1つ
   *
   * @param name カタログでの名前
   * @param parameterNames 宣言の引数名（カタログでの名前。宣言順）
   * @param language 呼び出し仕様（{@code IS LANGUAGE JAVA …}・{@code IS EXTERNAL …}）の言語。PL/SQLで書かれていれば空文字
   * @param bodyStart 本体の開始位置（宣言の{@code IS}・{@code AS}の次の字句の位置）
   * @param bodyEnd 本体の終了位置（本体を閉じる{@code END}・呼び出し仕様を閉じる{@code ;}の字句の位置。含まない）
   */
  record Subprogram(
      String name, List<String> parameterNames, String language, int bodyStart, int bodyEnd) {

    /** リストは変更不可な複製として保持する */
    Subprogram {
      parameterNames = List.copyOf(parameterNames);
    }
  }

  /**
   * パッケージ本体の最上位のサブプログラムを求めるメソッド
   *
   * @param tokens 定義の字句（空白・コメントを除いたもの）
   * @param from パッケージ本体の宣言の{@code IS}・{@code AS}の次の位置
   * @return 最上位のサブプログラム（現れた順）。{@code END}で閉じていないものは含まない
   */
  static List<Subprogram> find(List<SqlToken> tokens, int from) {
    final List<Subprogram> found = new ArrayList<>();
    final Deque<Block> blocks = new ArrayDeque<>();
    Header header = null;
    int parenDepth = 0;
    for (int i = from; i < tokens.size(); i++) {
      final SqlToken token = tokens.get(i);
      if (token.isSymbol("(")) {
        parenDepth++;
      } else if (token.isSymbol(")")) {
        parenDepth = Math.max(0, parenDepth - 1);
      } else if (token.isSymbol(";")) {
        // 括弧は文をまたがないため、数え違いを次の文へ持ち越さない
        parenDepth = 0;
        if (header != null && !header.language.isEmpty() && blocks.isEmpty()) {
          found.add(header.toSubprogram(i));
        }
        header = null;
      } else if (header != null) {
        if (!header.language.isEmpty()) {
          continue;
        }
        if (parenDepth == header.parenDepth + 1 && token.isName() && isParameterStart(tokens, i)) {
          header.parameterNames.add(SqlNames.identifier(token, Dbms.ORACLE));
        } else if (parenDepth == header.parenDepth && (token.isWord("IS") || token.isWord("AS"))) {
          header.bodyStart = i + 1;
          header.language = callSpecLanguage(tokens, i + 1);
          if (header.language.isEmpty()) {
            blocks.push(new Block(header));
            header = null;
          }
        }
      } else if (token.kind() == SqlTokenKind.WORD) {
        header = visitKeyword(tokens, i, token, blocks, found, parenDepth);
        if (isWordAt(tokens, i, "END") && isEndOfIfOrLoopOrCase(tokens, i + 1)) {
          // END IF・END LOOP・END CASEの後ろの語を、新しいIF・CASE等として読まない
          i++;
        }
      }
    }
    return found;
  }

  /**
   * キーワード1つ分の状態の変化
   *
   * @return サブプログラムの宣言が始まった場合はその見出し。それ以外はnull
   */
  private static Header visitKeyword(
      List<SqlToken> tokens,
      int i,
      SqlToken token,
      Deque<Block> blocks,
      List<Subprogram> found,
      int parenDepth) {
    final SqlToken next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
    switch (AsciiCase.toUpper(token.text())) {
      case "FUNCTION", "PROCEDURE" -> {
        if (next != null && next.isName()) {
          return new Header(SqlNames.identifier(next, Dbms.ORACLE), parenDepth);
        }
      }
      case "DECLARE" -> blocks.push(new Block(null));
      case "BEGIN" -> {
        if (!blocks.isEmpty() && blocks.peek().awaitingBegin) {
          blocks.peek().awaitingBegin = false;
        } else {
          blocks.push(Block.started());
        }
      }
      case "CASE" -> blocks.push(Block.started());
      case "END" -> {
        if (next != null && (next.isWord("IF") || next.isWord("LOOP"))) {
          return null;
        }
        final Block closed = blocks.poll();
        if (closed != null && closed.header != null && blocks.isEmpty()) {
          found.add(closed.header.toSubprogram(i));
        }
      }
      default -> {}
    }
    return null;
  }

  /** 引数の並びの括弧の中で、{@code (}か{@code ,}の直後の名前は引数名 */
  private static boolean isParameterStart(List<SqlToken> tokens, int i) {
    final SqlToken previous = tokens.get(i - 1);
    return previous.isSymbol("(") || previous.isSymbol(",");
  }

  /**
   * 宣言の{@code IS}・{@code AS}の直後から、呼び出し仕様の言語を求めるメソッド
   *
   * @return {@code LANGUAGE JAVA}は{@code JAVA}、{@code EXTERNAL}は{@code C}。呼び出し仕様でなければ空文字
   */
  static String callSpecLanguage(List<SqlToken> tokens, int i) {
    if (isWordAt(tokens, i, "EXTERNAL")) {
      return "C";
    }
    if (!isWordAt(tokens, i, "LANGUAGE")) {
      return "";
    }
    final boolean named = i + 1 < tokens.size() && tokens.get(i + 1).isName();
    return named ? SqlNames.identifier(tokens.get(i + 1), Dbms.ORACLE) : "LANGUAGE";
  }

  private static boolean isEndOfIfOrLoopOrCase(List<SqlToken> tokens, int i) {
    return isWordAt(tokens, i, "IF") || isWordAt(tokens, i, "LOOP") || isWordAt(tokens, i, "CASE");
  }

  private static boolean isWordAt(List<SqlToken> tokens, int i, String keyword) {
    return i < tokens.size() && tokens.get(i).isWord(keyword);
  }

  /** 宣言中のサブプログラムの見出し（{@code FUNCTION 名前 (引数…) … IS}の{@code IS}まで） */
  private static final class Header {

    private final String name;
    private final int parenDepth;
    private final List<String> parameterNames = new ArrayList<>();
    private String language = "";
    private int bodyStart;

    private Header(String name, int parenDepth) {
      this.name = name;
      this.parenDepth = parenDepth;
    }

    private Subprogram toSubprogram(int bodyEnd) {
      return new Subprogram(name, parameterNames, language, bodyStart, bodyEnd);
    }
  }

  /** {@code END}で閉じるまとまり（サブプログラム・{@code DECLARE}・{@code BEGIN}・{@code CASE}）1つ */
  private static final class Block {

    /** サブプログラムなら見出し。それ以外はnull */
    private final Header header;

    /** 宣言部にいて、自身の{@code BEGIN}をまだ読んでいないか */
    private boolean awaitingBegin;

    private Block(Header header) {
      this.header = header;
      this.awaitingBegin = true;
    }

    private static Block started() {
      final Block block = new Block(null);
      block.awaitingBegin = false;
      return block;
    }
  }
}
