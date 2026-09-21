package com.export_table_definition.domain.model.entity;

/**
 * シーケンス情報に関するrecordクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record SequenceEntity(String dbName, String schemaName, String sequenceName, String sequenceListInfo,
        String sequenceInfo) {
}
