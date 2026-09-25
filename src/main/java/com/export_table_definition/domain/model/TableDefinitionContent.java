package com.export_table_definition.domain.model;

import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.collection.Columns;
import com.export_table_definition.domain.model.collection.Constraints;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.collection.Indexes;
import com.export_table_definition.domain.model.collection.Triggers;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * テーブル定義出力に必要な情報をまとめたレコード
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TableDefinitionContent(
    BaseInfoEntity baseInfo,
    TableEntity table,
    List<ColumnEntity> columns,
    List<IndexEntity> indexes,
    List<ConstraintEntity> constraints,
    List<ForeignKeyEntity> foreignKeys,
    List<ForeignKeyEntity> logicalRelations,
    List<ForeignKeyEntity> incomingForeignKeys,
    List<TriggerEntity> triggers,
    TableAnnotation annotation,
    Path outputBaseDir) {

  /**
   * テーブル定義出力に必要な情報をまとめたレコードを組み立てる<br>
   * 参照側（自テーブル → 参照先）の関連は、DBに実在する外部キー制約（{@code foreignKeys}）と サイドカーYAML由来の論理リレーション（{@code
   * logicalRelations}）に分けて保持する。 テーブル定義書では別々のセクションへ掲載し、ER図では両者を1つの図にまとめて描画するため。 被参照側（{@code
   * incomingForeignKeys}）はER図でしか用いないため由来を分けない
   *
   * @param baseInfo
   * @param table
   * @param columns
   * @param indexes
   * @param constraints
   * @param foreignkeys
   * @param triggers
   * @param annotations 対象範囲全体の手動付帯情報（当該テーブル分を抽出して保持する）
   * @param baseDir
   * @return TableDefinitionContent
   */
  public static TableDefinitionContent assemble(
      BaseInfoEntity baseInfo,
      TableEntity table,
      Columns columns,
      Indexes indexes,
      Constraints constraints,
      ForeignKeys foreignkeys,
      Triggers triggers,
      Annotations annotations,
      Path baseDir) {
    return new TableDefinitionContent(
        baseInfo,
        table,
        columns.of(table),
        indexes.of(table),
        constraints.of(table),
        foreignkeys.physicalOf(table),
        foreignkeys.logicalOf(table),
        foreignkeys.incomingOf(table),
        triggers.of(table),
        annotations.of(table),
        baseDir);
  }

  /**
   * ER図に描画する参照側（自テーブル → 参照先）の関連をまとめて取得するメソッド<br>
   * 物理・論理の区別は線種で表現するため、描画対象としては1つのリストに束ねる
   *
   * @return 物理外部キーと論理リレーションを結合したリスト
   */
  public List<ForeignKeyEntity> outgoingRelations() {
    return Stream.concat(foreignKeys.stream(), logicalRelations.stream()).toList();
  }
}
