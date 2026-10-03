package com.export_table_definition.mcp.catalog;

import java.util.List;

/** 名前で指定されたテーブルを解決した結果 */
public sealed interface TableLookup {

  /** 1つに定まった場合 */
  record Found(TableEntry table) implements TableLookup {}

  /**
   * 同名のテーブルが複数のDB・スキーマにあり、1つに定まらない場合
   *
   * @param candidates 当てはまるテーブル（DB名・スキーマ名の順）
   */
  record Ambiguous(List<TableEntry> candidates) implements TableLookup {}

  /**
   * 当てはまるテーブルが無い場合
   *
   * @param suggestions 名前の似たテーブル（一致の強い順。無ければ空）
   */
  record NotFound(List<TableEntry> suggestions) implements TableLookup {}
}
