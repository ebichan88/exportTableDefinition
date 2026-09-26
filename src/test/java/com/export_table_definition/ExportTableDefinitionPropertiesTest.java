package com.export_table_definition;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.ExportTableDefinitionProperties.SettingOverride;
import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.config.InvalidConfigurationException;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableType;
import com.export_table_definition.domain.model.target.OutputObjectType;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ExportTableDefinitionProperties の設定ファイルの読み込み・検証（READMEに記載した設定項目の仕様）に関するテスト */
public class ExportTableDefinitionPropertiesTest {

  private static TableEntity table(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, TableType.TABLE, "");
  }

  @Test
  @DisplayName("of: すべての項目が未指定の場合は既定値を用いる")
  void testOfUsesDefaultsWhenNothingSpecified() {
    ExportRequest request = ExportTableDefinitionProperties.of(Map.of()).toExportRequest(false);

    assertEquals(List.of(), request.targetSelection().targetScope().schemaNames());
    assertFalse(request.targetSelection().targetScope().isFiltered());
    assertEquals(
        EnumSet.allOf(OutputObjectType.class), request.targetSelection().outputObjectTypes());
    assertEquals("", request.targetSelection().sidecarPath());
    assertEquals("", request.outputPath());
    assertEquals(3000, request.chunkSize());
    assertEquals(80, request.erDiagramMaxNodes());
  }

  @Test
  @DisplayName("of: キーを書かない場合と値を空白にした場合は、同じ「未指定」として扱う")
  void testOfTreatsBlankValueAsUnspecified() {
    ExportRequest omitted = ExportTableDefinitionProperties.of(Map.of()).toExportRequest(false);
    ExportRequest blank =
        ExportTableDefinitionProperties.of(
                Map.of(
                    "schema", "",
                    "table", " ",
                    "outputPath", "",
                    "chunkSize", "",
                    "erDiagramMaxNodes", "  ",
                    "outputObjects", "",
                    "annotationPath", ""))
            .toExportRequest(false);

    assertEquals(
        omitted.targetSelection().targetScope().schemaNames(),
        blank.targetSelection().targetScope().schemaNames());
    assertFalse(blank.targetSelection().targetScope().isFiltered());
    assertEquals(
        omitted.targetSelection().outputObjectTypes(), blank.targetSelection().outputObjectTypes());
    assertEquals(omitted.targetSelection().sidecarPath(), blank.targetSelection().sidecarPath());
    assertEquals(omitted.outputPath(), blank.outputPath());
    assertEquals(omitted.chunkSize(), blank.chunkSize());
    assertEquals(omitted.erDiagramMaxNodes(), blank.erDiagramMaxNodes());
  }

  @Test
  @DisplayName("of: 指定した値を型へ変換し、前後の空白を除去する")
  void testOfParsesSpecifiedValues() {
    ExportRequest request =
        ExportTableDefinitionProperties.of(
                Map.of(
                    "schema", " sample ",
                    "table", "!tmp_*",
                    "outputPath", " ./docs/db ",
                    "chunkSize", " 100 ",
                    "erDiagramMaxNodes", "0",
                    "outputObjects", "trigger, function",
                    "annotationPath", " conf/annotations.yml "))
            .toExportRequest(true);

    assertEquals(List.of("sample"), request.targetSelection().targetScope().schemaNames());
    assertFalse(request.targetSelection().targetScope().matches(table("sample", "tmp_work")));
    assertEquals("./docs/db", request.outputPath());
    assertEquals(100, request.chunkSize());
    assertEquals(0, request.erDiagramMaxNodes());
    assertEquals(
        Set.of(OutputObjectType.TRIGGER, OutputObjectType.FUNCTION),
        request.targetSelection().outputObjectTypes());
    assertEquals("conf/annotations.yml", request.targetSelection().sidecarPath());
    assertTrue(request.rmDist());
  }

  @Test
  @DisplayName("of: カンマ区切りの値は分割し、各要素の前後の空白を除去して空要素を除く")
  void testOfSplitsCommaSeparatedValues() {
    ExportRequest request =
        ExportTableDefinitionProperties.of(Map.of("schema", "alpha,beta, gamma ,,delta"))
            .toExportRequest(false);

    assertEquals(
        List.of("alpha", "beta", "gamma", "delta"),
        request.targetSelection().targetScope().schemaNames());
  }

  @Test
  @DisplayName("of: 未知のキー（キー名の書き誤り等）は、書けるキーを添えて誤りとする")
  void testOfRejectsUnknownKey() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () -> ExportTableDefinitionProperties.of(Map.of("outputpath", "./docs")));

    assertTrue(e.getMessage().contains("ExportTableDefinition.properties"));
    assertTrue(e.getMessage().contains("Unknown key: outputpath"));
    assertTrue(e.getMessage().contains("outputPath"));
  }

  @Test
  @DisplayName("of: 整数として解釈できない値は、既定値へ置き換えずに誤りとする")
  void testOfRejectsNonIntegerValues() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () -> ExportTableDefinitionProperties.of(Map.of("chunkSize", "abc")));

    assertTrue(e.getMessage().contains("chunkSize must be an integer: abc"));
  }

  @Test
  @DisplayName("of: 出力対象の条件として解釈できない値（未知の種別名・空のテーブル名）は誤りとする")
  void testOfRejectsInvalidTargetSelection() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () ->
                ExportTableDefinitionProperties.of(
                    Map.of("outputObjects", "trigers", "table", "sample.")));

    assertTrue(e.getMessage().contains("trigers"));
    assertTrue(e.getMessage().contains("sample."));
  }

  @Test
  @DisplayName("of: 複数の誤りは1件ずつではなく、まとめて報告する")
  void testOfReportsAllErrorsAtOnce() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () ->
                ExportTableDefinitionProperties.of(
                    Map.of(
                        "chunksize", "100",
                        "erDiagramMaxNodes", "1.5",
                        "outputObjects", "trigers")));

    assertTrue(e.getMessage().contains("Unknown key: chunksize"));
    assertTrue(e.getMessage().contains("erDiagramMaxNodes must be an integer: 1.5"));
    assertTrue(e.getMessage().contains("trigers"));
  }

  @Test
  @DisplayName("toCheckDiffRequest: 差分検知の入力へ、出力対象の条件・出力先・chunkSizeを渡す")
  void testToCheckDiffRequest() {
    ExportTableDefinitionProperties properties =
        ExportTableDefinitionProperties.of(
            Map.of("schema", "sample", "outputPath", "./docs/db", "chunkSize", "50"));

    CheckDiffRequest request = properties.toCheckDiffRequest();

    assertEquals(List.of("sample"), request.targetSelection().targetScope().schemaNames());
    assertEquals("./docs/db", request.outputPath());
    assertEquals(50, request.chunkSize());
  }

  @Test
  @DisplayName("load: 配布する設定ファイル（全項目が未指定）は、誤りなく既定値で読み込める")
  void testLoadDistributedTemplate() {
    ExportRequest request = ExportTableDefinitionProperties.load(Map.of()).toExportRequest(false);

    assertEquals(3000, request.chunkSize());
    assertEquals(80, request.erDiagramMaxNodes());
    assertFalse(request.targetSelection().targetScope().isFiltered());
  }

  @Test
  @DisplayName("of: CLI引数による上書き値は設定ファイルの値より優先し、上書きしないキーは設定ファイルの値を用いる")
  void testOfAppliesOverrides() {
    ExportRequest request =
        ExportTableDefinitionProperties.of(
                Map.of("schema", "sample", "outputPath", "./docs/db", "chunkSize", "100"),
                Map.of(
                    "outputPath", new SettingOverride("./docs/prod", "--output-path"),
                    "table", new SettingOverride("!tmp_*", "--table")))
            .toExportRequest(false);

    assertEquals(List.of("sample"), request.targetSelection().targetScope().schemaNames());
    assertFalse(request.targetSelection().targetScope().matches(table("sample", "tmp_work")));
    assertEquals("./docs/prod", request.outputPath());
    assertEquals(100, request.chunkSize());
  }

  @Test
  @DisplayName("of: 上書き値も設定ファイルの値と同じ仕様で検証し、誤りの報告に上書きの指定元を添える")
  void testOfValidatesOverriddenValues() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () ->
                ExportTableDefinitionProperties.of(
                    Map.of("chunkSize", "100"),
                    Map.of(
                        "chunkSize", new SettingOverride("abc", "--chunk-size"),
                        "outputObjects", new SettingOverride("trigers", "--output-objects"))));

    assertTrue(e.getMessage().contains("chunkSize must be an integer: abc"));
    assertTrue(e.getMessage().contains("trigers"));
    assertTrue(e.getMessage().contains("overridden by "));
    assertTrue(e.getMessage().contains("--chunk-size"));
    assertTrue(e.getMessage().contains("--output-objects"));
  }

  @Test
  @DisplayName("of: 上書きしていない場合は、誤りの報告に上書きの指定元を添えない")
  void testOfDoesNotMentionOverridesWhenNothingOverridden() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () -> ExportTableDefinitionProperties.of(Map.of("chunkSize", "abc"), Map.of()));

    assertTrue(
        e.getMessage().startsWith("Invalid configuration in ExportTableDefinition.properties."));
    assertFalse(e.getMessage().contains("overridden by"));
  }
}
