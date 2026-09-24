package com.export_table_definition.presentation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ProcessResult;

/**
 * ExportTableDefinitionController の成功/例外時の処理結果組み立てに関するテスト
 */
public class ExportTableDefinitionControllerTest {

    /** 呼び出し引数を記録し、任意の例外を投げられるユースケースのスタブ */
    private static class RecordingUsecase implements ExportTableDefinitionUsecase {
        List<String> capturedSchemaList;
        List<String> capturedTableList;
        String capturedOutputPath;
        int capturedChunkSize;
        int capturedErDiagramMaxNodes;
        RuntimeException toThrow;

        @Override
        public void exportTableDefinition(List<String> targetSchemaList, List<String> targetTableList,
                String outputPath, int chunkSize, int erDiagramMaxNodes) {
            this.capturedSchemaList = targetSchemaList;
            this.capturedTableList = targetTableList;
            this.capturedOutputPath = outputPath;
            this.capturedChunkSize = chunkSize;
            this.capturedErDiagramMaxNodes = erDiagramMaxNodes;
            if (toThrow != null) {
                throw toThrow;
            }
        }
    }

    @Test
    @DisplayName("execute: ユースケースが正常終了した場合はSUCCESSの結果を返す")
    void testExecuteSuccessReturnsSuccessResult() {
        var usecase = new RecordingUsecase();
        var controller = new ExportTableDefinitionController(usecase);

        ResultDto result = controller.execute(List.of("public"), List.of("orders"), "output", 100, 80);

        assertEquals(ProcessResult.SUCCESS, result.result());
        assertEquals("Table definition output is complete.", result.message());
    }

    @Test
    @DisplayName("execute: 引数をそのままユースケースへ渡す")
    void testExecutePassesArgumentsThrough() {
        var usecase = new RecordingUsecase();
        var controller = new ExportTableDefinitionController(usecase);

        controller.execute(List.of("public"), List.of("orders"), "output", 100, 80);

        assertEquals(List.of("public"), usecase.capturedSchemaList);
        assertEquals(List.of("orders"), usecase.capturedTableList);
        assertEquals("output", usecase.capturedOutputPath);
        assertEquals(100, usecase.capturedChunkSize);
        assertEquals(80, usecase.capturedErDiagramMaxNodes);
    }

    @Test
    @DisplayName("execute: ユースケースが例外を投げた場合はFAILの結果を返し、例外メッセージを含む")
    void testExecuteExceptionReturnsFailResult() {
        var usecase = new RecordingUsecase();
        usecase.toThrow = new RuntimeException("boom");
        var controller = new ExportTableDefinitionController(usecase);

        ResultDto result = controller.execute(List.of(), List.of(), null, 0, 0);

        assertEquals(ProcessResult.FAIL, result.result());
        assertTrue(result.message().contains("boom"));
    }

    @Test
    @DisplayName("execute: 例外発生時もexecute自体は例外を伝播させない")
    void testExecuteExceptionDoesNotPropagate() {
        var usecase = new RecordingUsecase();
        usecase.toThrow = new IllegalStateException("unexpected");
        var controller = new ExportTableDefinitionController(usecase);

        assertDoesNotThrow(() -> controller.execute(List.of(), List.of(), null, 0, 0));
    }
}
