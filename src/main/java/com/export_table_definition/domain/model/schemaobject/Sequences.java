package com.export_table_definition.domain.model.schemaobject;

import com.export_table_definition.domain.model.table.AbstractEntities;
import com.export_table_definition.domain.model.table.TableKey;
import java.util.List;
import java.util.Map;

/** シーケンス情報の集合を扱うクラス */
public final class Sequences extends AbstractEntities<SequenceEntity> {
  private Sequences(Map<TableKey, List<SequenceEntity>> byKey) {
    super(byKey);
  }

  /** シーケンス情報のリストを、所属テーブルのテーブルキーで引けるようにする */
  public static Sequences of(List<SequenceEntity> list) {
    return new Sequences(index(list));
  }
}
