package com.export_table_definition.domain.model.collection;

import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.List;
import java.util.Map;

/**
 * 制約情報の集合を扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class Constraints extends AbstractEntities<ConstraintEntity> {
  private Constraints(Map<TableKey, List<ConstraintEntity>> byKey) {
    super(byKey);
  }

  public static Constraints of(List<ConstraintEntity> list) {
    return new Constraints(index(list, c -> TableKey.of(c.schemaName(), c.tableName())));
  }

  /** {@inheritDoc} */
  @Override
  protected TableKey extractKey(ConstraintEntity e) {
    return TableKey.of(e.schemaName(), e.tableName());
  }
}
