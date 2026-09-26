package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.table.IndexEntity;

/** インデックス情報に関してORMのデータの受け渡しに利用するDTOクラス */
public record IndexDto(
    String schemaName,
    String tableName,
    String indexName,
    String indexMethod,
    boolean isUnique,
    boolean isPrimary,
    String indexDefinition,
    String remarks) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return AllIndexEntityのインスタンス
   */
  public IndexEntity toEntity() {
    return new IndexEntity(
        schemaName,
        tableName,
        indexName,
        DtoValues.text(indexMethod),
        isUnique,
        isPrimary,
        DtoValues.text(indexDefinition),
        DtoValues.text(remarks));
  }
}
