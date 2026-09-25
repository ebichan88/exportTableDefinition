package com.export_table_definition.domain.model.entity;

/**
 * 制約情報に関するrecordクラス
 *
 * @param schemaName スキーマ名
 * @param tableName テーブル名
 * @param constraintName 制約名
 * @param constraintType 制約種別（CHECK/FOREIGN KEY/PRIMARY KEY/UNIQUE）
 * @param constraintDefinition 制約定義
 * @param remarks 備考
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ConstraintEntity(
    String schemaName,
    String tableName,
    String constraintName,
    String constraintType,
    String constraintDefinition,
    String remarks)
    implements SchemaTableKeyed {

  /**
   * スキーマ名・テーブル名のみを持つ制約を生成するコンストラクタ<br>
   * テーブルへの紐付けのみを参照し、表示内容を参照しない呼び出し元向けの簡易コンストラクタ
   *
   * @param schemaName スキーマ名
   * @param tableName テーブル名
   */
  public ConstraintEntity(String schemaName, String tableName) {
    this(schemaName, tableName, "", "", "", "");
  }
}
