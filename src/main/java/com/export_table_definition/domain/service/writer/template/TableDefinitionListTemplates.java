package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import java.util.Map;

/**
 * テーブル定義一覧書き込みに利用するMarkdownのテンプレートを扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class TableDefinitionListTemplates {

  /**
   * テーブル定義一覧ヘッダー
   *
   * @param baseInfo データベース基本情報
   * @return ヘッダー文字列
   */
  public static String fileHeader(BaseInfoEntity baseInfo) {
    return MarkdownTemplateSupport.titledFileHeader("テーブル一覧", baseInfo);
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
   * 関連ドキュメントセクション<br>
   * トリガー・関数/プロシージャ・シーケンス・ユーザー定義型など、 存在するオブジェクト一覧へのリンクを列挙する
   *
   * @param baseInfo データベース基本情報
   * @param entries リンク表示名をキー、一覧ファイル名の接頭辞を値とするマップ（挿入順を保持すること）
   * @return 関連ドキュメントセクション文字列。entriesが空の場合は空文字列
   */
  public static String relatedDocuments(BaseInfoEntity baseInfo, Map<String, String> entries) {
    if (entries == null || entries.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("## 関連ドキュメント").append(LINE_SEPARATOR_DOUBLE);
    entries.forEach(
        (label, prefix) ->
            sb.append(String.format("* [%s](./%sList_%s.md)  ", label, prefix, baseInfo.dbName()))
                .append(LINE_SEPARATOR));
    return sb.append(LINE_SEPARATOR).toString();
  }

  /**
   * テーブル一覧セクションの表ヘッダー<br>
   * 見出しは{@link PagedSectionTemplates#heading(String)}で付与するため含まない。
   * 行数が多い場合に呼び出し側がページ単位で切り出して書き込めるようにするためで、 トリガー・関数/プロシージャなどの一覧（{@link
   * ObjectListTemplates}）と同じ方針である
   *
   * @return 表ヘッダー文字列
   */
  public static String tableListTableHeader() {
    return """
                | No. | スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | Link | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
  }

  /**
   * テーブル一覧セクション（テーブル情報1行分）
   *
   * @param no 行番号（1始まり）
   * @param table テーブル情報
   * @return テーブル一覧セクション文字列
   */
  public static String tableListLine(int no, TableEntity table) {
    return "|"
        + no
        + "|"
        + table.schemaName()
        + "|"
        + table.logicalTableName()
        + "|"
        + table.physicalTableName()
        + "|"
        + table.tableType()
        + "|"
        + tableDefinitionLink(table)
        + "|"
        + table.remarks()
        + "|"
        + LINE_SEPARATOR;
  }

  /**
   * テーブル定義書への相対パスをMarkdownのリンク記法で表す文字列を生成するメソッド<br>
   * テーブル定義書は出力ベースディレクトリ直下に配置されるため、{@code ./{DB名}/{スキーマ名}/{区分}/{物理テーブル名}.md}となる
   *
   * @param table テーブル情報
   * @return テーブル定義書へのリンク文字列
   */
  private static String tableDefinitionLink(TableEntity table) {
    return String.format(
        "[■](./%s/%s/%s/%s.md)",
        table.dbName(), table.schemaName(), table.tableType(), table.physicalTableName());
  }
}
