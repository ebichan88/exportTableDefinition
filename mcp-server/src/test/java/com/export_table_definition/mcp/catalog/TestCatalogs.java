package com.export_table_definition.mcp.catalog;

import java.util.List;

/** テスト用の{@link SchemaCatalog}を組み立てる補助 */
public final class TestCatalogs {

  private TestCatalogs() {}

  /** テーブルの一覧だけから組み立てる（DBMS種別は不明として扱う） */
  public static SchemaCatalog of(List<TableEntry> tables) {
    return SchemaCatalog.of(
        tables.stream()
            .map(table -> table.key().database())
            .distinct()
            .map(name -> new DatabaseEntry(name, null, null))
            .toList(),
        tables,
        List.of(),
        List.of(),
        List.of());
  }
}
