package com.export_table_definition.domain.model.entity;

/**
 * ユーザー定義型（ENUM等）情報に関するrecordクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TypeEntity(String dbName, String schemaName, String typeName, String typeCategory, String typeListInfo,
        String definition) {
}
