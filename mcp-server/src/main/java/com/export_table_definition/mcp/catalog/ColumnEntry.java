package com.export_table_definition.mcp.catalog;

/**
 * テーブルのカラムのうち、検索・逆引きに使う項目
 *
 * @param logicalName 論理名。未設定の場合は空文字
 * @param type 型（{@code character varying(50)}等）。未設定の場合は空文字
 * @param defaultValue デフォルト値の式。未設定の場合は空文字
 * @param remarks カラム備考（サイドカーYAML由来）。未設定の場合は空文字
 */
public record ColumnEntry(
    String name,
    String logicalName,
    String type,
    boolean primaryKey,
    boolean notNull,
    String defaultValue,
    String remarks) {

  /** 未設定の項目（null）を空文字へ揃える */
  public ColumnEntry {
    logicalName = TextValues.orEmpty(logicalName);
    type = TextValues.orEmpty(type);
    defaultValue = TextValues.orEmpty(defaultValue);
    remarks = TextValues.orEmpty(remarks);
  }
}
