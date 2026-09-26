package com.export_table_definition.domain.model.table;

/** カラム情報に関するrecordクラス */
public record ColumnEntity(
    String schemaName,
    String tableName,
    String logicalColumnName,
    String physicalColumnName,
    String columnType,
    String precisionScale,
    boolean primaryKey,
    boolean notNull,
    String defaultValue)
    implements SchemaTableKeyed {}
