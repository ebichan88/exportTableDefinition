package com.export_table_definition.mcp.catalog;

/**
 * 1スキーマに含まれるオブジェクトの数
 *
 * @param dbms DBMS種別。未設定の場合は空文字
 */
public record SchemaSummary(
    String database, String dbms, String schema, int tables, int views, int materializedViews) {}
