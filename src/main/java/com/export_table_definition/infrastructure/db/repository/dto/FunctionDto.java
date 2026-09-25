package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.entity.FunctionEntity;

/**
 * 関数・プロシージャ情報に関してORMのデータの受け渡しに利用するDTOクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record FunctionDto(
    String dbName,
    String schemaName,
    String functionName,
    String fileName,
    String functionListInfo,
    String definition) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return FunctionEntityのインスタンス
   */
  public FunctionEntity toEntity() {
    return new FunctionEntity(
        dbName, schemaName, functionName, fileName, functionListInfo, definition);
  }
}
