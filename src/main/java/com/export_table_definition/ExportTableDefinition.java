package com.export_table_definition;

import java.util.List;

import com.export_table_definition.config.PropertyLoader;
import com.export_table_definition.config.module.ExportTableDefinitionModule;
import com.export_table_definition.presentation.ExportTableDefinitionController;
import com.export_table_definition.presentation.dto.ResultDto;
import com.google.inject.Guice;

/**
 * テーブル定義出力処理を呼び出すクラス
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinition {

    /** chunkSize未設定時のデフォルト値（1スキーマあたりこの件数ごとに詳細情報を取得・出力する） */
    private static final int DEFAULT_CHUNK_SIZE = 3000;
    private ExportTableDefinitionController controller;

    ExportTableDefinition(ExportTableDefinitionController controller) {
        this.controller = controller;
    }

    /**
     * テーブル定義出力処理のエントリーポイントメソッド
     * 
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        new ExportTableDefinition(Guice.createInjector(new ExportTableDefinitionModule())
                .getInstance(ExportTableDefinitionController.class)).run();
    }

    /**
     * テーブル定義出力処理実行メソッド
     */
    void run() {
        // プロパティファイルの読み込み
        final List<String> schemaList = PropertyLoader.getList("ExportTableDefinition", "schema");
        final List<String> tableList = PropertyLoader.getList("ExportTableDefinition", "table");
        final String outputPath = PropertyLoader.getString("ExportTableDefinition", "outputPath");
        final int chunkSize = PropertyLoader.getInt("ExportTableDefinition", "chunkSize", DEFAULT_CHUNK_SIZE);
        // 処理開始メッセージ出力
        System.out.println("""
                Starting output of table definition document.
                Please wait a moment ...
                """);
        // テーブル定義出力処理実行
        final ResultDto resultDto = controller.execute(schemaList, tableList, outputPath, chunkSize);
        // 処理終了メッセージ出力
        System.out.println(resultDto.getResultMessage());
    }
}
