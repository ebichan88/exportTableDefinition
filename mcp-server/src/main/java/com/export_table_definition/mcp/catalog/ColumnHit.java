package com.export_table_definition.mcp.catalog;

import java.util.List;

/**
 * カラムの逆引きで当てはまった1カラム
 *
 * @param references カラムが外部キー・論理リレーションで参照している先（複合キーの場合は対応する1カラム）
 */
public record ColumnHit(TableEntry table, ColumnEntry column, List<ColumnReference> references) {

  /**
   * カラムが参照している先
   *
   * @param table 参照先のテーブル。スナップショットに含まれないテーブルの場合もある
   */
  public record ColumnReference(ObjectKey table, String column, RelationKind kind) {}
}
