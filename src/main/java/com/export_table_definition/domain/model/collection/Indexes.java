package com.export_table_definition.domain.model.collection;

import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.List;
import java.util.Map;

/**
 * インデックス情報の集合を扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class Indexes extends AbstractEntities<IndexEntity> {
  private Indexes(Map<TableKey, List<IndexEntity>> byKey) {
    super(byKey);
  }

  public static Indexes of(List<IndexEntity> list) {
    return new Indexes(index(list));
  }
}
