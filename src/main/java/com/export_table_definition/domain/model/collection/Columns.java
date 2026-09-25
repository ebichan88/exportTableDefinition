package com.export_table_definition.domain.model.collection;

import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.List;
import java.util.Map;

/**
 * カラム情報の集合を扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class Columns extends AbstractEntities<ColumnEntity> {
  private Columns(Map<TableKey, List<ColumnEntity>> byKey) {
    super(byKey);
  }

  public static Columns of(List<ColumnEntity> list) {
    return new Columns(index(list, c -> TableKey.of(c.schemaName(), c.tableName())));
  }

  /** {@inheritDoc} */
  @Override
  protected TableKey extractKey(ColumnEntity e) {
    return TableKey.of(e.schemaName(), e.tableName());
  }
}
