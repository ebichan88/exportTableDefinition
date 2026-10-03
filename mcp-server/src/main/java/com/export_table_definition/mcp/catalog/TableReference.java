package com.export_table_definition.mcp.catalog;

/**
 * 名前で指定されたテーブル（AIがツールの引数に渡した名前）
 *
 * @param scope DB名・スキーマ名による絞り込み
 * @param table テーブル名（大文字小文字を区別しない）
 */
public record TableReference(SearchScope scope, String table) {

  /**
   * ツールの引数からテーブルの指定を組み立てるメソッド<br>
   * スキーマ名が未指定で、テーブル名が{@code スキーマ名.テーブル名}の形の場合は、スキーマ修飾とみなして分ける
   *
   * @param database DB名。未指定の場合はnullまたは空文字
   * @param schema スキーマ名。未指定の場合はnullまたは空文字
   */
  public static TableReference of(String database, String schema, String table) {
    final String schemaName = TextValues.orEmpty(schema);
    final int dot = table.indexOf('.');
    if (schemaName.isEmpty() && dot > 0 && dot < table.length() - 1) {
      return new TableReference(
          new SearchScope(database, table.substring(0, dot)), table.substring(dot + 1));
    }
    return new TableReference(new SearchScope(database, schemaName), table);
  }

  /** テーブルが指定に当てはまるか判定するメソッド */
  boolean matches(TableKey key) {
    return scope.matches(key) && table.equalsIgnoreCase(key.name());
  }
}
