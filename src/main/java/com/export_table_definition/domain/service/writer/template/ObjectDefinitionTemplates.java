package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;

/**
 * 関数/プロシージャ・シーケンス・ユーザー定義型の個別定義書き込みに利用する
 * Markdownのテンプレートを扱うクラス
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
     * @param listPrefix 一覧ファイル名の接頭辞（例: function, sequence, type）
     * @param listLabel  一覧へのリンク表示名
     * @param baseInfo   データベース基本情報
     * @return フッター文字列
     */
    private static String footer(String listPrefix, String listLabel, BaseInfoEntity baseInfo) {
        return PagedSectionTemplates.pageFooter(null, null,
                String.format("../../../%sList_%s.md", listPrefix, baseInfo.dbName()), listLabel);
    }

    /**
     * 関数・プロシージャの個別定義ファイル内容
     *
     * @param function 関数・プロシージャ情報
     * @param baseInfo データベース基本情報
     * @return ファイル内容文字列
     */
    public static String functionFile(FunctionEntity function, BaseInfoEntity baseInfo) {
        return "# " + function.getHeaderName() + LINE_SEPARATOR_DOUBLE
                + baseInfo(baseInfo)
                + "## 定義" + LINE_SEPARATOR_DOUBLE
                + "```sql" + LINE_SEPARATOR + function.definition() + LINE_SEPARATOR + "```" + LINE_SEPARATOR_DOUBLE
                + footer("function", "関数・プロシージャ一覧へ", baseInfo);
    }

    /**
     * シーケンスの個別定義ファイル内容
     *
     * @param sequence シーケンス情報
     * @param baseInfo データベース基本情報
     * @return ファイル内容文字列
     */
    public static String sequenceFile(SequenceEntity sequence, BaseInfoEntity baseInfo) {
        String properties = """
                ## シーケンス情報

                | 増分 | 最小値 | 最大値 | キャッシュ | 開始値 | 循環 | 所有カラム |
                |:---|:---|:---|:---|:---|:---|:---|
                """ + sequence.sequenceInfo() + LINE_SEPARATOR_DOUBLE;
        return "# " + sequence.sequenceName() + LINE_SEPARATOR_DOUBLE
                + baseInfo(baseInfo)
                + properties
                + footer("sequence", "シーケンス一覧へ", baseInfo);
    }

    /**
     * ユーザー定義型の個別定義ファイル内容
     *
     * @param type     ユーザー定義型情報
     * @param baseInfo データベース基本情報
     * @return ファイル内容文字列
     */
    public static String typeFile(TypeEntity type, BaseInfoEntity baseInfo) {
        String definition = """
                ## 定義

                | 種別 | 定義 |
                |:---|:---|
                """ + "|" + type.typeCategory() + "|" + type.definition() + "|" + LINE_SEPARATOR_DOUBLE;
        return "# " + type.typeName() + LINE_SEPARATOR_DOUBLE
                + baseInfo(baseInfo)
                + definition
                + footer("type", "ユーザー定義型一覧へ", baseInfo);
    }
}
