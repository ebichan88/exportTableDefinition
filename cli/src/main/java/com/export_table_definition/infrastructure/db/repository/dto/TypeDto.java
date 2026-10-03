package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.schemaobject.TypeEntity;

/** ユーザー定義型（ENUM等）情報に関してORMのデータの受け渡しに利用するDTOクラス */
public record TypeDto(
    String dbName, String schemaName, String typeName, String typeCategory, String definition) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return TypeEntityのインスタンス
   */
  public TypeEntity toEntity() {
    return new TypeEntity(
        dbName, schemaName, typeName, DtoValues.text(typeCategory), DtoValues.text(definition));
  }
}
