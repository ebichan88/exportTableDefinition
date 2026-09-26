package com.export_table_definition.domain.model.table;

import java.util.List;
import java.util.Map;

/**
 * カラム情報の集合を扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
final class Columns extends AbstractEntities<ColumnEntity> {
  private Columns(Map<TableKey, List<ColumnEntity>> byKey) {
    super(byKey);
  }

  public static Columns of(List<ColumnEntity> list) {
    return new Columns(index(list));
  }
}
