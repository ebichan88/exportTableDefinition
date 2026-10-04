package com.export_table_definition.domain.model.target;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.relation.ForeignKeys;
import com.export_table_definition.domain.model.schemaobject.Functions;
import com.export_table_definition.domain.model.schemaobject.Sequences;
import com.export_table_definition.domain.model.schemaobject.Types;
import com.export_table_definition.domain.model.sidecar.Annotations;
import com.export_table_definition.domain.model.table.Partitions;
import com.export_table_definition.domain.model.table.Tables;
import com.export_table_definition.domain.model.table.Triggers;
import com.export_table_definition.domain.model.viewpoint.Viewpoints;

/**
 * 出力対象のうち、一括取得する軽量な情報の組<br>
 * テーブル数に比例して重くなる詳細情報（カラム・インデックス・制約）と関数の定義本体は含まない。 それらは出力時にスキーマ・チャンク単位で取得する
 *
 * @param tables 出力対象のテーブル情報のリスト（テーブルの絞り込み済み）
 * @param foreignKeys 出力対象のテーブル同士の外部キー（論理リレーションを含む）
 * @param triggers 対象範囲全体のトリガー情報
 * @param partitions 対象範囲全体のパーティション表の下位のパーティション（テーブルには含まれない）
 * @param functions 関数・プロシージャの一覧情報（定義本体を含まない）
 * @param annotations 対象範囲全体の手動付帯情報
 * @param viewpoints サイドカーYAMLで宣言された観点
 */
public record ExportTargets(
    BaseInfoEntity baseInfo,
    Tables tables,
    ForeignKeys foreignKeys,
    Triggers triggers,
    Partitions partitions,
    Functions functions,
    Sequences sequences,
    Types types,
    Annotations annotations,
    Viewpoints viewpoints) {}
