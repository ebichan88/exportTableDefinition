package com.export_table_definition.domain.model.schemaobject;

import com.export_table_definition.domain.model.table.SchemaTableKeyed;

/**
 * シーケンス情報に関するrecordクラス
 *
 * @param cycle 最大値（最小値）到達時に循環するか
 * @param ownedBy 所有カラム（テーブル.カラム形式）
 */
public record SequenceEntity(
    String dbName,
    String schemaName,
    String sequenceName,
    String incrementBy,
    String minValue,
    String maxValue,
    String cacheSize,
    String startValue,
    boolean cycle,
    String ownedBy) implements SchemaTableKeyed {

  @Override
  public String tableName() {
    return sequenceName;
  }
}
