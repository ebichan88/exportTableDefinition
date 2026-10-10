package com.dbxray.infrastructure.db.repository.dto;

import com.dbxray.domain.model.table.PartitionEntity;

/** パーティション情報に関してORMのデータの受け渡しに利用するDTOクラス */
public record PartitionDto(
    String schemaName,
    String tableName,
    String partitionSchemaName,
    String partitionName,
    String parentSchemaName,
    String parentName,
    String bound,
    String partitionKey) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return PartitionEntityのインスタンス
   */
  public PartitionEntity toEntity() {
    return new PartitionEntity(
        schemaName,
        tableName,
        partitionSchemaName,
        partitionName,
        parentSchemaName,
        parentName,
        DtoValues.text(bound),
        DtoValues.text(partitionKey));
  }
}
