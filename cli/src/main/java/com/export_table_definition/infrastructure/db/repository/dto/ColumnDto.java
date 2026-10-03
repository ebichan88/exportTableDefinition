package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.table.ColumnEntity;

/** カラム情報に関してORMのデータの受け渡しに利用するDTOクラス */
public record ColumnDto(
    String schemaName,
    String tableName,
    String logicalColumnName,
    String physicalColumnName,
    String columnType,
    String precisionScale,
    boolean primaryKey,
    boolean notNull,
    String defaultValue) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return AllColumnEntityのインスタンス
   */
  public ColumnEntity toEntity() {
    return new ColumnEntity(
        schemaName,
        tableName,
        DtoValues.text(logicalColumnName),
        physicalColumnName,
        columnType,
        DtoValues.text(precisionScale),
        primaryKey,
        notNull,
        DtoValues.text(defaultValue));
  }
}
