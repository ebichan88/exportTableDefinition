package com.export_table_definition.domain.model.target;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.relation.ForeignKeys;
import com.export_table_definition.domain.model.sidecar.Annotations;
import com.export_table_definition.domain.model.sidecar.TableAnnotation;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.ConstraintEntity;
import com.export_table_definition.domain.model.table.IndexEntity;
import com.export_table_definition.domain.model.table.TableDetail;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.model.table.Triggers;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import com.export_table_definition.domain.model.viewpoint.Viewpoints;
import java.util.List;
import java.util.stream.Stream;

/**
 * テーブル定義出力に必要な情報をまとめたレコード<br>
 * 1テーブル分の「何を出力するか」のみを持ち、出力先（ディレクトリ）は出力形式ごとの書き込み側が持つ
 *
 * @param baseInfo データベースの基本情報
 * @param table テーブル情報
 * @param columns カラム情報のリスト
 * @param indexes インデックス情報のリスト
 * @param constraints 制約情報のリスト
 * @param foreignKeys 自テーブルが参照する関連のうち、DBに実在する外部キー制約（物理）のリスト
 * @param logicalRelations 自テーブルが参照する関連のうち、サイドカーYAMLで宣言された論理リレーションのリスト
 * @param incomingRelations 自テーブルを参照する関連（物理外部キー・論理リレーションの双方）のリスト
 * @param triggers トリガー情報のリスト
 * @param annotation 手動付帯情報
 * @param viewpoints 当該テーブルが所属する観点のリスト（宣言順。所属する観点が無い場合は空）
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
    List<ForeignKeyEntity> incomingRelations,
    List<TriggerEntity> triggers,
    TableAnnotation annotation,
    List<Viewpoint> viewpoints) {

  /**
   * テーブル定義出力に必要な情報をまとめたレコードを組み立てる<br>
   * 参照側（自テーブル → 参照先）の関連は、DBに実在する外部キー制約（{@code foreignKeys}）と サイドカーYAML由来の論理リレーション（{@code
   * logicalRelations}）に分けて保持する。 テーブル定義書では別々のセクションへ掲載し、ER図では両者を1つの図にまとめて描画するため。 被参照側（{@code
   * incomingRelations}）はER図でしか用いないため由来を分けない（物理外部キーと論理リレーションの双方を含む）
   *
   * @param baseInfo データベースの基本情報
   * @param detail 当該テーブルの詳細情報（カラム・インデックス・制約）
   * @param foreignkeys 対象範囲全体の外部キー（論理リレーションを含む。当該テーブル分を抽出して保持する）
   * @param triggers 対象範囲全体のトリガー情報（当該テーブル分を抽出して保持する）
   * @param annotations 対象範囲全体の手動付帯情報（当該テーブル分を抽出して保持する）
   * @param viewpoints サイドカーYAMLで宣言された観点（当該テーブルが所属するものを抽出して保持する）
   * @return TableDefinitionContent
   */
  public static TableDefinitionContent assemble(
      BaseInfoEntity baseInfo,
      TableDetail detail,
      ForeignKeys foreignkeys,
      Triggers triggers,
      Annotations annotations,
      Viewpoints viewpoints) {
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
        annotations.of(table),
        viewpoints.of(table));
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
