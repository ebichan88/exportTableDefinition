package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.HORIZON;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;

import java.util.List;

/**
 * 行数の多い表を複数ページに分割して出力する際に共通で利用するMarkdownのテンプレートを扱うクラス<br>
 * GitHub上で表として表示できる行数には上限があるため、一覧系のドキュメントは行数が多い場合に 別ファイルへ分割して出力する。本体ページに載せるリンク一覧と、分割ページのページ送りを扱う
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class PagedSectionTemplates {

  /**
   * セクションの見出し行
   *
   * @param heading 見出し文字列
   * @return 見出しセクション文字列
   */
  public static String heading(String heading) {
    return "## " + heading + LINE_SEPARATOR_DOUBLE;
  }

  /**
   * 分割した詳細ページへのリンク一覧セクション<br>
   * 行数が上限を超える場合、本体ページには表を載せず分割したページへのリンクのみを掲載する
   *
   * @param heading セクションの見出し
   * @param label 各ページのリンク表示名
   * @param pageHrefs 各ページへの相対パスのリスト（1ページ目から順に並ぶこと）
   * @return リンク一覧セクション文字列
   */
  public static String pagedSectionLinks(String heading, String label, List<String> pageHrefs) {
    StringBuilder sb = new StringBuilder(heading(heading));
    for (int i = 0; i < pageHrefs.size(); i++) {
      sb.append(String.format("* [%s_%d](%s)  ", label, i + 1, pageHrefs.get(i)))
          .append(LINE_SEPARATOR);
    }
    return sb.append(LINE_SEPARATOR).toString();
  }

  /**
   * 分割した詳細ページのフッター（ページ送り）
   *
   * @param prevHref 前ページへの相対パス。存在しない場合はnull
   * @param nextHref 次ページへの相対パス。存在しない場合はnull
   * @param backHref 本体ページへの相対パス
   * @param backLabel 本体ページへのリンク表示名
   * @return フッター文字列
   */
  public static String pageFooter(
      String prevHref, String nextHref, String backHref, String backLabel) {
    StringBuilder sb = new StringBuilder(HORIZON).append(LINE_SEPARATOR_DOUBLE);
    if (prevHref != null) {
      sb.append(String.format("[<<前へ](%s) ", prevHref));
    }
    if (nextHref != null) {
      sb.append(String.format("[次へ>>](%s) ", nextHref));
    }
    return sb.append(String.format("[%s](%s)", backLabel, backHref))
        .append(LINE_SEPARATOR)
        .toString();
  }
}
