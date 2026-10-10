package com.dbxray.mcp.catalog;

import java.util.List;

/**
 * あるテーブルから関連をたどった結果
 *
 * @param start たどり始めたテーブル
 * @param relations たどった関連。見つけた順（{@code depth}の昇順）に重複なく並ぶ
 * @param tables 関連に現れたテーブル（たどり始めたテーブルを含む）。スナップショットに含まれないテーブルは{@code missingTables}に分ける
 * @param missingTables 関連の参照先として現れたが、スナップショットに含まれないテーブル
 */
public record RelatedTables(
    TableEntry start,
    List<RelationAtDepth> relations,
    List<TableEntry> tables,
    List<ObjectKey> missingTables) {

  /**
   * たどった関連と、たどり始めたテーブルからの距離
   *
   * @param depth たどり始めたテーブルから何段目で見つけた関連か（1始まり）
   */
  public record RelationAtDepth(Relation relation, int depth) {}
}
