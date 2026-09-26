package com.export_table_definition.domain.model.schemaobject;

/**
 * ユーザー定義型（ENUM等）情報に関するrecordクラス
 *
 * @param dbName データベース名
 * @param schemaName スキーマ名
 * @param typeName 型名
 * @param typeCategory 種別（ENUM/COMPOSITE/DOMAIN/RANGE）
 * @param definition 定義
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TypeEntity(
    String dbName, String schemaName, String typeName, String typeCategory, String definition) {}
