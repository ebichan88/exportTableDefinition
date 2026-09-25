package com.export_table_definition.presentation;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ProcessResult;
import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
  public ExportTableDefinitionController(
      ExportTableDefinitionUsecase exportTableDefinitionUsecase) {
    this.exportTableDefinitionUsecase = exportTableDefinitionUsecase;
  }

  /**
   * コントローラーメソッド
   *
   * @param request テーブル定義出力の入力
   * @return 処理結果
   */
  public ResultDto execute(ExportRequest request) {
    logger.info("[START] exportTableDefinition");
    try {
      exportTableDefinitionUsecase.exportTableDefinition(request);
    } catch (Exception e) {
      logger.error(e);
      return new ResultDto(
          ProcessResult.FAIL,
          String.format(
              "Failed to output table definition document. %s [errmsg]:%s",
              System.getProperty("line.separator"), e.getMessage()));
    }
    logger.info("[ END ] exportTableDefinition");
    return new ResultDto(ProcessResult.SUCCESS, "Table definition output is complete.");
  }

  /**
   * DBの現状から生成したドキュメントと、{@code outputPath}配下に既にコミット済みのドキュメントの差分を検知するメソッド
   *
   * @param request DB vs ドキュメントの差分検知の入力
   * @return 処理結果（比較処理自体の成否と、差分の有無）
   */
  public DiffCheckResultDto checkDiff(CheckDiffRequest request) {
    logger.info("[START] checkDocumentDiff");
    final DiffResult diffResult;
    try {
      diffResult = exportTableDefinitionUsecase.checkDocumentDiff(request);
    } catch (Exception e) {
      logger.error(e);
      return new DiffCheckResultDto(
          ProcessResult.FAIL,
          String.format(
              "Failed to check table definition document diff. %s [errmsg]:%s",
              System.getProperty("line.separator"), e.getMessage()),
          false);
    }
    logger.info("[ END ] checkDocumentDiff");
    return new DiffCheckResultDto(
        ProcessResult.SUCCESS, DiffReportFormatter.format(diffResult), diffResult.hasDifference());
  }
}
