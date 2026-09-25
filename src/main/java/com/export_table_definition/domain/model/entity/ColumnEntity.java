package com.export_table_definition.domain.model.entity;

/**
 * カラム情報に関するrecordクラス
 *
 * @param schemaName スキーマ名
 * @param tableName テーブル名
 * @param logicalColumnName 論理カラム名
 * @param physicalColumnName 物理カラム名
 * @param columnType データ型
 * @param precisionScale 桁数/精度
 * @param primaryKey 主キーを構成するカラムか
 * @param notNull NOT NULL制約を持つか
 * @param defaultValue デフォルト値
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ColumnEntity(
    String schemaName,
    String tableName,
    String logicalColumnName,
    String physicalColumnName,
    String columnType,
    String precisionScale,
    boolean primaryKey,
    boolean notNull,
    String defaultValue)
    implements SchemaTableKeyed {

  /**
   * 論理カラム名・桁数/精度・NOT NULL・デフォルト値を持たないカラムを生成するコンストラクタ<br>
   * これらの表示専用属性を参照しない呼び出し元向けの簡易コンストラクタ
   *
   * @param schemaName スキーマ名
   * @param tableName テーブル名
   * @param physicalColumnName 物理カラム名
   * @param columnType データ型
   * @param primaryKey 主キーを構成するカラムか
   */
  public ColumnEntity(
      String schemaName,
      String tableName,
      String physicalColumnName,
      String columnType,
      boolean primaryKey) {
    this(schemaName, tableName, "", physicalColumnName, columnType, "", primaryKey, false, "");
  }
}
