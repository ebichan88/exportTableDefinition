package com.export_table_definition.mcp.catalog;

/**
 * テーブルの関連の数<br>
 * 外部キーと論理リレーションを数える。自己参照と、スナップショットに含まれないテーブルとの関連は数えない
 *
 * @param incoming 自テーブルを参照しているテーブルの数。同じテーブルからの複数の関連は1つと数える
 * @param outgoing 自テーブルが参照しているテーブルの数。同じテーブルへの複数の関連は1つと数える
 * @param impact 参照元を{@value #IMPACT_DEPTH}段までたどって届くテーブルの数（変更の影響範囲）
 */
public record RelationCounts(int incoming, int outgoing, int impact) {

  /** 変更の影響範囲としてたどる段数の上限。大きなDBでは段数を増やすと共通のマスタ経由で大半のテーブルに届き、差が出なくなる */
  public static final int IMPACT_DEPTH = 3;
}
