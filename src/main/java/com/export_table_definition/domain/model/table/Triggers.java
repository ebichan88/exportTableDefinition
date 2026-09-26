package com.export_table_definition.domain.model.table;

import java.util.List;
import java.util.Map;

/** トリガー情報の集合を扱うクラス */
public final class Triggers extends AbstractEntities<TriggerEntity> {
  private Triggers(Map<TableKey, List<TriggerEntity>> byKey) {
    super(byKey);
  }

  public static Triggers of(List<TriggerEntity> list) {
    return new Triggers(index(list));
  }
}
