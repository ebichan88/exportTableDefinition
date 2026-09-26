package com.export_table_definition.domain.model.table;

import java.util.List;

/**
 * トリガー情報に関するrecordクラス
 *
 * @param schemaName スキーマ名
 * @param tableName テーブル名
 * @param triggerName トリガー名
 * @param timing 実行タイミング（BEFORE/AFTER/INSTEAD OF）
 * @param events 対象イベント（INSERT/UPDATE/DELETE/TRUNCATE）のリスト
 * @param orientation 実行単位（ROW/STATEMENT）
 * @param functionName 実行される関数名（スキーマ修飾）
 * @param triggerDefinition トリガー定義
 */
public record TriggerEntity(
    String schemaName,
    String tableName,
    String triggerName,
    String timing,
    List<String> events,
    String orientation,
    String functionName,
    String triggerDefinition)
    implements SchemaTableKeyed {

  /** コンパクトコンストラクタ（対象イベントのリストは変更不可な複製として保持する） */
  public TriggerEntity {
    events = List.copyOf(events);
  }
}
