package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.service.path.DocumentLocations;
import java.util.Set;

/** データベース単位ディレクトリのREADME書き込みに利用するMarkdownのテンプレートを扱うクラス */
public class ReadmeTemplates {

  /** READMEのファイルヘッダー */
  public static String fileHeader(BaseInfoEntity baseInfo) {
    return "# " + String.format("%s ドキュメント一覧", baseInfo.dbName()) + LINE_SEPARATOR_DOUBLE;
  }

  /** 基本情報セクション */
  public static String baseInfo(BaseInfoEntity baseInfo) {
    return MarkdownTemplateSupport.baseInfoSection(baseInfo);
  }

  /**
   * ドキュメント一覧セクション<br>
   * 出力される一覧ドキュメント（テーブル一覧・ER図一覧・観点一覧・トリガー/関数/シーケンス/型の一覧のうち、 対象が存在するもの）へのリンクを列挙する。個別のテーブル定義書・個別定義書へは、
   * それぞれの一覧ドキュメントを経由してたどれるため、ここには一覧のみを掲載する
   *
   * @param documents 出力する一覧ドキュメントの種別（掲載順）
   */
  public static String documentLinks(BaseInfoEntity baseInfo, Set<ListDocumentType> documents) {
    final StringBuilder sb = new StringBuilder("## ドキュメント一覧").append(LINE_SEPARATOR_DOUBLE);
    documents.forEach(
        type ->
            sb.append(
                    String.format(
                        "* [%s](%s)  ",
                        type.getTitle(),
                        DocumentLocations.linkFromDatabaseRoot(
                            DocumentLocations.listFile(type, baseInfo.dbName()))))
                .append(LINE_SEPARATOR));
    return sb.append(LINE_SEPARATOR).toString();
  }
}
