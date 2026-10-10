package com.dbxray.mcp.catalog;

/**
 * テーブルの一覧・検索の対象を絞り込む条件<br>
 * DB・スキーマ・区分・観点の条件をすべて満たすテーブルを対象とする。呼び出しの前に1回組み立てて使い回す
 */
public final class TableFilter {

  /** 絞り込まない条件 */
  public static final TableFilter ALL = new TableFilter(SearchScope.ALL, null, null);

  private final SearchScope scope;

  /** nullの場合は区分で絞り込まない */
  private final TableType type;

  /** nullの場合は観点で絞り込まない */
  private final ViewpointEntry viewpoint;

  private TableFilter(SearchScope scope, TableType type, ViewpointEntry viewpoint) {
    this.scope = scope;
    this.type = type;
    this.viewpoint = viewpoint;
  }

  /** DB・スキーマだけで絞り込む条件を返すメソッド */
  public static TableFilter of(SearchScope scope) {
    return new TableFilter(scope, null, null);
  }

  /** 区分の条件を加えた条件を返すメソッド */
  public TableFilter withType(TableType type) {
    return new TableFilter(scope, type, viewpoint);
  }

  /** 観点の所属テーブルだけに絞り込む条件を加えた条件を返すメソッド */
  public TableFilter withViewpoint(ViewpointEntry viewpoint) {
    return new TableFilter(scope, type, viewpoint);
  }

  /** テーブルが条件に当てはまるか判定するメソッド */
  boolean matches(TableEntry table) {
    return scope.matches(table.key())
        && (type == null || type.value().equals(table.type()))
        && (viewpoint == null || viewpoint.contains(table.key()));
  }
}
