package com.export_table_definition.mcp.catalog;

/** テーブルの一覧の並べ方。関連の数で並べる場合、同数のテーブルはDB名・スキーマ名・テーブル名の順にする */
public enum TableOrder {
  /** DB名・スキーマ名・テーブル名の順 */
  NAME,
  /** 参照元のテーブルの多い順（{@link RelationCounts#incoming()}） */
  INCOMING,
  /** 参照先のテーブルの多い順（{@link RelationCounts#outgoing()}） */
  OUTGOING,
  /** 変更の影響範囲の大きい順（{@link RelationCounts#impact()}） */
  IMPACT
}
