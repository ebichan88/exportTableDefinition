package com.export_table_definition.application;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.type.OutputObjectType;
import com.export_table_definition.domain.model.type.TableType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TargetSelection の生成（設定値の型への変換・検証）に関するテスト */
public class TargetSelectionTest {

  private static TableEntity table(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, TableType.TABLE, "");
  }

  @Test
  @DisplayName("of: 出力対象オブジェクト種別名を種別の集合へ変換する")
  void testOfParsesOutputObjectTypes() {
    TargetSelection selection =
        TargetSelection.of(List.of(), List.of(), List.of("trigger", "function"), null);

    assertEquals(
        Set.of(OutputObjectType.TRIGGER, OutputObjectType.FUNCTION), selection.outputObjectTypes());
  }

  @Test
  @DisplayName("of: 出力対象オブジェクト種別が未指定の場合は全種別を対象とする")
  void testOfDefaultsToAllOutputObjectTypes() {
    TargetSelection selection = TargetSelection.of(List.of(), List.of(), List.of(), null);

    assertEquals(EnumSet.allOf(OutputObjectType.class), selection.outputObjectTypes());
  }

  @Test
  @DisplayName("of: 未知の出力対象オブジェクト種別名はIllegalArgumentExceptionで検知する")
  void testOfRejectsUnknownOutputObjectType() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> TargetSelection.of(List.of(), List.of(), List.of("trigers"), null));
    assertTrue(e.getMessage().contains("trigers"));
  }

  @Test
  @DisplayName("of: スキーマ・テーブルの指定から出力対象の範囲を組み立てる")
  void testOfBuildsTargetScope() {
    TargetSelection selection =
        TargetSelection.of(List.of("sample"), List.of("!tmp_*"), List.of(), "sidecar.yml");

    assertEquals(List.of("sample"), selection.targetScope().schemaNames());
    assertTrue(selection.targetScope().matches(table("sample", "employee")));
    assertFalse(selection.targetScope().matches(table("sample", "tmp_work")));
    assertFalse(selection.targetScope().matches(table("public", "employee")));
    assertEquals("sidecar.yml", selection.sidecarPath());
  }
}
