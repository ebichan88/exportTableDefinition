package com.export_table_definition.domain.model.entity;

/**
 * 制約情報に関するrecordクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ConstraintEntity(String schemaName, String tableName, String constraintInfo)
    implements SchemaTableKeyed {}
