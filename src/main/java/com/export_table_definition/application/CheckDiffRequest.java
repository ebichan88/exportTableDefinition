package com.export_table_definition.application;

/**
 * DB vs ドキュメントの差分検知（{@code --check}モード）のユースケースへの入力をまとめたrecord<br>
 * {@link ExportRequest}と異なり、Markdownの描画・ER図の生成を行わないため{@code erDiagramMaxNodes}を持たず、
 * 書き込み前の出力先削除も行わないため{@code rmDist}も持たない
 *
 * @param targetSelection 出力対象の絞り込み条件（スキーマ・テーブル・outputObjects・サイドカーYAMLのパス）
 * @param outputPath 比較対象となる、既にコミット済みのドキュメントが配置されたパス
 * @param chunkSize 詳細情報をまとめて取得するテーブル数の上限。0以下の場合はスキーマ単位で分割せず取得する
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record CheckDiffRequest(TargetSelection targetSelection, String outputPath, int chunkSize) {}
