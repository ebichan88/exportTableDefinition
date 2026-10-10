package com.dbxray.mcp.catalog;

/** カラムを名前（物理名・論理名）で探す条件 */
public final class ColumnQuery {

  private static final int NAME_EXACT = 4;
  private static final int LOGICAL_NAME_EXACT = 3;
  private static final int NAME_PREFIX = 2;
  private static final int PARTIAL = 1;

  private final String term;
  private final MatchMode mode;

  private ColumnQuery(String term, MatchMode mode) {
    this.term = term;
    this.mode = mode;
  }

  /**
   * 条件を組み立てるメソッド
   *
   * @param name カラムの物理名または論理名
   * @throws IllegalArgumentException 名前が空の場合
   */
  public static ColumnQuery of(String name, MatchMode mode) {
    final String term = TextValues.normalize(name).strip();
    if (term.isEmpty()) {
      throw new IllegalArgumentException("カラム名が空です。");
    }
    return new ColumnQuery(term, mode);
  }

  /**
   * カラムが条件に当てはまる強さを求めるメソッド
   *
   * @return 当てはまらない場合は0。物理名の完全一致＞論理名の完全一致＞物理名の前方一致＞部分一致の順に大きい
   */
  int score(ColumnEntry column) {
    final String name = TextValues.normalize(column.name());
    final String logicalName = TextValues.normalize(column.logicalName());
    if (name.equals(term)) {
      return NAME_EXACT;
    }
    if (logicalName.equals(term)) {
      return LOGICAL_NAME_EXACT;
    }
    if (mode == MatchMode.EXACT) {
      return 0;
    }
    if (name.startsWith(term)) {
      return NAME_PREFIX;
    }
    return name.contains(term) || logicalName.contains(term) ? PARTIAL : 0;
  }
}
