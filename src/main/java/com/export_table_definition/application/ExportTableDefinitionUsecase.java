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
     */
    public void exportTableDefinition(List<String> targetSchemaList, List<String> targetTableList, String outputPath,
            int chunkSize);
}
