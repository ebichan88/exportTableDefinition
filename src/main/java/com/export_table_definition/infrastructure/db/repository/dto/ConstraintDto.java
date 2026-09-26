package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.entity.ConstraintEntity;

/**
 * 制約情報に関してORMのデータの受け渡しに利用するDTOクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ConstraintDto(
    String schemaName,
    String tableName,
    String constraintName,
    String constraintType,
    String constraintDefinition,
    String remarks) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return AllConstraintEntityのインスタンス
   */
  public ConstraintEntity toEntity() {
    return new ConstraintEntity(
        schemaName,
        tableName,
        constraintName,
        DtoValues.text(constraintType),
        DtoValues.text(constraintDefinition),
        DtoValues.text(remarks));
  }
}
