package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.entity.ForeignKeyEntity;

/**
 * 外部キー情報に関してORMのデータの受け渡しに利用するDTOクラス
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ForeignKeyDto(String schemaName, String tableName, String foreignkeyInfo, String foreignkeyName,
        String referenceSchemaName, String referenceTableName) {

    /**
     * DTOからEntityへの変換メソッド
     *
     * @return AllForeignkeyEntityのインスタンス
     */
    public ForeignKeyEntity toEntity() {
        return new ForeignKeyEntity(schemaName, tableName, foreignkeyInfo, foreignkeyName, referenceSchemaName,
                referenceTableName);
    }
}
