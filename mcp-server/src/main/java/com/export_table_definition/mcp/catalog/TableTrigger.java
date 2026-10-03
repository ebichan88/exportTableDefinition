package com.export_table_definition.mcp.catalog;

/** トリガーと、それを持つテーブル */
public record TableTrigger(TableEntry table, TriggerEntry trigger) {}
