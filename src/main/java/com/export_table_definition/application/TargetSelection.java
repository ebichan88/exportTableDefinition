package com.export_table_definition.application;

import java.util.List;

/**
 * 出力対象の絞り込み条件をまとめたrecord<br>
 * {@link ExportRequest}・{@link CheckDiffRequest}の双方が持つスキーマ・テーブル・出力対象オブジェクト種別・
 * 手動付帯情報パスの4条件をまとめ、エントリーポイント→コントローラー→ユースケースの3層を 分解・再構築せずそのまま通過させる
 *
 * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
 * @param targetTableList テーブル定義出力対象のテーブルのリスト
 * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名（{@link
 *     com.export_table_definition.domain.model.type.OutputObjectType#getName()}）のリスト。
 *     空の場合は全種別を出力対象とする
 * @param annotationPath 手動付帯情報（テーブル説明・テーブル備考・カラム備考）を記述したサイドカーYAMLのパス。 空・未指定の場合はマージを行わない
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TargetSelection(
    List<String> targetSchemaList,
    List<String> targetTableList,
    List<String> outputObjectList,
    String annotationPath) {}
