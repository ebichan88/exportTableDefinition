package com.export_table_definition.mcp.catalog;

/** 一覧を名前の部分一致（大文字小文字・全角半角を区別しない）で絞り込む条件 */
public final class NameFilter {

  /** 絞り込まない条件 */
  public static final NameFilter ALL = new NameFilter("");

  private final String term;

  private NameFilter(String term) {
    this.term = term;
  }

  /**
   * 条件を組み立てるメソッド
   *
   * @param name 名前の一部。空文字の場合は絞り込まない
   */
  public static NameFilter of(String name) {
    return new NameFilter(TextValues.normalize(name).strip());
  }

  /** オブジェクトの名前が条件に当てはまるか判定するメソッド */
  boolean matches(ObjectKey key) {
    return term.isEmpty() || TextValues.normalize(key.name()).contains(term);
  }
}
