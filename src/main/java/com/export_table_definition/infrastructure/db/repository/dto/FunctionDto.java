package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.schemaobject.FunctionEntity;

/** 関数・プロシージャ情報に関してORMのデータの受け渡しに利用するDTOクラス */
public record FunctionDto(
    String dbName,
    String schemaName,
    String functionName,
    int overloadIndex,
    int overloadCount,
    String functionKind,
    String functionArguments,
    String functionResult,
    String languageName,
    String definition) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return FunctionEntityのインスタンス
   */
  public FunctionEntity toEntity() {
    return new FunctionEntity(
        dbName,
        schemaName,
        functionName,
        overloadIndex,
        overloadCount,
        functionKind,
        DtoValues.text(functionArguments),
        DtoValues.text(functionResult),
        DtoValues.text(languageName),
        DtoValues.text(definition));
  }
}
