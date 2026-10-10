package com.dbxray.mcp.catalog;

/** スキーマに属し、名前で指定できるオブジェクト */
public interface SchemaObject {

  /**
   * オブジェクトを識別するキーを返すメソッド
   *
   * @return DB名・スキーマ名・オブジェクト名
   */
  ObjectKey key();
}
