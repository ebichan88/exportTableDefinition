package com.dbxray.mcp.catalog;

import java.util.Comparator;

/**
 * スキーマに属するオブジェクト（テーブル・関数・シーケンス・ユーザー定義型）を識別するキー<br>
 * 関数のオーバーロードは同じキーになる
 *
 * @param database DB名（スナップショットの{@code {DB名}}ディレクトリ）
 */
public record ObjectKey(String database, String schema, String name) {

  /** 一覧・候補を並べる順（DB名・スキーマ名・オブジェクト名） */
  static final Comparator<ObjectKey> ORDER =
      Comparator.comparing(ObjectKey::database)
          .thenComparing(ObjectKey::schema)
          .thenComparing(ObjectKey::name);

  /**
   * スキーマ修飾した名前を返すメソッド
   *
   * @return {@code スキーマ名.オブジェクト名}
   */
  public String qualifiedName() {
    return schema + "." + name;
  }
}
