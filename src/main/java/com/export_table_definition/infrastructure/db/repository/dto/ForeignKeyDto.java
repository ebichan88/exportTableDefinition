package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.type.Cardinality;
import com.export_table_definition.domain.model.type.RelationType;

/**
 * 外部キー情報に関してORMのデータの受け渡しに利用するDTOクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ForeignKeyDto(
    String schemaName,
    String tableName,
    String foreignkeyName,
    String columnNames,
    String referenceSchemaName,
    String referenceTableName,
    String referenceColumnNames,
    boolean childKeyUnique,
    boolean childKeyMandatory) {

  /**
   * DTOからEntityへの変換メソッド<br>
   * DBのカタログから取得した外部キー制約のため、由来は常に{@link RelationType#PHYSICAL}となる。 SQLがカンマ区切りで連結して返す列名は、ここでリストへ分解する
   *
   * @return ForeignKeyEntityのインスタンス
   */
  public ForeignKeyEntity toEntity() {
    return new ForeignKeyEntity(
        schemaName,
        tableName,
        foreignkeyName,
        DtoValues.split(columnNames, ","),
        referenceSchemaName,
        referenceTableName,
        DtoValues.split(referenceColumnNames, ","),
        Cardinality.of(childKeyUnique, childKeyMandatory),
        RelationType.PHYSICAL);
  }
}
