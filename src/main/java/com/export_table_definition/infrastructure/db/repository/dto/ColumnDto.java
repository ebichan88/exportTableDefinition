package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.entity.ColumnEntity;

/**
 * カラム情報に関してORMのデータの受け渡しに利用するDTOクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ColumnDto(
    String schemaName,
    String tableName,
    String columnInfo,
    String physicalColumnName,
    String columnType,
    String primaryKey) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return AllColumnEntityのインスタンス
   */
  public ColumnEntity toEntity() {
    return new ColumnEntity(
        schemaName, tableName, columnInfo, physicalColumnName, columnType, primaryKey);
  }
}
