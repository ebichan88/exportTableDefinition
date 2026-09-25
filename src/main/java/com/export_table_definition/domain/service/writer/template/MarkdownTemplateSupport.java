package com.export_table_definition.domain.service.writer.template;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;

/**
 * 各テンプレートクラスで共通して利用するMarkdownの定数・部品を集約したクラス<br>
 * 改行コードや水平線、「## 基本情報」セクション、タイトル付きファイルヘッダーなど、
 * 複数のテンプレートクラスで内容が重複していたものをここに集約する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class MarkdownTemplateSupport {

    public static final String LINE_SEPARATOR = System.lineSeparator();
    public static final String LINE_SEPARATOR_DOUBLE = LINE_SEPARATOR + LINE_SEPARATOR;
    public static final String HORIZON = "___";

    private MarkdownTemplateSupport() {
    }

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
                """ + baseInfo.baseInfo() + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * タイトル付きファイルヘッダー（「# タイトル（DB名：xxx）」の形式）<br>
     * ER図一覧・オブジェクト一覧など、タイトルとDB名のみで組み立てられるヘッダーで共通利用する
     *
     * @param title    ページのタイトル
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
}
