package com.export_table_definition.domain.model.entity;

/**
 * インデックス情報に関するrecordクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record IndexEntity(String schemaName, String tableName, String indexInfo)
    implements SchemaTableKeyed {}
