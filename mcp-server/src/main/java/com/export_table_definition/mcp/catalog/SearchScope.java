package com.export_table_definition.mcp.catalog;

/**
 * 検索・名前の解決の対象を、DB・スキーマで絞り込む条件
 *
 * @param database 対象のDB名。空文字の場合は全DBが対象
 * @param schema 対象のスキーマ名。空文字の場合は全スキーマが対象
 */
public record SearchScope(String database, String schema) {

  /** 絞り込まない条件 */
  public static final SearchScope ALL = new SearchScope("", "");

  /** 未指定（null）を空文字へ揃える */
  public SearchScope {
    database = TextValues.orEmpty(database);
    schema = TextValues.orEmpty(schema);
  }

  /** テーブルが絞り込みの条件に当てはまるか判定するメソッド（DB名・スキーマ名は大文字小文字を区別しない） */
  public boolean matches(ObjectKey key) {
    return (database.isEmpty() || database.equalsIgnoreCase(key.database()))
        && (schema.isEmpty() || schema.equalsIgnoreCase(key.schema()));
  }
}
