package com.export_table_definition.domain.model.table;

/**
 * 制約情報に関するrecordクラス
 *
 * @param constraintType 制約種別（CHECK/FOREIGN KEY/PRIMARY KEY/UNIQUE）
 */
public record ConstraintEntity(
    String schemaName,
    String tableName,
    String constraintName,
    String constraintType,
    String constraintDefinition,
    String remarks)
    implements SchemaTableKeyed {}
