package com.dbxray.mcp.catalog;

/**
 * スナップショットの{@code types.jsonl}の1行（1ユーザー定義型）
 *
 * @param category 種別（PostgreSQLはENUM/COMPOSITE/DOMAIN/RANGE、OracleはOBJECT/VARRAY/NESTED
 *     TABLE）。未設定の場合は空文字
 * @param json スナップショットの1行そのもの
 */
public record TypeEntry(ObjectKey key, String category, String json) implements SchemaObject {

  /** 未設定の項目（null）を空文字へ揃える */
  public TypeEntry {
    category = TextValues.orEmpty(category);
  }
}
