package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.entity.SequenceEntity;

/**
 * シーケンス情報に関してORMのデータの受け渡しに利用するDTOクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record SequenceDto(String dbName, String schemaName, String sequenceName, String sequenceListInfo,
        String sequenceInfo) {

    /**
     * DTOからEntityへの変換メソッド
     *
     * @return SequenceEntityのインスタンス
     */
    public SequenceEntity toEntity() {
        return new SequenceEntity(dbName, schemaName, sequenceName, sequenceListInfo, sequenceInfo);
    }
}
