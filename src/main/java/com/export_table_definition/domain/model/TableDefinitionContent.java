package com.export_table_definition.domain.model;

import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.collection.Triggers;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import java.util.List;
import java.util.stream.Stream;

/**
 * テーブル定義出力に必要な情報をまとめたレコード<br>
 * 1テーブル分の「何を出力するか」のみを持ち、出力先（ディレクトリ）は出力形式ごとの書き込み側が持つ
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
    TableAnnotation annotation) {

  /**
   * テーブル定義出力に必要な情報をまとめたレコードを組み立てる<br>
   * 参照側（自テーブル → 参照先）の関連は、DBに実在する外部キー制約（{@code foreignKeys}）と サイドカーYAML由来の論理リレーション（{@code
   * logicalRelations}）に分けて保持する。 テーブル定義書では別々のセクションへ掲載し、ER図では両者を1つの図にまとめて描画するため。 被参照側（{@code
   * incomingForeignKeys}）はER図でしか用いないため由来を分けない
   *
   * @param baseInfo データベースの基本情報
   * @param detail 当該テーブルの詳細情報（カラム・インデックス・制約）
   * @param foreignkeys 対象範囲全体の外部キー（論理リレーションを含む。当該テーブル分を抽出して保持する）
   * @param triggers 対象範囲全体のトリガー情報（当該テーブル分を抽出して保持する）
   * @param annotations 対象範囲全体の手動付帯情報（当該テーブル分を抽出して保持する）
   * @return TableDefinitionContent
   */
  public static TableDefinitionContent assemble(
      BaseInfoEntity baseInfo,
      TableDetail detail,
      ForeignKeys foreignkeys,
      Triggers triggers,
      Annotations annotations) {
    final TableEntity table = detail.table();
    return new TableDefinitionContent(
        baseInfo,
        table,
        detail.columns(),
        detail.indexes(),
        detail.constraints(),
        foreignkeys.physicalOf(table),
        foreignkeys.logicalOf(table),
        foreignkeys.incomingOf(table),
        triggers.of(table),
        annotations.of(table));
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
