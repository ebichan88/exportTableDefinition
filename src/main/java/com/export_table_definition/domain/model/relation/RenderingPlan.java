package com.export_table_definition.domain.model.relation;

/**
 * 外部キーのまとまりをER図として描画するか、描画を省略して外部キー一覧にフォールバックするかを表す、描き方の計画<br>
 * 上限との比較は{@link ForeignKeyGroup#planRendering}が1回だけ行い、Writer・テンプレートはこの結果に従う
 */
public sealed interface RenderingPlan {

  /** 計画の対象となった外部キーのまとまり */
  ForeignKeyGroup group();

  /**
   * ER図として描画する（外部キーが無く、描画するものが無い場合を含む）
   *
   * @param group 描画する外部キーのまとまり
   */
  record Draw(ForeignKeyGroup group) implements RenderingPlan {}

  /**
   * ノード数が上限を超えるため描画を省略し、代わりに外部キー一覧を掲載する
   *
   * @param group 描画を省略する外部キーのまとまり
   * @param limit 超過した上限。省略メッセージに表示する
   */
  record Omit(ForeignKeyGroup group, NodeLimit limit) implements RenderingPlan {}
}
