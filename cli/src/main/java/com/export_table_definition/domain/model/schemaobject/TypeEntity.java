package com.export_table_definition.domain.model.schemaobject;

/**
 * ユーザー定義型（ENUM等）情報に関するrecordクラス
 *
 * @param typeCategory 種別（PostgreSQLはENUM/COMPOSITE/DOMAIN/RANGE、OracleはOBJECT/VARRAY/NESTED TABLE）
 */
public record TypeEntity(
    String dbName, String schemaName, String typeName, String typeCategory, String definition) {}
