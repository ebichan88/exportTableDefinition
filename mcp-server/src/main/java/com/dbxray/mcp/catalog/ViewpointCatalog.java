package com.dbxray.mcp.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 観点の参考情報の一覧・解決と、テーブルから所属する観点の逆引きを担うクラス<br>
 * 観点はスキーマを持たないため、{@link SearchScope}の{@code schema}は無視する
 */
public final class ViewpointCatalog {

  private final List<ViewpointEntry> viewpoints;

  ViewpointCatalog(List<ViewpointEntry> viewpoints) {
    this.viewpoints = List.copyOf(viewpoints);
  }

  /**
   * 観点を一覧にするメソッド
   *
   * @return 宣言順
   */
  public List<ViewpointEntry> list(SearchScope scope) {
    return viewpoints.stream()
        .filter(viewpoint -> scope.matchesDatabase(viewpoint.database()))
        .toList();
  }

  /** 識別子で観点を解決するメソッド（識別子は大文字小文字を区別しない） */
  public Optional<ViewpointEntry> find(SearchScope scope, String id) {
    return list(scope).stream()
        .filter(viewpoint -> viewpoint.id().equalsIgnoreCase(id))
        .findFirst();
  }

  /**
   * テーブルが所属する観点を求めるメソッド<br>
   * 1つのテーブルが複数の観点に所属することがある
   *
   * @return 宣言順。所属する観点が無い場合は空のリスト
   */
  public List<ViewpointEntry> containing(TableEntry table) {
    return viewpoints.stream().filter(viewpoint -> viewpoint.contains(table.key())).toList();
  }
}
