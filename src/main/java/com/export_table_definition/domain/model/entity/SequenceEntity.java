package com.export_table_definition.domain.model.entity;

/**
 * シーケンス情報に関するrecordクラス
 *
 * @param dbName データベース名
 * @param schemaName スキーマ名
 * @param sequenceName シーケンス名
 * @param incrementBy 増分
 * @param minValue 最小値
 * @param maxValue 最大値
 * @param cacheSize キャッシュサイズ
 * @param startValue 開始値
 * @param cycle 循環することを表すマーカー文字列
 * @param ownedBy 所有カラム（テーブル.カラム形式）
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
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
    String cycle,
    String ownedBy) {}
