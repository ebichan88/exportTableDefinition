package com.export_table_definition.domain.model.collection;

import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.List;
import java.util.Map;

/**
 * トリガー情報の集合を扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class Triggers extends AbstractEntities<TriggerEntity> {
  private Triggers(Map<TableKey, List<TriggerEntity>> byKey) {
    super(byKey);
  }

  public static Triggers of(List<TriggerEntity> list) {
    return new Triggers(index(list, c -> TableKey.of(c.schemaName(), c.tableName())));
  }

  /** {@inheritDoc} */
  @Override
  protected TableKey extractKey(TriggerEntity e) {
    return TableKey.of(e.schemaName(), e.tableName());
  }
}
