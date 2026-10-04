package com.export_table_definition.mcp.catalog;

/** カラムと、それを持つテーブル */
public record TableColumn(TableEntry table, ColumnEntry column) {}
