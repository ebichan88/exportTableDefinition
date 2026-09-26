package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.type.TableType;

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
   * DTOからEntityへの変換メソッド<br>
   * 区分はこの時点で{@link TableType}へ変換するため、未知の区分は読み込み時に検知される
   *
   * @return TableEntityのインスタンス
   * @throws IllegalArgumentException 区分が未知の値の場合
   */
  public TableEntity toEntity() {
    return new TableEntity(
        dbName,
        schemaName,
        DtoValues.text(logicalTableName),
        physicalTableName,
        TableType.findByName(tableType),
        DtoValues.text(definition));
  }
}
