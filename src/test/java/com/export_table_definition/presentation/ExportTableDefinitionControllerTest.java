package com.export_table_definition.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.CheckDocumentDiffUsecase;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.application.TargetSelection;
import com.export_table_definition.domain.model.snapshot.ContentDiff;
import com.export_table_definition.domain.model.snapshot.DiffResult;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ExitStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ExportTableDefinitionController の処理結果の組み立てと、例外を捕捉せずに伝えることに関するテスト */
public class ExportTableDefinitionControllerTest {

  /** 呼び出し引数を記録し、任意の例外を投げられるユースケースのスタブ */
  private static class RecordingUsecase
      implements ExportTableDefinitionUsecase, CheckDocumentDiffUsecase {
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
      String sidecarPath,
      boolean rmDist) {
    return new ExportRequest(
        TargetSelection.of(schemaList, tableList, outputObjectList, sidecarPath),
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
      String sidecarPath) {
    return new CheckDiffRequest(
        TargetSelection.of(schemaList, tableList, outputObjectList, sidecarPath),
        outputPath,
        chunkSize);
  }

  @Test
  @DisplayName("execute: ユースケースが正常終了した場合はSUCCESSの結果を返す")
  void testExecuteSuccessReturnsSuccessResult() {
    var usecase = new RecordingUsecase();
    var controller = new ExportTableDefinitionController(usecase, usecase);

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

    assertEquals("Table definition output is complete.", result.message());
    assertTrue(result.getResultMessage().startsWith("[result]:SUCCESS"));
  }

  @Test
  @DisplayName("execute: 引数（ExportRequest）をそのままユースケースへ渡す")
  void testExecutePassesArgumentsThrough() {
    var usecase = new RecordingUsecase();
    var controller = new ExportTableDefinitionController(usecase, usecase);

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
  @DisplayName("execute: ユースケースの例外は捕捉せず、そのまま呼び出し元へ伝える（捕捉はエントリーポイントの境界で行う）")
  void testExecutePropagatesUsecaseException() {
    var usecase = new RecordingUsecase();
    usecase.toThrow = new IllegalStateException("unexpected");
    var controller = new ExportTableDefinitionController(usecase, usecase);

    var thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                controller.execute(
                    exportRequest(List.of(), List.of(), null, 0, 0, List.of(), null, false)));

    assertSame(usecase.toThrow, thrown);
  }

  @Test
  @DisplayName("checkDiff: 差分が見つからない場合はhasDifference=falseを返し、終了状態はSUCCESSとなる")
  void testCheckDiffNoDifferenceReturnsSuccessWithoutDifference() {
    var usecase = new RecordingUsecase();
    usecase.diffResultToReturn = new DiffResult(List.of(), List.of(), List.of());
    var controller = new ExportTableDefinitionController(usecase, usecase);

    DiffCheckResultDto result =
        controller.checkDiff(
            checkDiffRequest(List.of("public"), List.of(), "output", 100, List.of(), null));

    assertFalse(result.hasDifference());
    assertEquals(ExitStatus.SUCCESS, result.exitStatus());
    assertTrue(result.getResultMessage().startsWith("[result]:SUCCESS"));
  }

  @Test
  @DisplayName(
      "checkDiff: 差分が見つかった場合はhasDifference=trueを返し（終了状態はDIFFERENCE_FOUND）、差分対象とunified diffをメッセージに含める")
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
    var controller = new ExportTableDefinitionController(usecase, usecase);

    DiffCheckResultDto result =
        controller.checkDiff(
            checkDiffRequest(List.of(), List.of(), "output", 100, List.of(), null));

    assertTrue(result.hasDifference());
    assertEquals(ExitStatus.DIFFERENCE_FOUND, result.exitStatus());
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
    var controller = new ExportTableDefinitionController(usecase, usecase);

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
  @DisplayName("checkDiff: ユースケースの例外は捕捉せず、そのまま呼び出し元へ伝える（捕捉はエントリーポイントの境界で行う）")
  void testCheckDiffPropagatesUsecaseException() {
    var usecase = new RecordingUsecase();
    usecase.toThrow = new RuntimeException("boom");
    var controller = new ExportTableDefinitionController(usecase, usecase);

    var thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                controller.checkDiff(
                    checkDiffRequest(List.of(), List.of(), null, 0, List.of(), null)));

    assertSame(usecase.toThrow, thrown);
  }
}
