package com.export_table_definition.mcp.catalog;

/**
 * スナップショットの{@code database.json}（1DB）
 *
 * @param dbms DBMS種別（{@code PostgreSQL}・{@code Oracle}）。未設定の場合は空文字
 * @param majorVersion DBMSのメジャーバージョン。項目を持たない古いcliのスナップショットではnull
 */
public record DatabaseEntry(String name, String dbms, Integer majorVersion) {

  /** 未設定の項目（null）を空文字へ揃える */
  public DatabaseEntry {
    dbms = TextValues.orEmpty(dbms);
  }
}
