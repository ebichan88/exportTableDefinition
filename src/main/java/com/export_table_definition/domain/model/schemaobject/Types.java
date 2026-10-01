package com.export_table_definition.domain.model.schemaobject;

import com.export_table_definition.domain.model.table.AbstractEntities;
import com.export_table_definition.domain.model.table.TableKey;
import java.util.List;
import java.util.Map;

/** 型情報の集合を扱うクラス */
public final class Types extends AbstractEntities<TypeEntity> {
  private Types(Map<TableKey, List<TypeEntity>> byKey) {
    super(byKey);
  }

  /** 型情報のリストを、所属テーブルのテーブルキーで引けるようにする */
  public static Types of(List<TypeEntity> list) {
    return new Types(index(list));
  }
}
