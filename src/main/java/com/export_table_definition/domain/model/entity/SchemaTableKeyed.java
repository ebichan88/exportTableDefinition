package com.export_table_definition.domain.model.entity;

/**
 * スキーマ名・テーブル名を持つエンティティに共通の振る舞いを提供するインタフェース
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface SchemaTableKeyed {

    /**
     * スキーマ名を取得するメソッド
     *
     * @return スキーマ名
     */
    String schemaName();

    /**
     * テーブル名を取得するメソッド
     *
     * @return テーブル名
     */
    String tableName();

    /**
     * スキーマ.テーブル 形式の名称を取得するメソッド
     *
     * @return スキーマ.テーブル 形式の名称
     */
    default String getSchemaTableName() {
        return schemaName() + "." + tableName();
    }
}
