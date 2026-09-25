package com.export_table_definition.application;

import java.util.List;

/**
 * DB vs ドキュメントの差分検知（{@code --check}モード）のユースケースへの入力をまとめたrecord<br>
 * {@link ExportRequest}と異なり、Markdownの描画・ER図の生成を行わないため{@code erDiagramMaxNodes}を持たず、
 * 書き込み前の出力先削除も行わないため{@code rmDist}も持たない
 *
 * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
 * @param targetTableList テーブル定義出力対象のテーブルのリスト
 * @param outputPath 比較対象となる、既にコミット済みのドキュメントが配置されたパス
 * @param chunkSize 詳細情報をまとめて取得するテーブル数の上限。0以下の場合はスキーマ単位で分割せず取得する
 * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名のリスト。空の場合は全種別を出力対象とする
 * @param annotationPath 手動付帯情報を記述したサイドカーYAMLのパス。空・未指定の場合はマージを行わない
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record CheckDiffRequest(
    List<String> targetSchemaList,
    List<String> targetTableList,
    String outputPath,
    int chunkSize,
    List<String> outputObjectList,
    String annotationPath) {}
