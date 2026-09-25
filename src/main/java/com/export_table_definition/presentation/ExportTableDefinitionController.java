package com.export_table_definition.presentation;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ProcessResult;
import com.google.inject.Inject;

/**
 * テーブル定義出力処理のコントローラークラス
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinitionController {

    private static final Logger logger = LogManager.getLogger(ExportTableDefinitionController.class);
    private final ExportTableDefinitionUsecase exportTableDefinitionUsecase;

    /**
     * コンストラクタ
     * 
     * @param exportTableDefinitionUsecase テーブル定義出力に関するユースケースクラス
     */
    @Inject
    public ExportTableDefinitionController(ExportTableDefinitionUsecase exportTableDefinitionUsecase) {
        this.exportTableDefinitionUsecase = exportTableDefinitionUsecase;
    }

    /**
     * コントローラーメソッド
     * 
     * @param schemaList テーブル定義出力対象のスキーマのリスト
     * @param tableList  テーブル定義出力対象のテーブルのリスト
     * @param outputPath テーブル定義出力の出力先のパス
     * @param chunkSize  詳細情報をまとめて取得するテーブル数の上限
     * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限
     * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名のリスト（空の場合は全種別を出力対象とする）
     * @param annotationPath   手動付帯情報を記述したサイドカーYAMLのパス（空の場合はマージを行わない）
     * @return 処理結果
     */
    public ResultDto execute(List<String> schemaList, List<String> tableList, String outputPath, int chunkSize,
            int erDiagramMaxNodes, List<String> outputObjectList, String annotationPath) {
        logger.info("[START] exportTableDefinition");
        try {
            exportTableDefinitionUsecase.exportTableDefinition(schemaList, tableList, outputPath, chunkSize,
                    erDiagramMaxNodes, outputObjectList, annotationPath);
        } catch (Exception e) {
            logger.error(e);
            return new ResultDto(ProcessResult.FAIL,
                    String.format("Failed to output table definition document. %s [errmsg]:%s",
                            System.getProperty("line.separator"), e.getMessage()));
        }
        logger.info("[ END ] exportTableDefinition");
        return new ResultDto(ProcessResult.SUCCESS, "Table definition output is complete.");
    }

}
