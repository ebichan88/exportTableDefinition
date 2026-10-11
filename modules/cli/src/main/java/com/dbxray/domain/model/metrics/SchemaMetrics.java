package com.dbxray.domain.model.metrics;

/**
 * 1スキーマ分の、出力対象の集計<br>
 * 良し悪しの判定は持たず、数の事実だけを持つ。テーブル・ビュー・マテリアライズドビューはテーブル一覧と同じく出力対象の絞り込み後の数で、
 * 関数・シーケンス・ユーザー定義型はスキーマの絞り込みだけを受ける
 *
 * @param schemaName スキーマ名。全スキーマの合計の場合は空文字
 * @param tables 区分がテーブルの数（パーティション表を含む。子のパーティションは含まない）
 * @param partitionedTables パーティション表（宣言的パーティションの親）の数
 * @param columns テーブル・ビュー・マテリアライズドビューのカラムの数
 * @param functions 関数の数（プロシージャを含まない。オーバーロードはそれぞれ数える）
 * @param procedures プロシージャの数（オーバーロードはそれぞれ数える）
 * @param triggers 出力対象のテーブルに属するトリガーの数
 * @param tablesWithLogicalName 論理名（DBのコメント）を持つテーブル・ビュー・マテリアライズドビューの数
 * @param columnsWithLogicalName 論理名（DBのコメント）を持つカラムの数
 * @param foreignKeys 物理外部キーの数（参照元のテーブルのスキーマで数える）
 * @param logicalRelations サイドカーYAMLで宣言した論理リレーションの数（参照元のテーブルのスキーマで数える）
 * @param unrelatedTables 物理外部キー・論理リレーションのどちらの端にもならないテーブル・ビュー・マテリアライズドビューの数
 * @param viewpointTables いずれかの観点に所属するテーブル・ビュー・マテリアライズドビューの数
 * @param annotatedTables サイドカーYAMLで説明・備考・カラム備考のいずれかを補ったテーブル・ビュー・マテリアライズドビューの数
 */
public record SchemaMetrics(
    String schemaName,
    int tables,
    int partitionedTables,
    int views,
    int materializedViews,
    int columns,
    int functions,
    int procedures,
    int sequences,
    int types,
    int triggers,
    int tablesWithLogicalName,
    int columnsWithLogicalName,
    int foreignKeys,
    int logicalRelations,
    int unrelatedTables,
    int viewpointTables,
    int annotatedTables) {

  /**
   * テーブル・ビュー・マテリアライズドビューの数（テーブル一覧に載る数）を取得するメソッド<br>
   * 論理名の記述状況・関連と読み解きの状況の「全体」に用いる
   */
  public int allTables() {
    return tables + views + materializedViews;
  }

  /** 2つの集計を足し合わせるメソッド（スキーマ名は空文字になる） */
  SchemaMetrics plus(SchemaMetrics other) {
    return new SchemaMetrics(
        "",
        tables + other.tables,
        partitionedTables + other.partitionedTables,
        views + other.views,
        materializedViews + other.materializedViews,
        columns + other.columns,
        functions + other.functions,
        procedures + other.procedures,
        sequences + other.sequences,
        types + other.types,
        triggers + other.triggers,
        tablesWithLogicalName + other.tablesWithLogicalName,
        columnsWithLogicalName + other.columnsWithLogicalName,
        foreignKeys + other.foreignKeys,
        logicalRelations + other.logicalRelations,
        unrelatedTables + other.unrelatedTables,
        viewpointTables + other.viewpointTables,
        annotatedTables + other.annotatedTables);
  }
}
