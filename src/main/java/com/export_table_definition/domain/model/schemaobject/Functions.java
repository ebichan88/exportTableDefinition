package com.export_table_definition.domain.model.schemaobject;

import com.export_table_definition.domain.model.table.AbstractEntities;
import com.export_table_definition.domain.model.table.TableKey;
import java.util.List;
import java.util.Map;

/** 関数情報の集合を扱うクラス */
public final class Functions extends AbstractEntities<FunctionEntity> {
  private Functions(Map<TableKey, List<FunctionEntity>> byKey) {
    super(byKey);
  }

  /** 関数情報のリストを、所属テーブルのテーブルキーで引けるようにする */
  public static Functions of(List<FunctionEntity> list) {
    return new Functions(index(list));
  }
}
