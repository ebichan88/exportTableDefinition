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
    String foreignkeyInfo,
    String foreignkeyName,
    String columnNames,
    String referenceSchemaName,
    String referenceTableName,
    String referenceColumnNames,
    String childKeyUnique,
    String childKeyMandatory) {

  /** 参照元（子）テーブルの外部キー列が条件を満たすことを表すマーカー文字列 */
  private static final String MARKER = "○";

  /**
   * DTOからEntityへの変換メソッド<br>
   * DBのカタログから取得した外部キー制約のため、由来は常に{@link RelationType#PHYSICAL}となる
   *
   * @return ForeignKeyEntityのインスタンス
   */
  public ForeignKeyEntity toEntity() {
    return new ForeignKeyEntity(
        schemaName,
        tableName,
        foreignkeyInfo,
        foreignkeyName,
        columnNames,
        referenceSchemaName,
        referenceTableName,
        referenceColumnNames,
        Cardinality.of(MARKER.equals(childKeyUnique), MARKER.equals(childKeyMandatory)),
        RelationType.PHYSICAL);
  }
}
