package com.dbxray.domain.model.table;

import java.util.List;

/**
 * トリガー情報に関するrecordクラス
 *
 * @param timing 実行タイミング（BEFORE/AFTER/INSTEAD OF）
 * @param events 対象イベント（INSERT/UPDATE/DELETE/TRUNCATE）のリスト
 * @param orientation 実行単位（ROW/STATEMENT）
 * @param functionName 実行される関数名（スキーマ修飾）
 * @param triggerDefinition 定義。Oracleは宣言部（{@code CREATE OR REPLACE TRIGGER ...}とWHEN句）だけで、本体を含まない
 * @param body 本体（Oracleのみ。トリガーの中に書いたPL/SQL）。PostgreSQLは本体を実行する関数が持つため空文字
 */
public record TriggerEntity(
    String schemaName,
    String tableName,
    String triggerName,
    String timing,
    List<String> events,
    String orientation,
    String functionName,
    String triggerDefinition,
    String body)
    implements SchemaTableKeyed {

  /** 対象イベントのリストは変更不可な複製として保持する */
  public TriggerEntity {
    events = List.copyOf(events);
  }
}
