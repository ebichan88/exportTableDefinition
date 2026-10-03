package com.export_table_definition.domain.model.relation;

/**
 * 1枚のER図に描画するノード数（テーブル数）の上限を表す値オブジェクト
 *
 * @param value 上限のテーブル数。0は上限なしを表す
 */
public record NodeLimit(int value) {

  /** 上限なし（0以下の指定はすべてこの値に揃える） */
  public static final NodeLimit UNLIMITED = new NodeLimit(0);

  /** 0以下の値は上限なしへ正規化する。 */
  public NodeLimit {
    value = Math.max(value, 0);
  }

  /**
   * 設定値から上限を生成するメソッド
   *
   * @param maxNodes 1つの図に描画するノード数の上限。0以下の場合は上限なし
   */
  public static NodeLimit of(int maxNodes) {
    return new NodeLimit(maxNodes);
  }

  /**
   * ノード数が上限を超えるか判定するメソッド
   *
   * @return 上限を超える場合はtrue（上限なしの場合は常にfalse）
   */
  public boolean isExceededBy(int nodeCount) {
    return value > 0 && nodeCount > value;
  }
}
