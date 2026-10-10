package com.dbxray.domain.model.table;

import java.util.List;
import java.util.Map;

/** インデックス情報の集合を扱うクラス */
final class Indexes extends AbstractEntities<IndexEntity> {
  private Indexes(Map<TableKey, List<IndexEntity>> byKey) {
    super(byKey);
  }

  /** インデックス情報のリストを、所属テーブルのテーブルキーで引けるようにする */
  public static Indexes of(List<IndexEntity> list) {
    return new Indexes(index(list));
  }
}
