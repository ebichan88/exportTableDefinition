package com.dbxray.mcp.catalog;

/** カラムと、それを持つテーブル */
public record TableColumn(TableEntry table, ColumnEntry column) {}
