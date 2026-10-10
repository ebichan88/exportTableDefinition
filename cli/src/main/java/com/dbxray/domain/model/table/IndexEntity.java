package com.dbxray.domain.model.table;

/**
 * インデックス情報に関するrecordクラス
 *
 * @param indexMethod インデックスの種別（アクセスメソッド）
 */
public record IndexEntity(
    String schemaName,
    String tableName,
    String indexName,
    String indexMethod,
    boolean isUnique,
    boolean isPrimary,
    String indexDefinition,
    String remarks)
    implements SchemaTableKeyed {}
