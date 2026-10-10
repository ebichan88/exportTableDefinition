package com.dbxray.mcp.catalog;

import java.util.List;

/**
 * 2つのテーブルをつなぐ1つの経路
 *
 * @param tables 始点から終点まで、経路をたどる順のテーブル（関連の数より1つ多い）
 * @param relations 隣り合うテーブルをつなぐ関連（{@code tables}の順）。関連の向き（参照元・参照先）はたどる向きと一致するとは限らない
 */
public record JoinPath(List<ObjectKey> tables, List<Relation> relations) {

  /** 複製して変更できないようにする */
  public JoinPath {
    tables = List.copyOf(tables);
    relations = List.copyOf(relations);
  }
}
