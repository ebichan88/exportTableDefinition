package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.HORIZON;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.relation.ForeignKeyGroup;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableKey;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import com.export_table_definition.domain.model.viewpoint.ViewpointContent;
import com.export_table_definition.domain.service.path.DocumentLocations;
import java.util.List;

/**
 * 観点ページ・観点一覧の書き込みに利用するMarkdownのテンプレートを扱うクラス<br>
 * ER図の描画と、テーブル・関連の表の1行分は、スキーマ別ER図（{@link ErDiagramTemplates}）と同じものを用いる。
 * 観点は人が選んだテーブルのまとまりのため、スキーマ別ER図と異なり連結成分によるグループ分割は行わない
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ViewpointTemplates {

  /**
   * 観点ページのファイルヘッダー
   *
   * @param viewpoint 観点
   * @param baseInfo データベース基本情報
   * @return ヘッダー文字列
   */
  public static String fileHeader(Viewpoint viewpoint, BaseInfoEntity baseInfo) {
    return "# "
        + String.format("観点：%s（DB名：%s）", viewpoint.name(), baseInfo.dbName())
        + LINE_SEPARATOR_DOUBLE;
  }

  /**
   * 基本情報セクション
   *
   * @param baseInfo データベース基本情報
   * @return 基本情報セクション文字列
   */
  public static String baseInfo(BaseInfoEntity baseInfo) {
    return MarkdownTemplateSupport.baseInfoSection(baseInfo);
  }

  /**
   * 観点の説明セクション
   *
   * @param viewpoint 観点
   * @return 説明セクション文字列。説明が未指定の場合は空文字（セクションごと省く）
   */
  public static String description(Viewpoint viewpoint) {
    if (viewpoint.description().isEmpty()) {
      return "";
    }
    return "## 説明" + LINE_SEPARATOR_DOUBLE + viewpoint.description() + LINE_SEPARATOR_DOUBLE;
  }

  /**
   * ER図セクション（Mermaid記法）<br>
   * 所属テーブル同士の関連のみを描画する。描画の内容・上限を超えた場合の扱いはスキーマ別ER図と同じ （{@link ErDiagramTemplates#erDiagram}）
   *
   * @param relations 両端が所属テーブルの関連のまとまり
   * @param maxNodes 1つの図に描画するノード数の上限。0以下の場合は上限なし
   * @return ER図セクション文字列
   */
  public static String erDiagram(ForeignKeyGroup relations, int maxNodes) {
    if (relations.foreignKeys().isEmpty()) {
      return "## ER図"
          + LINE_SEPARATOR_DOUBLE
          + "所属テーブル同士の関連（外部キー・論理リレーション）はありません。"
          + LINE_SEPARATOR_DOUBLE;
    }
    return ErDiagramTemplates.erDiagram(relations, maxNodes);
  }

  /**
   * 所属テーブルセクションの見出し
   *
   * @return 見出し文字列
   */
  public static String tableHeading() {
    return "所属テーブル";
  }

  /**
   * 所属テーブルセクションの表ヘッダー
   *
   * @return 表ヘッダー文字列
   */
  public static String tableHeader() {
    return ErDiagramTemplates.diagramTableHeader();
  }

  /**
   * 所属テーブルセクションの1行分<br>
   * 図中のノードからテーブル定義書への導線を、スキーマ別ER図の掲載テーブルと同じくこの一覧で代替する
   *
   * @param no 行番号
   * @param table 所属テーブル
   * @return 所属テーブル1行分の文字列
   */
  public static String tableLine(int no, TableEntity table) {
    return ErDiagramTemplates.diagramTableLine(no, TableKey.of(table), table);
  }

  /**
   * 所属テーブルが無い場合の所属テーブルセクション
   *
   * @return 所属テーブルセクション文字列
   */
  public static String noTables() {
    return PagedSectionTemplates.heading(tableHeading())
        + "出力対象のテーブルのうち、この観点に所属するものはありません。"
        + LINE_SEPARATOR_DOUBLE;
  }

  /**
   * 所属テーブル同士の関連の一覧セクション（ER図の描画を省略した場合の代替掲載）
   *
   * @param relations 両端が所属テーブルの関連のリスト
   * @return 関連の一覧セクション文字列。関連が無い場合は空文字
   */
  public static String relations(List<ForeignKeyEntity> relations) {
    return relationSection(ErDiagramTemplates.foreignKeyHeading(), relations);
  }

  /**
   * 観点外のテーブルとの関連の一覧セクション<br>
   * 観点のER図には所属テーブル同士の関連しか描かないため、観点の外にあるテーブルとのつながりを一覧で補う
   *
   * @param outsideRelations 片端だけが所属テーブルの関連のリスト
   * @return 関連の一覧セクション文字列。関連が無い場合は空文字
   */
  public static String outsideRelations(List<ForeignKeyEntity> outsideRelations) {
    return relationSection("観点外のテーブルとの関連", outsideRelations);
  }

  /**
   * 観点ページのフッター<br>
   * 観点一覧・テーブル一覧へ戻る導線を置く
   *
   * @param baseInfo データベース基本情報
   * @return フッター文字列
   */
  public static String footer(BaseInfoEntity baseInfo) {
    return HORIZON
        + LINE_SEPARATOR_DOUBLE
        + String.format(
            "[%s](%s) [%s](%s)",
            ListDocumentType.VIEWPOINT.getBackLinkLabel(),
            listLink(ListDocumentType.VIEWPOINT, baseInfo),
            ListDocumentType.TABLE.getBackLinkLabel(),
            listLink(ListDocumentType.TABLE, baseInfo))
        + LINE_SEPARATOR;
  }

  /**
   * 観点一覧のファイルヘッダー
   *
   * @param baseInfo データベース基本情報
   * @return ヘッダー文字列
   */
  public static String indexFileHeader(BaseInfoEntity baseInfo) {
    return MarkdownTemplateSupport.titledFileHeader(
        ListDocumentType.VIEWPOINT.getTitle(), baseInfo);
  }

  /**
   * 観点一覧セクションの見出し
   *
   * @return 見出し文字列
   */
  public static String indexHeading() {
    return "観点情報";
  }

  /**
   * 観点一覧セクションの表ヘッダー
   *
   * @return 表ヘッダー文字列
   */
  public static String indexHeader() {
    return """
                | No. | 観点名 | 説明 | テーブル数 | Link |
                |:---|:---|:---|:---|:---|
                """;
  }

  /**
   * 観点一覧セクションの1行分<br>
   * 説明は一覧の表を読みやすく保つため、先頭の1行のみを掲載する（全文は観点ページに掲載する）
   *
   * @param no 行番号
   * @param content 1観点分の出力内容
   * @param baseInfo データベース基本情報
   * @return 観点一覧1行分の文字列
   */
  public static String indexLine(int no, ViewpointContent content, BaseInfoEntity baseInfo) {
    final Viewpoint viewpoint = content.viewpoint();
    return MarkdownTemplateSupport.row(
            no,
            MarkdownTemplateSupport.escapeTableCell(viewpoint.name()),
            MarkdownTemplateSupport.escapeTableCell(
                viewpoint.description().lines().findFirst().orElse("")),
            content.tables().size(),
            MarkdownTemplateSupport.linkCell(
                DocumentLocations.linkFromBase(
                    DocumentLocations.viewpointFile(baseInfo.dbName(), viewpoint))))
        + LINE_SEPARATOR;
  }

  /**
   * 観点一覧のフッター
   *
   * @param baseInfo データベース基本情報
   * @return フッター文字列
   */
  public static String indexFooter(BaseInfoEntity baseInfo) {
    return PagedSectionTemplates.backOnlyFooter(
        listLink(ListDocumentType.TABLE, baseInfo), ListDocumentType.TABLE.getBackLinkLabel());
  }

  /**
   * 関連の一覧セクションを組み立てる共通メソッド
   *
   * @param heading セクションの見出し
   * @param relations 関連のリスト
   * @return 関連の一覧セクション文字列。関連が無い場合は空文字
   */
  private static String relationSection(String heading, List<ForeignKeyEntity> relations) {
    if (relations.isEmpty()) {
      return "";
    }
    final StringBuilder sb =
        new StringBuilder(PagedSectionTemplates.heading(heading))
            .append(ErDiagramTemplates.foreignKeyTableHeader());
    for (int i = 0; i < relations.size(); i++) {
      sb.append(ErDiagramTemplates.foreignKeyTableLine(i + 1, relations.get(i)));
    }
    return sb.append(LINE_SEPARATOR).toString();
  }

  /**
   * 一覧への相対リンクを生成するメソッド<br>
   * 観点ページ・観点一覧は出力ベースディレクトリ直下に配置される
   *
   * @param type 一覧の種別
   * @param baseInfo データベース基本情報
   * @return 一覧への相対リンク
   */
  private static String listLink(ListDocumentType type, BaseInfoEntity baseInfo) {
    return DocumentLocations.linkFromBase(DocumentLocations.listFile(type, baseInfo.dbName()));
  }
}
