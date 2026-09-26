package com.export_table_definition.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.application.TargetSelection;
import com.export_table_definition.domain.model.ContentDiff;
import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ProcessResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ExportTableDefinitionController の成功/例外時の処理結果組み立てに関するテスト */
public class ExportTableDefinitionControllerTest {

  /** 呼び出し引数を記録し、任意の例外を投げられるユースケースのスタブ */
  private static class RecordingUsecase implements ExportTableDefinitionUsecase {
    ExportRequest capturedExportRequest;
    CheckDiffRequest capturedCheckDiffRequest;
    RuntimeException toThrow;
    DiffResult diffResultToReturn = new DiffResult(List.of(), List.of(), List.of());

    @Override
    public void exportTableDefinition(ExportRequest request) {
      this.capturedExportRequest = request;
      if (toThrow != null) {
        throw toThrow;
      }
    }

    @Override
    public DiffResult checkDocumentDiff(CheckDiffRequest request) {
      this.capturedCheckDiffRequest = request;
      if (toThrow != null) {
        throw toThrow;
      }
      return diffResultToReturn;
    }
  }

  private ExportRequest exportRequest(
      List<String> schemaList,
      List<String> tableList,
      String outputPath,
      int chunkSize,
      int erDiagramMaxNodes,
      List<String> outputObjectList,
      String annotationPath,
      boolean rmDist) {
    return new ExportRequest(
        new TargetSelection(schemaList, tableList, outputObjectList, annotationPath),
        outputPath,
        chunkSize,
        erDiagramMaxNodes,
        rmDist);
  }

  private CheckDiffRequest checkDiffRequest(
      List<String> schemaList,
      List<String> tableList,
      String outputPath,
      int chunkSize,
      List<String> outputObjectList,
      String annotationPath) {
    return new CheckDiffRequest(
        new TargetSelection(schemaList, tableList, outputObjectList, annotationPath),
        outputPath,
        chunkSize);
  }

  @Test
  @DisplayName("execute: ユースケースが正常終了した場合はSUCCESSの結果を返す")
  void testExecuteSuccessReturnsSuccessResult() {
    var usecase = new RecordingUsecase();
    var controller = new ExportTableDefinitionController(usecase);

    ResultDto result =
        controller.execute(
            exportRequest(
                List.of("public"),
                List.of("orders"),
                "output",
                100,
                80,
                List.of(),
                "conf/annotations.yml",
                false));

    assertEquals(ProcessResult.SUCCESS, result.result());
    assertEquals("Table definition output is complete.", result.message());
  }

  @Test
  @DisplayName("execute: 引数（ExportRequest）をそのままユースケースへ渡す")
  void testExecutePassesArgumentsThrough() {
    var usecase = new RecordingUsecase();
    var controller = new ExportTableDefinitionController(usecase);

    ExportRequest request =
        exportRequest(
            List.of("public"),
            List.of("orders"),
            "output",
            100,
            80,
            List.of("trigger", "function"),
            "conf/annotations.yml",
            true);

    controller.execute(request);

    assertSame(request, usecase.capturedExportRequest);
  }

  @Test
  @DisplayName("execute: ユースケースが例外を投げた場合はFAILの結果を返し、例外メッセージを含む")
  void testExecuteExceptionReturnsFailResult() {
    var usecase = new RecordingUsecase();
    usecase.toThrow = new RuntimeException("boom");
    var controller = new ExportTableDefinitionController(usecase);

    ResultDto result =
        controller.execute(exportRequest(List.of(), List.of(), null, 0, 0, List.of(), null, false));

    assertEquals(ProcessResult.FAIL, result.result());
    assertTrue(result.message().contains("boom"));
  }

  @Test
  @DisplayName("execute: 例外発生時もexecute自体は例外を伝播させない")
  void testExecuteExceptionDoesNotPropagate() {
    var usecase = new RecordingUsecase();
    usecase.toThrow = new IllegalStateException("unexpected");
    var controller = new ExportTableDefinitionController(usecase);

    assertDoesNotThrow(
        () ->
            controller.execute(
                exportRequest(List.of(), List.of(), null, 0, 0, List.of(), null, false)));
  }

  @Test
  @DisplayName("checkDiff: 差分が見つからない場合はSUCCESSかつhasDifference=falseを返す")
  void testCheckDiffNoDifferenceReturnsSuccessWithoutDifference() {
    var usecase = new RecordingUsecase();
    usecase.diffResultToReturn = new DiffResult(List.of(), List.of(), List.of());
    var controller = new ExportTableDefinitionController(usecase);

    DiffCheckResultDto result =
        controller.checkDiff(
            checkDiffRequest(List.of("public"), List.of(), "output", 100, List.of(), null));

    assertEquals(ProcessResult.SUCCESS, result.result());
    assertFalse(result.hasDifference());
  }

  @Test
  @DisplayName("checkDiff: 差分が見つかった場合はSUCCESSかつhasDifference=trueを返し、差分対象とunified diffをメッセージに含める")
  void testCheckDiffWithDifferenceReturnsSuccessWithDifference() {
    var usecase = new RecordingUsecase();
    usecase.diffResultToReturn =
        new DiffResult(
            List.of("new.md"),
            List.of("stale.md"),
            List.of(
                new ContentDiff(
                    "table public.changed",
                    List.of(
                        "--- committed/t.jsonl",
                        "+++ generated/t.jsonl",
                        "@@ -1 +1 @@",
                        "-old",
                        "+new"))));
    var controller = new ExportTableDefinitionController(usecase);

    DiffCheckResultDto result =
        controller.checkDiff(
            checkDiffRequest(List.of(), List.of(), "output", 100, List.of(), null));

    assertEquals(ProcessResult.SUCCESS, result.result());
    assertTrue(result.hasDifference());
    assertTrue(result.message().contains("new.md"));
    assertTrue(result.message().contains("stale.md"));
    assertTrue(result.message().contains("table public.changed"));
    assertTrue(result.message().contains("--- committed/t.jsonl"));
    assertTrue(result.message().contains("+++ generated/t.jsonl"));
    assertTrue(result.message().contains("@@ -1 +1 @@"));
    assertTrue(result.message().contains("-old"));
    assertTrue(result.message().contains("+new"));
  }

  @Test
  @DisplayName("checkDiff: 引数（CheckDiffRequest）をそのままユースケースへ渡す")
  void testCheckDiffPassesArgumentsThrough() {
    var usecase = new RecordingUsecase();
    var controller = new ExportTableDefinitionController(usecase);

    CheckDiffRequest request =
        checkDiffRequest(
            List.of("public"),
            List.of("orders"),
            "output",
            100,
            List.of("trigger"),
            "conf/annotations.yml");

    controller.checkDiff(request);

    assertSame(request, usecase.capturedCheckDiffRequest);
  }

  @Test
  @DisplayName("checkDiff: ユースケースが例外を投げた場合はFAILの結果を返し、例外を伝播させない")
  void testCheckDiffExceptionReturnsFailResultWithoutPropagating() {
    var usecase = new RecordingUsecase();
    usecase.toThrow = new RuntimeException("boom");
    var controller = new ExportTableDefinitionController(usecase);

    DiffCheckResultDto result =
        assertDoesNotThrow(
            () ->
                controller.checkDiff(
                    checkDiffRequest(List.of(), List.of(), null, 0, List.of(), null)));

    assertEquals(ProcessResult.FAIL, result.result());
    assertTrue(result.message().contains("boom"));
  }
}
