package com.export_table_definition.mcp.catalog;

import java.util.List;

/**
 * テーブルが持つ外部キー・論理リレーション（自テーブル → 参照先）
 *
 * @param name 外部キー名（論理リレーションの場合は関連名）
 * @param referenceSchema 参照先のスキーマ名。未設定の場合は空文字（自テーブルと同じスキーマとみなす）
 * @param cardinality 多重度（{@code ONE_TO_MANY}等）。参照先（親）から見た自テーブル（子）の数を表す
 */
public record RelationEntry(
    String name,
    List<String> columns,
    String referenceSchema,
    String referenceTable,
    List<String> referenceColumns,
    String cardinality) {

  /** 未設定の項目（null）を空文字・空リストへ揃える */
  public RelationEntry {
    name = TextValues.orEmpty(name);
    columns = columns == null ? List.of() : List.copyOf(columns);
    referenceSchema = TextValues.orEmpty(referenceSchema);
    referenceColumns = referenceColumns == null ? List.of() : List.copyOf(referenceColumns);
    cardinality = TextValues.orEmpty(cardinality);
  }
}
