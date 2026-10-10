package com.dbxray.mcp.catalog;

/**
 * スナップショットの{@code sequences.jsonl}の1行（1シーケンス）
 *
 * @param ownedBy 所有カラム（{@code テーブル名.カラム名}。テーブルはシーケンスと同じスキーマ）。未設定の場合は空文字
 * @param json スナップショットの1行そのもの
 */
public record SequenceEntry(ObjectKey key, String ownedBy, String json) implements SchemaObject {

  /** 未設定の項目（null）を空文字へ揃える */
  public SequenceEntry {
    ownedBy = TextValues.orEmpty(ownedBy);
  }
}
