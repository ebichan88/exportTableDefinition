package com.export_table_definition.domain.service.writer.template;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * テーブル定義一覧書き込みに利用するMarkdownのテンプレートを扱うクラス
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class TableDefinitionListTemplates {
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String LINE_SEPARATOR_DOUBLE = LINE_SEPARATOR + LINE_SEPARATOR;
    private static final String HORIZON = "___";

    /**
     * テーブル定義一覧ヘッダー
     * 
     * @param baseInfo データベース基本情報
     * @return ヘッダー文字列
     */
    public static String fileHeader(BaseInfoEntity baseInfo) {
        return "# " + String.format("テーブル一覧（DB名：%s）", baseInfo.dbName()) + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * 基本情報セクション
     * 
     * @param baseInfo データベース基本情報
     * @return 基本情報セクション文字列
     */
    public static String baseInfo(BaseInfoEntity baseInfo) {
        return """
                ## 基本情報

                | RDBMS | データベース名 | 作成日 |
                |:---|:---|:---|
                """ + baseInfo.baseInfo() + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * 関連ドキュメントセクション<br>
     * トリガー・関数/プロシージャ・シーケンス・ユーザー定義型など、
     * 存在するオブジェクト一覧へのリンクを列挙する
     *
     * @param baseInfo データベース基本情報
     * @param entries  リンク表示名をキー、一覧ファイル名の接頭辞を値とするマップ（挿入順を保持すること）
     * @return 関連ドキュメントセクション文字列。entriesが空の場合は空文字列
     */
    public static String relatedDocuments(BaseInfoEntity baseInfo, Map<String, String> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("## 関連ドキュメント").append(LINE_SEPARATOR_DOUBLE);
        entries.forEach((label, prefix) -> sb
                .append(String.format("* [%s](./%sList_%s.md)  ", label, prefix, baseInfo.dbName()))
                .append(LINE_SEPARATOR));
        return sb.append(LINE_SEPARATOR).toString();
    }

    /**
     * テーブル一覧セクション
     *
     * @return テーブル一覧セクション文字列
     */
    public static String tableListTableHeader() {
        return """
                ## テーブル情報

                | No. | スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | Link | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
    }

    /**
     * テーブル一覧セクション（テーブル情報1行分）
     * 
     * @param table テーブル情報
     * @return テーブル一覧セクション文字列
     */
    public static String tableListLine(TableEntity table) {
        return table.tableInfoList() + LINE_SEPARATOR;
    }

    /**
     * テーブル一覧セクション（テーブル情報付き）
     * 
     * @param tables テーブル情報のリスト
     * @return テーブル一覧セクション文字列
     */
    public static String tableList(List<TableEntity> tables) {
        return tableListTableHeader()
                + tables.stream().map(TableEntity::tableInfoList).collect(Collectors.joining(LINE_SEPARATOR))
                + LINE_SEPARATOR;
    }

    /**
     * サブテーブル一覧リンクセクション
     * 
     * @param baseInfo  データベース基本情報
     * @param fileIndex ファイルインデックス
     * @return サブテーブル一覧リンクセクション文字列
     */
    public static String subTableListLink(BaseInfoEntity baseInfo, int fileIndex) {
        return String.format("* [テーブル一覧_%s](./tableList_%s_%s.md)  ", fileIndex, baseInfo.dbName(), fileIndex)
                + LINE_SEPARATOR;
    }

    /**
     * テーブル一覧フッター（ページング用）
     * 
     * @param baseInfo    データベース基本情報
     * @param totalPages  1ページあたりの最大テーブル数
     * @param currentPage テーブル数
     * @param fileIndex   ファイルインデックス
     * @return テーブル一覧フッター文字列
     */
    public static String writeTableListFooter(BaseInfoEntity baseInfo, int totalPages, int currentPage, int fileIndex) {
        if (totalPages <= 1) {
            return LINE_SEPARATOR;
        }
        StringBuilder sb = new StringBuilder(HORIZON).append(LINE_SEPARATOR_DOUBLE);
        if (fileIndex > 1) {
            sb.append(String.format("[<<前へ](./tableList_%s_%d.md) ", baseInfo.dbName(), fileIndex - 1));
        }
        if (currentPage < totalPages) {
            sb.append(String.format("[次へ>>](./tableList_%s_%d.md) ", baseInfo.dbName(), fileIndex + 1));
        }
        sb.append(LINE_SEPARATOR);
        return sb.toString();
    }

    /**
     * 改行コードを取得するメソッド
     * 
     * @return 改行コード
     */
    public static String lineSeparator() {
        return LINE_SEPARATOR;
    }
}