package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.schemaobject.SequenceEntity;

/** シーケンス情報に関してORMのデータの受け渡しに利用するDTOクラス */
public record SequenceDto(
    String dbName,
    String schemaName,
    String sequenceName,
    String incrementBy,
    String minValue,
    String maxValue,
    String cacheSize,
    String startValue,
    boolean cycle,
    String ownedBy) {

  /**
   * DTOからEntityへの変換メソッド
   *
   * @return SequenceEntityのインスタンス
   */
  public SequenceEntity toEntity() {
    return new SequenceEntity(
        dbName,
        schemaName,
        sequenceName,
        DtoValues.text(incrementBy),
        DtoValues.text(minValue),
        DtoValues.text(maxValue),
        DtoValues.text(cacheSize),
        DtoValues.text(startValue),
        cycle,
        DtoValues.text(ownedBy));
  }
}
