package com.export_table_definition.domain.model.entity;

import com.export_table_definition.domain.model.type.Cardinality;

/**
 * 外部キー情報に関するrecordクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ForeignKeyEntity(String schemaName, String tableName, String foreignkeyInfo, String foreignkeyName,
        String referenceSchemaName, String referenceTableName, Cardinality cardinality) implements SchemaTableKeyed {

    /**
     * 多重度を指定しない場合のコンストラクタ<br>
     * 多重度を判定できない場合は、外部キーの関連として最も一般的な1対多とみなす
     *
     * @param schemaName          スキーマ名
     * @param tableName           テーブル名
     * @param foreignkeyInfo      外部キー情報セクションに掲載する行の文字列
     * @param foreignkeyName      外部キー名
     * @param referenceSchemaName 参照先スキーマ名
     * @param referenceTableName  参照先テーブル名
     */
    public ForeignKeyEntity(String schemaName, String tableName, String foreignkeyInfo, String foreignkeyName,
            String referenceSchemaName, String referenceTableName) {
        this(schemaName, tableName, foreignkeyInfo, foreignkeyName, referenceSchemaName, referenceTableName,
                Cardinality.ONE_TO_MANY);
    }

    /**
     * 参照先の スキーマ.テーブル 形式の名称を取得するメソッド
     *
     * @return 参照先の スキーマ.テーブル 形式の名称
     */
    public String getReferenceSchemaTableName() {
        return referenceSchemaName + "." + referenceTableName;
    }
}
