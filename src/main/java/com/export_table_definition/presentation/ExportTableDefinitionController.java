package com.export_table_definition.presentation;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
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

    /**
     * DBの現状から生成したドキュメントと、{@code outputPath}配下に既にコミット済みのドキュメントの差分を検知するメソッド
     *
     * @param schemaList テーブル定義出力対象のスキーマのリスト
     * @param tableList  テーブル定義出力対象のテーブルのリスト
     * @param outputPath 比較対象となる、既にコミット済みのドキュメントが配置されたパス
     * @param chunkSize  詳細情報をまとめて取得するテーブル数の上限
     * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限
     * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名のリスト（空の場合は全種別を出力対象とする）
     * @param annotationPath   手動付帯情報を記述したサイドカーYAMLのパス（空の場合はマージを行わない）
     * @return 処理結果（比較処理自体の成否と、差分の有無）
     */
    public DiffCheckResultDto checkDiff(List<String> schemaList, List<String> tableList, String outputPath,
            int chunkSize, int erDiagramMaxNodes, List<String> outputObjectList, String annotationPath) {
        logger.info("[START] checkDocumentDiff");
        final DiffResult diffResult;
        try {
            diffResult = exportTableDefinitionUsecase.checkDocumentDiff(schemaList, tableList, outputPath, chunkSize,
                    erDiagramMaxNodes, outputObjectList, annotationPath);
        } catch (Exception e) {
            logger.error(e);
            return new DiffCheckResultDto(ProcessResult.FAIL,
                    String.format("Failed to check table definition document diff. %s [errmsg]:%s",
                            System.getProperty("line.separator"), e.getMessage()),
                    false);
        }
        logger.info("[ END ] checkDocumentDiff");
        return new DiffCheckResultDto(ProcessResult.SUCCESS, buildDiffMessage(diffResult), diffResult.hasDifference());
    }

    /**
     * 比較結果からメッセージを組み立てるメソッド
     *
     * @param diffResult 比較結果
     * @return 差分の内容を含むメッセージ
     */
    private String buildDiffMessage(DiffResult diffResult) {
        final String lineSeparator = System.getProperty("line.separator");
        if (!diffResult.hasDifference()) {
            return "No difference detected between the generated document and the committed document.";
        }
        final StringBuilder message = new StringBuilder(
                "Difference detected between the generated document and the committed document.");
        appendPathSection(message, lineSeparator,
                "Only in generated document (possibly missing commit):", diffResult.onlyInGenerated());
        appendPathSection(message, lineSeparator,
                "Only in committed document (possibly a stale file):", diffResult.onlyInCommitted());
        appendPathSection(message, lineSeparator, "Content differs:", diffResult.contentDiffer());
        return message.toString();
    }

    /**
     * 差分ファイルの一覧をメッセージへ追記するメソッド。対象が空の場合は何も追記しない
     *
     * @param message       追記先のメッセージ
     * @param lineSeparator 改行文字
     * @param title         区分のタイトル
     * @param paths         区分に属するファイルパスのリスト
     */
    private void appendPathSection(StringBuilder message, String lineSeparator, String title, List<Path> paths) {
        if (paths.isEmpty()) {
            return;
        }
        message.append(lineSeparator).append(title);
        paths.forEach(path -> message.append(lineSeparator).append(" - ").append(path));
    }

}
