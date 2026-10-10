package com.dbxray.domain.model.schemaobject;

/**
 * シーケンス情報に関するrecordクラス
 *
 * @param cycle 最大値（最小値）到達時に循環するか
 * @param ownedBy 所有カラム（テーブル.カラム形式）
 */
public record SequenceEntity(
    String dbName,
    String schemaName,
    String sequenceName,
    String incrementBy,
    String minValue,
    String maxValue,
    String cacheSize,
    String startValue,
    boolean cycle,
    String ownedBy) {}
