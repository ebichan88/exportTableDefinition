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
 * @param primaryKey 主キーであることを表すマーカー文字列（{@link #isPrimaryKey()}参照）
 * @param notNull NOT NULL制約であることを表すマーカー文字列
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
    String primaryKey,
    String notNull,
    String defaultValue)
    implements SchemaTableKeyed {

  /** 主キー・NOT NULL制約であることを表すマーカー文字列 */
  private static final String MARKER = "○";

  /**
   * 論理カラム名・桁数/精度・NOT NULL・デフォルト値を持たないカラムを生成するコンストラクタ<br>
   * これらの表示専用属性を参照しない呼び出し元向けの簡易コンストラクタ
   *
   * @param schemaName スキーマ名
   * @param tableName テーブル名
   * @param physicalColumnName 物理カラム名
   * @param columnType データ型
   * @param primaryKey 主キーであることを表すマーカー文字列
   */
  public ColumnEntity(
      String schemaName,
      String tableName,
      String physicalColumnName,
      String columnType,
      String primaryKey) {
    this(schemaName, tableName, "", physicalColumnName, columnType, "", primaryKey, "", "");
  }

  /**
   * 主キーであるか判定するメソッド
   *
   * @return 主キーの場合はtrue。それ以外の場合はfalse
   */
  public boolean isPrimaryKey() {
    return MARKER.equals(primaryKey);
  }

  /**
   * NOT NULL制約を持つか判定するメソッド
   *
   * @return NOT NULL制約を持つ場合はtrue。それ以外の場合はfalse
   */
  public boolean isNotNull() {
    return MARKER.equals(notNull);
  }
}
