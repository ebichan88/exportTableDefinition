package com.export_table_definition.mcp.catalog;

/**
 * テーブルを一意に識別するキー
 *
 * @param database DB名（スナップショットの{@code {DB名}}ディレクトリ）
 */
public record TableKey(String database, String schema, String name) {

  /**
   * スキーマ修飾した名前を返すメソッド
   *
   * @return {@code スキーマ名.テーブル名}
   */
  public String qualifiedName() {
    return schema + "." + name;
  }
}
