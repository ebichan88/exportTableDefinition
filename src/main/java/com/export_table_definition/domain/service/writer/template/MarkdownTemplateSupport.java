package com.export_table_definition.domain.service.writer.template;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;

/**
 * 各テンプレートクラスで共通して利用するMarkdownの定数・部品を集約したクラス<br>
 * 改行コードや水平線、「## 基本情報」セクション、タイトル付きファイルヘッダーなど、 複数のテンプレートクラスで内容が重複していたものをここに集約する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class MarkdownTemplateSupport {

  public static final String LINE_SEPARATOR = System.lineSeparator();
  public static final String LINE_SEPARATOR_DOUBLE = LINE_SEPARATOR + LINE_SEPARATOR;
  public static final String HORIZON = "___";

  /** 表のセルで真偽値の「真」を表すマーカー文字列（「偽」は空文字とする） */
  private static final String MARKER = "○";

  private MarkdownTemplateSupport() {}

  /**
   * 基本情報セクション<br>
   * テーブル定義書・各種一覧・ER図など、ほぼ全てのファイルの先頭に共通で掲載する
   *
   * @param baseInfo データベース基本情報
   * @return 基本情報セクション文字列
   */
  public static String baseInfoSection(BaseInfoEntity baseInfo) {
    return """
                ## 基本情報

                | RDBMS | データベース名 | 作成日 |
                |:---|:---|:---|
                """
        + row(baseInfo.dbmsName(), baseInfo.dbName(), baseInfo.generatedDate())
        + LINE_SEPARATOR_DOUBLE;
  }

  /**
   * Markdownの表の1行を組み立てるメソッド<br>
   * 各引数を{@code |}区切りで連結し、先頭・末尾にも{@code |}を付与する（末尾の改行は含まない）。 セルの値は{@link
   * String#valueOf}相当で文字列化するため、エスケープが必要な自由記述文字列は 呼び出し側で{@link #escapeTableCell}等を適用した上で渡すこと
   *
   * @param cells セルの値（先頭から順に列として並ぶ）
   * @return {@code |cell1|cell2|...|} 形式の1行分の文字列
   */
  public static String row(Object... cells) {
    final StringBuilder sb = new StringBuilder();
    for (Object cell : cells) {
      sb.append('|').append(cell);
    }
    return sb.append('|').toString();
  }

  /**
   * テーブル定義書・個別定義ファイルなどへのリンクを表す表セルを組み立てるメソッド
   *
   * @param href リンク先への相対パス
   * @return {@code [■](href)} 形式のリンクセル文字列
   */
  public static String linkCell(String href) {
    return "[■](" + href + ")";
  }

  /**
   * タイトル付きファイルヘッダー（「# タイトル（DB名：xxx）」の形式）<br>
   * ER図一覧・オブジェクト一覧など、タイトルとDB名のみで組み立てられるヘッダーで共通利用する
   *
   * @param title ページのタイトル
   * @param baseInfo データベース基本情報
   * @return ヘッダー文字列
   */
  public static String titledFileHeader(String title, BaseInfoEntity baseInfo) {
    return "# " + String.format("%s（DB名：%s）", title, baseInfo.dbName()) + LINE_SEPARATOR_DOUBLE;
  }

  /**
   * 手動付帯情報などの自由記述文字列を、Markdownの表セルへ安全に埋め込める形へエスケープするメソッド<br>
   * セル区切りとして解釈される{@code |}をエスケープし、セルを崩す改行（CR/LF）は{@code <br>}に置換する
   *
   * @param value エスケープ対象の文字列（nullの場合は空文字として扱う）
   * @return 表セルへ埋め込み可能な文字列
   */
  public static String escapeTableCell(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    return value.replace("|", "\\|").replaceAll("\\r\\n|\\r|\\n", "<br>");
  }

  /**
   * DBのカタログから取得した定義文字列（インデックス定義・制約定義等）を、Markdownの表セルへ埋め込める形へエスケープするメソッド<br>
   * セル区切りとして解釈される{@code |}のみをエスケープする。{@link #escapeTableCell(String)}と異なり改行は置換しない
   * （カタログ由来の定義は1行で取得しているため）。 SQL側でエスケープすると構造化データ（スナップショット等）にもMarkdown記法が混入するため、表示用のエスケープはここで行う
   *
   * @param value エスケープ対象の文字列（nullの場合は空文字として扱う）
   * @return 表セルへ埋め込み可能な文字列
   */
  public static String escapePipe(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    return value.replace("|", "\\|");
  }

  /**
   * 真偽値を表のセルに表示するマーカー文字列へ変換するメソッド
   *
   * @param value 真偽値
   * @return 真の場合は{@code ○}、偽の場合は空文字
   */
  public static String marker(boolean value) {
    return value ? MARKER : "";
  }
}
