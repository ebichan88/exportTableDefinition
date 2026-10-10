package com.dbxray.mcp.catalog;

import java.util.List;

/**
 * テーブルのトリガーのうち、一覧・関数からの逆引きに使う項目
 *
 * @param timing 実行タイミング（BEFORE/AFTER/INSTEAD OF）。未設定の場合は空文字
 * @param events 対象イベント（INSERT/UPDATE/DELETE/TRUNCATE）
 * @param orientation 実行単位（ROW/STATEMENT）。未設定の場合は空文字
 * @param function 実行される関数名（{@code スキーマ名.関数名}）。未設定の場合は空文字
 */
public record TriggerEntry(
    String name, String timing, List<String> events, String orientation, String function) {

  /** 未設定の項目（null）を空文字・空リストへ揃える */
  public TriggerEntry {
    timing = TextValues.orEmpty(timing);
    events = events == null ? List.of() : List.copyOf(events);
    orientation = TextValues.orEmpty(orientation);
    function = TextValues.orEmpty(function);
  }
}
