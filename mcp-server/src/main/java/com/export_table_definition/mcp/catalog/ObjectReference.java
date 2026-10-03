package com.export_table_definition.mcp.catalog;

/**
 * 名前で指定されたオブジェクト（AIがツールの引数に渡した名前）
 *
 * @param scope DB名・スキーマ名による絞り込み
 * @param name オブジェクト名（大文字小文字を区別しない）
 */
public record ObjectReference(SearchScope scope, String name) {

  /**
   * ツールの引数からオブジェクトの指定を組み立てるメソッド<br>
   * スキーマ名が未指定で、名前が{@code スキーマ名.オブジェクト名}の形の場合は、スキーマ修飾とみなして分ける
   *
   * @param database DB名。未指定の場合はnullまたは空文字
   * @param schema スキーマ名。未指定の場合はnullまたは空文字
   */
  public static ObjectReference of(String database, String schema, String name) {
    final String schemaName = TextValues.orEmpty(schema);
    final int dot = name.indexOf('.');
    if (schemaName.isEmpty() && dot > 0 && dot < name.length() - 1) {
      return new ObjectReference(
          new SearchScope(database, name.substring(0, dot)), name.substring(dot + 1));
    }
    return new ObjectReference(new SearchScope(database, schemaName), name);
  }

  /** オブジェクトが指定に当てはまるか判定するメソッド */
  boolean matches(ObjectKey key) {
    return scope.matches(key) && name.equalsIgnoreCase(key.name());
  }
}
