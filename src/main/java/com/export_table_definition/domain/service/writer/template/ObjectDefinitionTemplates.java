package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.service.path.DocumentLocations;

/**
 * 関数/プロシージャ・シーケンス・ユーザー定義型の個別定義書き込みに利用する Markdownのテンプレートを扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ObjectDefinitionTemplates {

  /**
   * 基本情報セクション
   *
   * @param baseInfo データベース基本情報
   * @return 基本情報セクション文字列
   */
  private static String baseInfo(BaseInfoEntity baseInfo) {
    return MarkdownTemplateSupport.baseInfoSection(baseInfo);
  }

  /**
   * 一覧へ戻るフッター
   *
   * @param listType 戻り先の一覧の種別
   * @param baseInfo データベース基本情報
   * @return フッター文字列
   */
  private static String footer(ListDocumentType listType, BaseInfoEntity baseInfo) {
    return PagedSectionTemplates.backOnlyFooter(
        DocumentLocations.linkFromDefinition(
            DocumentLocations.listFile(listType, baseInfo.dbName())),
        listType.getBackLinkLabel());
  }

  /**
   * 関数・プロシージャの個別定義ファイル内容
   *
   * @param function 関数・プロシージャ情報
   * @param baseInfo データベース基本情報
   * @return ファイル内容文字列
   */
  public static String functionFile(FunctionEntity function, BaseInfoEntity baseInfo) {
    return "# "
        + function.getHeaderName()
        + LINE_SEPARATOR_DOUBLE
        + baseInfo(baseInfo)
        + "## 定義"
        + LINE_SEPARATOR_DOUBLE
        + "```sql"
        + LINE_SEPARATOR
        + function.definition()
        + LINE_SEPARATOR
        + "```"
        + LINE_SEPARATOR_DOUBLE
        + footer(ListDocumentType.FUNCTION, baseInfo);
  }

  /**
   * シーケンスの個別定義ファイル内容
   *
   * @param sequence シーケンス情報
   * @param baseInfo データベース基本情報
   * @return ファイル内容文字列
   */
  public static String sequenceFile(SequenceEntity sequence, BaseInfoEntity baseInfo) {
    String properties =
        """
                ## シーケンス情報

                | 増分 | 最小値 | 最大値 | キャッシュ | 開始値 | 循環 | 所有カラム |
                |:---|:---|:---|:---|:---|:---|:---|
                """
            + "|"
            + sequence.incrementBy()
            + "|"
            + sequence.minValue()
            + "|"
            + sequence.maxValue()
            + "|"
            + sequence.cacheSize()
            + "|"
            + sequence.startValue()
            + "|"
            + MarkdownTemplateSupport.marker(sequence.cycle())
            + "|"
            + sequence.ownedBy()
            + "|"
            + LINE_SEPARATOR_DOUBLE;
    return "# "
        + sequence.sequenceName()
        + LINE_SEPARATOR_DOUBLE
        + baseInfo(baseInfo)
        + properties
        + footer(ListDocumentType.SEQUENCE, baseInfo);
  }

  /**
   * ユーザー定義型の個別定義ファイル内容
   *
   * @param type ユーザー定義型情報
   * @param baseInfo データベース基本情報
   * @return ファイル内容文字列
   */
  public static String typeFile(TypeEntity type, BaseInfoEntity baseInfo) {
    String definition =
        """
                ## 定義

                | 種別 | 定義 |
                |:---|:---|
                """
            + "|"
            + type.typeCategory()
            + "|"
            + MarkdownTemplateSupport.escapePipe(type.definition())
            + "|"
            + LINE_SEPARATOR_DOUBLE;
    return "# "
        + type.typeName()
        + LINE_SEPARATOR_DOUBLE
        + baseInfo(baseInfo)
        + definition
        + footer(ListDocumentType.TYPE, baseInfo);
  }
}
