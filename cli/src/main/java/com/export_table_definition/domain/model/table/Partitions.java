package com.export_table_definition.domain.model.table;

import java.util.List;
import java.util.Map;

/** パーティション情報の集合を扱うクラス */
public final class Partitions extends AbstractEntities<PartitionEntity> {

  private Partitions(Map<TableKey, List<PartitionEntity>> byKey) {
    super(byKey);
  }

  /**
   * パーティション情報のリストを、所属するパーティション表（根）のテーブルキーで引けるようにする
   *
   * @param list パーティション情報のリスト（パーティション表ごとに親から子へ階層順に並べたもの）
   */
  public static Partitions of(List<PartitionEntity> list) {
    return new Partitions(index(list));
  }
}
