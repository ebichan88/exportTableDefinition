package com.export_table_definition.application;

import java.util.List;

/**
 * テーブル定義出力のユースケースを扱うインターフェース
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface ExportTableDefinitionUsecase {

    /**
     * テーブル定義出力のユースケースを扱うメソッド
     *
     * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
     * @param targetTableList  テーブル定義出力対象のテーブルのリスト
     * @param outputPath       テーブル定義出力の出力先のパス
     * @param chunkSize        詳細情報（カラム・インデックス・制約・外部キー）をまとめて取得するテーブル数の上限。
     *                         0以下の場合はスキーマ単位で分割せず取得する
     * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限。超過した場合はER図の代わりに
     *                          外部キーの一覧表を出力する。0以下の場合は上限なし
     * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名（{@link com.export_table_definition.domain.model.type.OutputObjectType#getName()}）のリスト。
     *                         空の場合は全種別を出力対象とする
     */
    public void exportTableDefinition(List<String> targetSchemaList, List<String> targetTableList, String outputPath,
            int chunkSize, int erDiagramMaxNodes, List<String> outputObjectList);
}
