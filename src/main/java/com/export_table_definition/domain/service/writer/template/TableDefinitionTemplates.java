package com.export_table_definition.domain.service.writer.template;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;

/**
 * テーブル定義書き込みに利用するMarkdownのテンプレートを扱うクラス
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class TableDefinitionTemplates {
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String LINE_SEPARATOR_DOUBLE = LINE_SEPARATOR + LINE_SEPARATOR;
    private static final String HORIZON = "___";

    /**
     * テーブル定義ヘッダー
     * 
     * @param table テーブル情報
     * @return ヘッダー文字列
     */
    public static String fileHeader(TableEntity table) {
        return "# " + table.getHeaderTableName() + LINE_SEPARATOR_DOUBLE;
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
     * テーブル説明セクション
     * 
     * @return テーブル説明セクション文字列
     */
    public static String tableExplanation() {
        return """
                ## テーブル説明

                """;
    }

    /**
     * テーブル情報セクション
     * 
     * @param table テーブル情報
     * @return テーブル情報セクション文字列
     */
    public static String tableInfo(TableEntity table) {
        return """
                ## テーブル情報

                | スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
                |:---|:---|:---|:---|:---|
                """ + table.tableInfo() + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * カラム情報セクション
     * 
     * @param columns カラム情報のリスト
     * @param table   テーブル情報
     * @return カラム情報セクション文字列
     */
    public static String columns(List<ColumnEntity> columns, TableEntity table) {
        String header = """
                ## カラム情報

                | No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|:---|:---|
                """;
        return tableSection(columns, table, header, ColumnEntity::columnInfo, ColumnEntity::getSchemaTableName);
    }

    /**
     * ビュー情報セクション
     * 
     * @param table テーブル情報
     * @return ビュー情報セクション文字列
     */
    public static String view(TableEntity table) {
        if (!table.isView()) {
            return "";
        }
        return """
                ## ソース

                ```sql
                """ + LINE_SEPARATOR + table.definition() + LINE_SEPARATOR + """

                ```

                """;
    }

    /**
     * インデックス情報セクション
     * 
     * @param indexes インデックス情報のリスト
     * @param table   テーブル情報
     * @return インデックス情報セクション文字列
     */
    public static String indexes(List<IndexEntity> indexes, TableEntity table) {
        String header = """
                ## インデックス情報

                | No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
        return tableSection(indexes, table, header, IndexEntity::indexInfo, IndexEntity::getSchemaTableName);
    }

    /**
     * 制約情報セクション
     * 
     * @param constraints 制約情報のリスト
     * @param table       テーブル情報
     * @return 制約情報セクション文字列
     */
    public static String constraints(List<ConstraintEntity> constraints, TableEntity table) {
        String header = """
                ## 制約情報

                | No. | 制約名 | 種類 | 制約定義 | 備考 |
                |:---|:---|:---|:---|:---|
                """;
        return tableSection(constraints, table, header, ConstraintEntity::constraintInfo,
                ConstraintEntity::getSchemaTableName);
    }

    /**
     * 外部キー情報セクション
     *
     * @param foreignkeys 外部キー情報のリスト
     * @param table       テーブル情報
     * @return 外部キー情報セクション文字列
     */
    public static String foreignKeys(List<ForeignKeyEntity> foreignkeys, TableEntity table) {
        String header = """
                ## 外部キー情報

                | No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト |
                |:---|:---|:---|:---|:---|
                """;
        return tableSection(foreignkeys, table, header, ForeignKeyEntity::foreignkeyInfo,
                ForeignKeyEntity::getSchemaTableName);
    }

    /**
     * トリガー情報セクション
     *
     * @param triggers トリガー情報のリスト
     * @param table    テーブル情報
     * @return トリガー情報セクション文字列
     */
    public static String triggers(List<TriggerEntity> triggers, TableEntity table) {
        String header = """
                ## トリガー情報

                | No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
                |:---|:---|:---|:---|:---|:---|
                """;
        return tableSection(triggers, table, header, TriggerEntity::triggerInfo, TriggerEntity::getSchemaTableName);
    }

    /**
     * ER図セクション（Mermaid記法）<br>
     * 自テーブルはカラム・PK情報付きの箱として、関連テーブル（参照元・参照先）は
     * 属性なしの箱として描画する。関連テーブルの属性情報を必要としないため、
     * チャンク単位の分割取得（他チャンク・他スキーマのテーブル詳細を保持しないこと）の影響を受けない
     *
     * @param table         テーブル情報
     * @param columns       自テーブルのカラム情報のリスト
     * @param outgoingFks   自テーブルが参照している外部キー（自テーブル → 参照先）のリスト
     * @param incomingFks   自テーブルを参照している外部キー（参照元 → 自テーブル）のリスト
     * @return ER図セクション文字列
     */
    public static String erDiagram(TableEntity table, List<ColumnEntity> columns, List<ForeignKeyEntity> outgoingFks,
            List<ForeignKeyEntity> incomingFks) {
        StringBuilder sb = new StringBuilder("## ER図").append(LINE_SEPARATOR_DOUBLE);
        if (outgoingFks.isEmpty() && incomingFks.isEmpty()) {
            return sb.append("関連するテーブルはありません。").append(LINE_SEPARATOR_DOUBLE).toString();
        }
        final String selfId = mermaidId(table.schemaName(), table.physicalTableName());
        sb.append("```mermaid").append(LINE_SEPARATOR).append("erDiagram").append(LINE_SEPARATOR);
        outgoingFks.forEach(fk -> sb.append("    ")
                .append(mermaidId(fk.referenceSchemaName(), fk.referenceTableName()))
                .append(" ||--o{ ").append(selfId).append(" : \"").append(fk.foreignkeyName()).append('"')
                .append(LINE_SEPARATOR));
        incomingFks.forEach(fk -> sb.append("    ").append(selfId).append(" ||--o{ ")
                .append(mermaidId(fk.schemaName(), fk.tableName())).append(" : \"").append(fk.foreignkeyName())
                .append('"').append(LINE_SEPARATOR));
        sb.append("    ").append(selfId).append(" {").append(LINE_SEPARATOR);
        columns.forEach(c -> sb.append("        ").append(sanitizeType(c.columnType())).append(' ')
                .append(sanitizeIdentifier(c.physicalColumnName())).append(c.isPrimaryKey() ? " PK" : "")
                .append(LINE_SEPARATOR));
        sb.append("    }").append(LINE_SEPARATOR).append("```").append(LINE_SEPARATOR_DOUBLE);
        return sb.toString();
    }

    /**
     * Mermaid記法のエンティティ識別子を生成するメソッド<br>
     * スキーマ名を含めることで、同名テーブルが複数スキーマに存在する場合の識別子衝突を避ける
     *
     * @param schemaName        スキーマ名
     * @param physicalTableName 物理テーブル名
     * @return サニタイズ済みのエンティティ識別子
     */
    private static String mermaidId(String schemaName, String physicalTableName) {
        return sanitizeIdentifier(schemaName + "_" + physicalTableName);
    }

    /**
     * Mermaid記法で識別子として利用できない文字をアンダースコアに置換するメソッド
     *
     * @param value 変換対象の文字列
     * @return サニタイズ済みの文字列
     */
    private static String sanitizeIdentifier(String value) {
        return value.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /**
     * データ型からMermaid記法の属性型として利用できる文字列を生成するメソッド<br>
     * 桁数・精度を表す括弧部分を除去し、残った空白をアンダースコアに置換する
     *
     * @param columnType データ型
     * @return サニタイズ済みのデータ型文字列
     */
    private static String sanitizeType(String columnType) {
        return columnType.replaceAll("\\(.*\\)", "").trim().replaceAll("[^A-Za-z0-9_]+", "_");
    }

    /**
     * フッター
     * 
     * @param baseInfo データベース基本情報
     * @return フッター文字列
     */
    public static String footer(BaseInfoEntity baseInfo) {
        return HORIZON + LINE_SEPARATOR_DOUBLE + String.format("[テーブル一覧へ](../../../tableList_%s.md)", baseInfo.dbName())
                + LINE_SEPARATOR;
    }

    /**
     * テーブルごとのセクションを生成する共通メソッド
     * 
     * @param <T>                   エンティティの型
     * @param list                  エンティティのリスト
     * @param table                 テーブル情報
     * @param header                セクションのヘッダー文字列
     * @param infoMapper            エンティティから情報文字列を生成する関数
     * @param schemaTableNameGetter エンティティからスキーマ名とテーブル名を結合した文字列を取得する関数
     * @return テーブルごとのセクション文字列
     */
    private static <T> String tableSection(List<T> list, TableEntity table, String header,
            Function<T, String> infoMapper, Function<T, String> schemaTableNameGetter) {
        return header + list.stream().filter(e -> schemaTableNameGetter.apply(e).equals(table.getSchemaTableName()))
                .map(infoMapper).collect(Collectors.joining(LINE_SEPARATOR)) + LINE_SEPARATOR_DOUBLE;
    }
}
