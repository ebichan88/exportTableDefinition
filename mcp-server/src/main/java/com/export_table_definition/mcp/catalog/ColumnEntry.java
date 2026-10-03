package com.export_table_definition.mcp.catalog;

/**
 * 検索の対象にするカラムの情報
 *
 * @param logicalName 論理名。未設定の場合は空文字
 * @param remarks カラム備考（サイドカーYAML由来）。未設定の場合は空文字
 */
public record ColumnEntry(String name, String logicalName, String remarks) {

  /** 未設定の項目（null）を空文字へ揃える */
  public ColumnEntry {
    logicalName = TextValues.orEmpty(logicalName);
    remarks = TextValues.orEmpty(remarks);
  }
}
