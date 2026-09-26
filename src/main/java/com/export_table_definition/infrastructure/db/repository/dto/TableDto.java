package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * テーブル情報に関してORMのデータの受け渡しに利用するDTOクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TableDto(
    String dbName,
    String schemaName,
    String logicalTableName,
    String physicalTableName,
    String tableType,
    String definition) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return AllTableEntityのインスタンス
   */
  public TableEntity toEntity() {
    return new TableEntity(
        dbName, schemaName, logicalTableName, physicalTableName, tableType, definition);
  }
}
