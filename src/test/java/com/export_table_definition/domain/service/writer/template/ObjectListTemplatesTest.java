package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;

/**
 * ObjectListTemplates のセクション生成テスト
 */
public class ObjectListTemplatesTest {

    private final BaseInfoEntity base = new BaseInfoEntity("TEST_DB", "| pg | TEST_DB | 2025-01-01 |");

    @Test
    @DisplayName("fileHeader: タイトルとDB名を含む")
    void testFileHeader() {
        String header = ObjectListTemplates.fileHeader("トリガー一覧", base);
        assertTrue(header.startsWith("# トリガー一覧（DB名：TEST_DB）"));
    }

    @Test
    @DisplayName("triggerList: ヘッダーと一覧行を含む")
    void testTriggerList() {
        var t = new TriggerEntity("public", "orders",
                "| 1 | public | orders | trg_orders | BEFORE | INSERT | public.f_orders |", "unused");
        String section = ObjectListTemplates.triggerList(List.of(t));
        assertTrue(section.contains("## トリガー一覧"));
        assertTrue(section.contains("| 1 | public | orders | trg_orders | BEFORE | INSERT | public.f_orders |"));
    }

    @Test
    @DisplayName("functionList: ヘッダーと一覧行を含む")
    void testFunctionList() {
        var f = new FunctionEntity("TEST_DB", "public", "f_add", "f_add",
                "| 1 | public | FUNCTION | f_add | a integer | integer | plpgsql | [■](./TEST_DB/public/function/f_add.md) |",
                "");
        String section = ObjectListTemplates.functionList(List.of(f));
        assertTrue(section.contains("## 関数・プロシージャ一覧"));
        assertTrue(section.contains("f_add"));
        assertTrue(section.contains("[■](./TEST_DB/public/function/f_add.md)"));
    }

    @Test
    @DisplayName("sequenceList: ヘッダーと一覧行を含む")
    void testSequenceList() {
        var s = new SequenceEntity("TEST_DB", "public", "seq_orders",
                "| 1 | public | seq_orders | 1 | 1 | 9223372036854775807 | 1 | 1 |  | orders.id | [■](./TEST_DB/public/sequence/seq_orders.md) |",
                "unused");
        String section = ObjectListTemplates.sequenceList(List.of(s));
        assertTrue(section.contains("## シーケンス一覧"));
        assertTrue(section.contains("seq_orders"));
    }

    @Test
    @DisplayName("typeList: ヘッダーと一覧行を含む")
    void testTypeList() {
        var t = new TypeEntity("TEST_DB", "public", "mood", "ENUM",
                "| 1 | public | mood | ENUM | sad, ok, happy | [■](./TEST_DB/public/type/mood.md) |", "sad, ok, happy");
        String section = ObjectListTemplates.typeList(List.of(t));
        assertTrue(section.contains("## ユーザー定義型一覧"));
        assertTrue(section.contains("mood"));
        assertTrue(section.contains("sad, ok, happy"));
    }
}
