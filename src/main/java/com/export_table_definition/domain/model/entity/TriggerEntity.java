package com.export_table_definition.domain.model.entity;

/**
 * トリガー情報に関するrecordクラス
 *
 * @param schemaName スキーマ名
 * @param tableName テーブル名
 * @param triggerName トリガー名
 * @param timing 実行タイミング（BEFORE/AFTER/INSTEAD OF）
 * @param events 対象イベント（INSERT/UPDATE/DELETE/TRUNCATEを"/"区切りで連結した文字列）
 * @param orientation 実行単位（ROW/STATEMENT）
 * @param functionName 実行される関数名（スキーマ修飾）
 * @param triggerDefinition トリガー定義
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TriggerEntity(
    String schemaName,
    String tableName,
    String triggerName,
    String timing,
    String events,
    String orientation,
    String functionName,
    String triggerDefinition)
    implements SchemaTableKeyed {

  /**
   * スキーマ名・テーブル名のみを持つトリガーを生成するコンストラクタ<br>
   * テーブルへの紐付けのみを参照し、表示内容を参照しない呼び出し元向けの簡易コンストラクタ
   *
   * @param schemaName スキーマ名
   * @param tableName テーブル名
   */
  public TriggerEntity(String schemaName, String tableName) {
    this(schemaName, tableName, "", "", "", "", "", "");
  }
}
