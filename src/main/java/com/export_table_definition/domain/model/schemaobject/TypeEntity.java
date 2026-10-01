package com.export_table_definition.domain.model.schemaobject;

import com.export_table_definition.domain.model.table.SchemaTableKeyed;

/**
 * ユーザー定義型（ENUM等）情報に関するrecordクラス
 *
 * @param typeCategory 種別（ENUM/COMPOSITE/DOMAIN/RANGE）
 */
public record TypeEntity(
    String dbName, String schemaName, String typeName, String typeCategory, String definition)
    implements SchemaTableKeyed {

  @Override
  public String tableName() {
    return typeName;
  }
}
