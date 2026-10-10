package com.dbxray.mcp.catalog;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ER図に描くテーブルと関連
 *
 * @param tables 図に描くテーブル（スナップショットに含まれるもの）
 * @param relations 図に描く関連。参照先がスナップショットに含まれないテーブルの関連を含む場合がある
 * @param missingTables 図の範囲に現れたが、スナップショットに含まれないテーブル
 */
public record DiagramScope(
    List<TableEntry> tables, List<Relation> relations, List<ObjectKey> missingTables) {

  /**
   * 図に箱として描くテーブルを返すメソッド
   *
   * @return {@code tables}の順の後に、関連の参照先のうちスナップショットに含まれないテーブルを、関連の順に重複なく並べたもの
   */
  public List<ObjectKey> nodes() {
    final Set<ObjectKey> nodes = new LinkedHashSet<>();
    tables.forEach(table -> nodes.add(table.key()));
    relations.forEach(relation -> nodes.add(relation.to()));
    return List.copyOf(nodes);
  }

  /** あるテーブルから関連をたどった結果を、そのまま図の範囲にするメソッド */
  public static DiagramScope of(RelatedTables related) {
    return new DiagramScope(
        related.tables(),
        related.relations().stream().map(RelatedTables.RelationAtDepth::relation).toList(),
        related.missingTables());
  }
}
