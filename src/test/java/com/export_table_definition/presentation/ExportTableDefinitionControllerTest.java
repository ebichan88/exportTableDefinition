package com.export_table_definition.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ProcessResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ExportTableDefinitionController の成功/例外時の処理結果組み立てに関するテスト */
public class ExportTableDefinitionControllerTest {

  /** 呼び出し引数を記録し、任意の例外を投げられるユースケースのスタブ */
  private static class RecordingUsecase implements ExportTableDefinitionUsecase {
    List<String> capturedSchemaList;
    List<String> capturedTableList;
    String capturedOutputPath;
    int capturedChunkSize;
    int capturedErDiagramMaxNodes;
    List<String> capturedOutputObjectList;
    String capturedAnnotationPath;
    boolean capturedRmDist;
    RuntimeException toThrow;
    DiffResult diffResultToReturn = new DiffResult(List.of(), List.of(), List.of());

    @Override
    public void exportTableDefinition(
        List<String> targetSchemaList,
        List<String> targetTableList,
        String outputPath,
        int chunkSize,
        int erDiagramMaxNodes,
        List<String> outputObjectList,
        String annotationPath,
        boolean rmDist) {
      this.capturedSchemaList = targetSchemaList;
      this.capturedTableList = targetTableList;
      this.capturedOutputPath = outputPath;
      this.capturedChunkSize = chunkSize;
      this.capturedErDiagramMaxNodes = erDiagramMaxNodes;
      this.capturedOutputObjectList = outputObjectList;
      this.capturedAnnotationPath = annotationPath;
      this.capturedRmDist = rmDist;
      if (toThrow != null) {
        throw toThrow;
      }
    }

    @Override
    public DiffResult checkDocumentDiff(
        List<String> targetSchemaList,
        List<String> targetTableList,
        String outputPath,
        int chunkSize,
        int erDiagramMaxNodes,
        List<String> outputObjectList,
        String annotationPath) {
      this.capturedSchemaList = targetSchemaList;
      this.capturedTableList = targetTableList;
      this.capturedOutputPath = outputPath;
      this.capturedChunkSize = chunkSize;
      this.capturedErDiagramMaxNodes = erDiagramMaxNodes;
      this.capturedOutputObjectList = outputObjectList;
      this.capturedAnnotationPath = annotationPath;
      if (toThrow != null) {
        throw toThrow;
      }
      return diffResultToReturn;
    }
  }

  @Test
  @DisplayName("execute: ユースケースが正常終了した場合はSUCCESSの結果を返す")
  void testExecuteSuccessReturnsSuccessResult() {
    var usecase = new RecordingUsecase();
    var controller = new ExportTableDefinitionController(usecase);

    ResultDto result =
        controller.execute(
            List.of("public"),
            List.of("orders"),
            "output",
            100,
            80,
            List.of(),
            "conf/annotations.yml",
            false);

    assertEquals(ProcessResult.SUCCESS, result.result());
    assertEquals("Table definition output is complete.", result.message());
  }

  @Test
  @DisplayName("execute: 引数をそのままユースケースへ渡す")
  void testExecutePassesArgumentsThrough() {
    var usecase = new RecordingUsecase();
    var controller = new ExportTableDefinitionController(usecase);

    controller.execute(
        List.of("public"),
        List.of("orders"),
        "output",
        100,
        80,
        List.of("trigger", "function"),
        "conf/annotations.yml",
        true);

    assertEquals(List.of("public"), usecase.capturedSchemaList);
    assertEquals(List.of("orders"), usecase.capturedTableList);
    assertEquals("output", usecase.capturedOutputPath);
    assertEquals(100, usecase.capturedChunkSize);
    assertEquals(80, usecase.capturedErDiagramMaxNodes);
    assertEquals(List.of("trigger", "function"), usecase.capturedOutputObjectList);
    assertEquals("conf/annotations.yml", usecase.capturedAnnotationPath);
    assertTrue(usecase.capturedRmDist);
  }

  @Test
  @DisplayName("execute: ユースケースが例外を投げた場合はFAILの結果を返し、例外メッセージを含む")
  void testExecuteExceptionReturnsFailResult() {
    var usecase = new RecordingUsecase();
    usecase.toThrow = new RuntimeException("boom");
    var controller = new ExportTableDefinitionController(usecase);

    ResultDto result = controller.execute(List.of(), List.of(), null, 0, 0, List.of(), null, false);

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
        () -> controller.execute(List.of(), List.of(), null, 0, 0, List.of(), null, false));
  }

  @Test
  @DisplayName("checkDiff: 差分が見つからない場合はSUCCESSかつhasDifference=falseを返す")
  void testCheckDiffNoDifferenceReturnsSuccessWithoutDifference() {
    var usecase = new RecordingUsecase();
    usecase.diffResultToReturn = new DiffResult(List.of(), List.of(), List.of());
    var controller = new ExportTableDefinitionController(usecase);

    DiffCheckResultDto result =
        controller.checkDiff(List.of("public"), List.of(), "output", 100, 80, List.of(), null);

    assertEquals(ProcessResult.SUCCESS, result.result());
    assertFalse(result.hasDifference());
  }

  @Test
  @DisplayName("checkDiff: 差分が見つかった場合はSUCCESSかつhasDifference=trueを返し、差分ファイルをメッセージに含める")
  void testCheckDiffWithDifferenceReturnsSuccessWithDifference() {
    var usecase = new RecordingUsecase();
    usecase.diffResultToReturn =
        new DiffResult(
            List.of(Path.of("new.md")),
            List.of(Path.of("stale.md")),
            List.of(Path.of("changed.md")));
    var controller = new ExportTableDefinitionController(usecase);

    DiffCheckResultDto result =
        controller.checkDiff(List.of(), List.of(), "output", 100, 80, List.of(), null);

    assertEquals(ProcessResult.SUCCESS, result.result());
    assertTrue(result.hasDifference());
    assertTrue(result.message().contains("new.md"));
    assertTrue(result.message().contains("stale.md"));
    assertTrue(result.message().contains("changed.md"));
  }

  @Test
  @DisplayName("checkDiff: 引数をそのままユースケースへ渡す")
  void testCheckDiffPassesArgumentsThrough() {
    var usecase = new RecordingUsecase();
    var controller = new ExportTableDefinitionController(usecase);

    controller.checkDiff(
        List.of("public"),
        List.of("orders"),
        "output",
        100,
        80,
        List.of("trigger"),
        "conf/annotations.yml");

    assertEquals(List.of("public"), usecase.capturedSchemaList);
    assertEquals(List.of("orders"), usecase.capturedTableList);
    assertEquals("output", usecase.capturedOutputPath);
    assertEquals(100, usecase.capturedChunkSize);
    assertEquals(80, usecase.capturedErDiagramMaxNodes);
    assertEquals(List.of("trigger"), usecase.capturedOutputObjectList);
    assertEquals("conf/annotations.yml", usecase.capturedAnnotationPath);
  }

  @Test
  @DisplayName("checkDiff: ユースケースが例外を投げた場合はFAILの結果を返し、例外を伝播させない")
  void testCheckDiffExceptionReturnsFailResultWithoutPropagating() {
    var usecase = new RecordingUsecase();
    usecase.toThrow = new RuntimeException("boom");
    var controller = new ExportTableDefinitionController(usecase);

    DiffCheckResultDto result =
        assertDoesNotThrow(
            () -> controller.checkDiff(List.of(), List.of(), null, 0, 0, List.of(), null));

    assertEquals(ProcessResult.FAIL, result.result());
    assertTrue(result.message().contains("boom"));
  }
}
