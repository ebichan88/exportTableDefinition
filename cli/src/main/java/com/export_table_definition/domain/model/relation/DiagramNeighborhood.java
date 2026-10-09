package com.export_table_definition.domain.model.relation;

import java.util.List;

/**
 * テーブル定義書のER図に描く、1つのテーブルの周りの関連<br>
 * テーブルから関連を向きを問わずたどり、描画距離（段数）以内のテーブルが持つ関連を描く
 *
 * @param relations 描く関連（自テーブルに近い順。同じテーブルの関連は、参照先へ向かう関連・参照元から来る関連の順）
 * @param distance 描いた距離。図のテーブル数が上限（{@code erDiagramMaxNodes}）を超えないよう、指定より縮めた場合がある
 * @param requestedDistance 指定された描画距離
 */
public record DiagramNeighborhood(
    List<ForeignKeyEntity> relations, int distance, int requestedDistance) {

  /** 防御的コピーを作成する。 */
  public DiagramNeighborhood {
    relations = List.copyOf(relations);
  }

  /** 図のテーブル数が上限を超えるため、指定より短い距離で描いたか判定するメソッド */
  public boolean isShortened() {
    return distance < requestedDistance;
  }
}
