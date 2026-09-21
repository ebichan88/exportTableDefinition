package com.export_table_definition.domain.model.entity;

/**
 * カラム情報に関するrecordクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ColumnEntity(String schemaName, String tableName, String columnInfo, String physicalColumnName,
        String columnType, String primaryKey) {

    /** 主キーであることを表すマーカー文字列 */
    private static final String PRIMARY_KEY_MARKER = "○";

    /**
     * スキーマ.テーブル 形式の名称を取得するメソッド
     *
     * @return スキーマ.テーブル 形式の名称
     */
    public String getSchemaTableName() {
        return schemaName + "." + tableName;
    }

    /**
     * 主キーであるか判定するメソッド
     *
     * @return 主キーの場合はtrue。それ以外の場合はfalse
     */
    public boolean isPrimaryKey() {
        return PRIMARY_KEY_MARKER.equals(primaryKey);
    }
}
