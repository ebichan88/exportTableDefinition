package com.export_table_definition.presentation;

import com.export_table_definition.application.CheckDocumentDiffRequest;
import com.export_table_definition.application.CheckDocumentDiffUsecase;
import com.export_table_definition.application.ExportTableDefinitionRequest;
import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.domain.model.snapshot.DiffResult;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import jakarta.inject.Inject;
import java.time.Clock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * テーブル定義出力処理のコントローラークラス<br>
 * ユースケースを実行し、結果を表示用のDTOへ変換する。ユースケースの例外は捕捉せずに呼び出し元へ伝える （捕捉はエントリーポイントが1箇所にまとめて行い、報告は{@link
 * FailureReporter}が行う）
 */
public class ExportTableDefinitionController {

  private static final Logger logger = LogManager.getLogger(ExportTableDefinitionController.class);
  private final ExportTableDefinitionUsecase exportTableDefinitionUsecase;
  private final CheckDocumentDiffUsecase checkDocumentDiffUsecase;
  private final Clock clock;

  /**
   * @param clock 処理時間を計る時計
   */
  @Inject
  public ExportTableDefinitionController(
      ExportTableDefinitionUsecase exportTableDefinitionUsecase,
      CheckDocumentDiffUsecase checkDocumentDiffUsecase,
      Clock clock) {
    this.exportTableDefinitionUsecase = exportTableDefinitionUsecase;
    this.checkDocumentDiffUsecase = checkDocumentDiffUsecase;
    this.clock = clock;
  }

  /** コントローラーメソッド */
  public ResultDto execute(ExportTableDefinitionRequest request) {
    logger.info("[START] exportTableDefinition");
    final long startMillis = clock.millis();
    exportTableDefinitionUsecase.exportTableDefinition(request);
    logger.info("[ END ] exportTableDefinition [elapsedMillis={}]", clock.millis() - startMillis);
    return new ResultDto("Table definition output is complete.");
  }

  /**
   * DBの現状から生成したドキュメントと、{@code outputPath}配下に既にコミット済みのドキュメントの差分を検知するメソッド
   *
   * @param request DB vs ドキュメントの差分検知の入力
   * @return 処理結果（差分の報告と、差分の有無）
   */
  public DiffCheckResultDto checkDiff(CheckDocumentDiffRequest request) {
    logger.info("[START] checkDocumentDiff");
    final long startMillis = clock.millis();
    final DiffResult diffResult = checkDocumentDiffUsecase.checkDocumentDiff(request);
    // 差分の内容（unified diff）は画面にだけ出し、ログには規模だけを残す。ログが差分の大きさに比例して膨らまないようにするため
    logger.info(
        "[ END ] checkDocumentDiff [elapsedMillis={}, onlyInGenerated={}, onlyInCommitted={}, "
            + "contentDiffer={}]",
        clock.millis() - startMillis,
        diffResult.onlyInGenerated().size(),
        diffResult.onlyInCommitted().size(),
        diffResult.contentDiffer().size());
    return new DiffCheckResultDto(
        DiffReportFormatter.format(diffResult), diffResult.hasDifference());
  }
}
