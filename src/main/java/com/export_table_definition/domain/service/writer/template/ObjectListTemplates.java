package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;

/**
 * トリガー・関数/プロシージャ・シーケンス・ユーザー定義型の一覧書き込みに利用する
 * Markdownのテンプレートを扱うクラス<br>
 * 表のセクションはヘッダーと1行分を個別に生成できるようにしている。
 * 行数が多い場合に呼び出し側がページ単位で切り出して書き込めるようにするためで、
 * テーブル一覧（{@link TableDefinitionListTemplates}）と同じ方針である
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ObjectListTemplates {

    /**
     * 一覧ファイルヘッダー
     *
     * @param title    一覧のタイトル
     * @param baseInfo データベース基本情報
     * @return ヘッダー文字列
     */
    public static String fileHeader(String title, BaseInfoEntity baseInfo) {
        return MarkdownTemplateSupport.titledFileHeader(title, baseInfo);
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
     * トリガー一覧セクションの表ヘッダー
     *
     * @return 表ヘッダー文字列
     */
    public static String triggerTableHeader() {
        return """
                | No. | スキーマ名 | テーブル名 | トリガー名 | タイミング | イベント | 実行関数 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
    }

    /**
     * 関数・プロシージャ一覧セクションの表ヘッダー
     *
     * @return 表ヘッダー文字列
     */
    public static String functionTableHeader() {
        return """
                | No. | スキーマ名 | 種別 | 名前 | 引数 | 戻り値 | 言語 | Link |
                |:---|:---|:---|:---|:---|:---|:---|:---|
                """;
    }

    /**
     * シーケンス一覧セクションの表ヘッダー
     *
     * @return 表ヘッダー文字列
     */
    public static String sequenceTableHeader() {
        return """
                | No. | スキーマ名 | シーケンス名 | 増分 | 最小値 | 最大値 | キャッシュ | 開始値 | 循環 | 所有カラム | Link |
                |:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
                """;
    }

    /**
     * ユーザー定義型一覧セクションの表ヘッダー
     *
     * @return 表ヘッダー文字列
     */
    public static String typeTableHeader() {
        return """
                | No. | スキーマ名 | 型名 | 種別 | 定義 | Link |
                |:---|:---|:---|:---|:---|:---|
                """;
    }

    /**
     * 一覧セクションの1行分<br>
     * 行番号を含む行の内容はSQL側で組み立てているため、ここでは改行を付与するのみとする
     *
     * @param listInfo エンティティが保持する一覧行の文字列
     * @return 一覧1行分の文字列
     */
    public static String listLine(String listInfo) {
        return listInfo + LINE_SEPARATOR;
    }

    /**
     * 一覧ファイルのフッター<br>
     * 個別定義書から一覧へ戻る導線（{@link ObjectDefinitionTemplates}）と対になるよう、
     * 一覧からテーブル一覧へ戻る導線を設ける
     *
     * @param baseInfo データベース基本情報
     * @return フッター文字列
     */
    public static String footer(BaseInfoEntity baseInfo) {
        return PagedSectionTemplates.pageFooter(null, null,
                String.format("./tableList_%s.md", baseInfo.dbName()), "テーブル一覧へ");
    }
}
