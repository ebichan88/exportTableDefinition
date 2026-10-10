package com.dbxray.domain.model.table;

import java.util.List;
import java.util.Map;

/** カラム情報の集合を扱うクラス */
final class Columns extends AbstractEntities<ColumnEntity> {
  private Columns(Map<TableKey, List<ColumnEntity>> byKey) {
    super(byKey);
  }

  /** カラム情報のリストを、所属テーブルのテーブルキーで引けるようにする */
  public static Columns of(List<ColumnEntity> list) {
    return new Columns(index(list));
  }
}
