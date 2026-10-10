package com.dbxray.mcp.catalog;

/** トリガーと、それを持つテーブル */
public record TableTrigger(TableEntry table, TriggerEntry trigger) {}
