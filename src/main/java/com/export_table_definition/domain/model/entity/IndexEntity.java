package com.export_table_definition.domain.model.entity;

/**
 * インデックス情報に関するrecordクラス
 *
 * @param schemaName スキーマ名
 * @param tableName テーブル名
 * @param indexName インデックス名
 * @param indexMethod インデックスの種別（アクセスメソッド）
 * @param isUnique 一意インデックスか
 * @param isPrimary 主キーのインデックスか
 * @param indexDefinition インデックスの定義
 * @param remarks 備考
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record IndexEntity(
    String schemaName,
    String tableName,
    String indexName,
    String indexMethod,
    boolean isUnique,
    boolean isPrimary,
    String indexDefinition,
    String remarks)
    implements SchemaTableKeyed {

  /**
   * スキーマ名・テーブル名のみを持つインデックスを生成するコンストラクタ<br>
   * テーブルへの紐付けのみを参照し、表示内容を参照しない呼び出し元向けの簡易コンストラクタ
   *
   * @param schemaName スキーマ名
   * @param tableName テーブル名
   */
  public IndexEntity(String schemaName, String tableName) {
    this(schemaName, tableName, "", "", false, false, "", "");
  }
}
