package com.export_table_definition.domain.model.relation;

import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.TableKey;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ER図のテーブルの箱に表示するカラム
 *
 * @param foreignKey 図に描画する関連の参照元（子）として使われるカラムか
 */
public record DiagramColumn(ColumnEntity column, boolean foreignKey) {

  /**
   * テーブルのカラムに、図に描画する関連の参照元（子）として使われるかを添えるメソッド
   *
   * @param columns 当該テーブルのカラム（表示順）
   * @param drawnRelations 図に描画する関連（当該テーブルが参照元でない関連は無視する）
   * @return {@code columns}と同じ順
   */
  public static List<DiagramColumn> of(
      TableKey table, List<ColumnEntity> columns, Collection<ForeignKeyEntity> drawnRelations) {
    final Set<String> foreignKeyNames =
        drawnRelations.stream()
            .filter(fk -> fk.tableKey().equals(table))
            .flatMap(fk -> fk.columnNames().stream())
            .collect(Collectors.toSet());
    return of(columns, foreignKeyNames);
  }

  /**
   * テーブルのカラムに、外部キーのカラムかを添えるメソッド
   *
   * @param foreignKeyNames 図に描画する関連の参照元（子）として使われるカラムの名前
   */
  static List<DiagramColumn> of(List<ColumnEntity> columns, Set<String> foreignKeyNames) {
    return columns.stream()
        .map(
            column ->
                new DiagramColumn(column, foreignKeyNames.contains(column.physicalColumnName())))
        .toList();
  }
}
