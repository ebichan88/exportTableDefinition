package com.export_table_definition.testsupport;

import com.export_table_definition.domain.model.relation.DiagramBoxes;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.relation.ForeignKeys;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.Tables;
import java.util.List;

/**
 * テストで用いる{@link DiagramBoxes}の生成ヘルパー<br>
 * 本番コードでは関連カラムをDBから取得して組み立てるため、テストでは取得結果に当たるカラムを直接渡して組み立てる
 */
public final class DiagramBoxesFixtures {

  private DiagramBoxesFixtures() {}

  /**
   * 論理テーブル名・関連カラムを1つも持たない（箱にテーブル名だけを表示する）内容を生成する
   *
   * @return 空の{@link DiagramBoxes}
   */
  public static DiagramBoxes none() {
    return DiagramBoxes.builder(Tables.of(List.of()), ForeignKeys.of(List.of())).build();
  }

  /**
   * 取得結果に当たるカラムを渡して組み立てる
   *
   * @param tables 論理テーブル名を引くテーブル
   * @param foreignKeys 関連カラムを決める関連
   * @param columns 取得したカラム（関連カラム以外を含んでよい）
   * @return 組み立てた{@link DiagramBoxes}
   */
  public static DiagramBoxes of(
      List<TableEntity> tables, List<ForeignKeyEntity> foreignKeys, List<ColumnEntity> columns) {
    return DiagramBoxes.builder(Tables.of(tables), ForeignKeys.of(foreignKeys))
        .collectRelatedColumns(columns)
        .build();
  }
}
