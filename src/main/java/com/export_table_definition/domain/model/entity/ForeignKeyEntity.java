package com.export_table_definition.domain.model.entity;

/**
 * 外部キー情報に関するrecordクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ForeignKeyEntity(String schemaName, String tableName, String foreignkeyInfo, String foreignkeyName,
        String referenceSchemaName, String referenceTableName) implements SchemaTableKeyed {

    /**
     * 参照先の スキーマ.テーブル 形式の名称を取得するメソッド
     *
     * @return 参照先の スキーマ.テーブル 形式の名称
     */
    public String getReferenceSchemaTableName() {
        return referenceSchemaName + "." + referenceTableName;
    }
}
