package com.dbxray.mcp.catalog;

import java.util.List;

/**
 * 関連でつながるテーブルのまとまり1つ（関連から推測したもので、人が宣言した観点ではない）
 *
 * @param tables まとまりに属するテーブル。被参照のテーブル数の多い順（同数は名前の順）で、先頭を代表のテーブルとする
 * @param hubs まとまりのテーブルと関連を持つ、まとまりを分けるために除いた共通のテーブル（被参照の多い順）
 */
public record TableCluster(List<TableEntry> tables, List<TableEntry> hubs) {

  /** 複製して保持する */
  public TableCluster {
    tables = List.copyOf(tables);
    hubs = List.copyOf(hubs);
  }

  /** 代表のテーブル（まとまりの中で被参照のテーブル数が最も多いもの）を返すメソッド */
  public TableEntry representative() {
    return tables.get(0);
  }
}
