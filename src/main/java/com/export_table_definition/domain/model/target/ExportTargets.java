package com.export_table_definition.domain.model.target;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.relation.ForeignKeys;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.model.sidecar.Annotations;
import com.export_table_definition.domain.model.table.Tables;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.model.viewpoint.Viewpoints;
import java.util.List;

/**
 * 出力対象のうち、一括取得する軽量な情報の組<br>
 * テーブル数に比例して重くなる詳細情報（カラム・インデックス・制約）と関数の定義本体は含まない。 それらは出力時にスキーマ・チャンク単位で取得する
 *
 * @param baseInfo データベースの基本情報
 * @param tables 出力対象のテーブル情報のリスト（テーブルの絞り込み済み）
 * @param foreignKeys 出力対象のテーブル同士の外部キー（論理リレーションを含む）
 * @param triggers 対象範囲全体のトリガー情報のリスト
 * @param functions 関数・プロシージャの一覧情報（定義本体を含まない）のリスト
 * @param sequences シーケンス情報のリスト
 * @param types ユーザー定義型情報のリスト
 * @param annotations 対象範囲全体の手動付帯情報
 * @param viewpoints サイドカーYAMLで宣言された観点
 */
public record ExportTargets(
    BaseInfoEntity baseInfo,
    Tables tables,
    ForeignKeys foreignKeys,
    List<TriggerEntity> triggers,
    List<FunctionEntity> functions,
    List<SequenceEntity> sequences,
    List<TypeEntity> types,
    Annotations annotations,
    Viewpoints viewpoints) {}
