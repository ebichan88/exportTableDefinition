package com.export_table_definition;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.ExportTableDefinitionProperties.SettingOverride;
import com.export_table_definition.shared.exception.UserCorrectableException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CliArguments のフラグ判定・DB接続情報と実行時設定の上書き解決・解釈できない引数の検知に関するテスト */
public class CliArgumentsTest {

  @Test
  @DisplayName("parse: --checkが無い場合はisCheck=false、--rm-distが無い場合はisRmDist=false")
  void testParseFlagsAbsent() {
    CliArguments args = CliArguments.parse(new String[] {});
    assertFalse(args.isCheck());
    assertFalse(args.isRmDist());
  }

  @Test
  @DisplayName("parse: --check・--rm-distが指定されている場合はそれぞれtrueを返す")
  void testParseFlagsPresent() {
    CliArguments args = CliArguments.parse(new String[] {"--check", "--rm-dist"});
    assertTrue(args.isCheck());
    assertTrue(args.isRmDist());
  }

  @Test
  @DisplayName("connectionOverrides: --キー=値形式のCLI引数をDB接続情報として取り込む")
  void testConnectionOverridesFromCliArgs() {
    CliArguments args =
        CliArguments.parse(
            new String[] {
              "--db-driver=org.postgresql.Driver",
              "--db-url=jdbc:postgresql://localhost:5432/testdb",
              "--db-username=user",
              "--db-password=pass"
            });

    Properties overrides = args.connectionOverrides();
    assertEquals("org.postgresql.Driver", overrides.getProperty("driver"));
    assertEquals("jdbc:postgresql://localhost:5432/testdb", overrides.getProperty("url"));
    assertEquals("user", overrides.getProperty("username"));
    assertEquals("pass", overrides.getProperty("password"));
  }

  @Test
  @DisplayName("connectionOverrides: 対応するCLI引数が無いキーは含まれない")
  void testConnectionOverridesEmptyWhenNothingSpecified() {
    Properties overrides = CliArguments.parse(new String[] {}).connectionOverrides();
    assertTrue(overrides.isEmpty());
  }

  @Test
  @DisplayName("connectionOverrides: 空文字を指定した場合は上書きしない")
  void testConnectionOverridesIgnoresBlankValue() {
    Properties overrides = CliArguments.parse(new String[] {"--db-url="}).connectionOverrides();
    assertFalse(overrides.containsKey("url"));
  }

  @Test
  @DisplayName("connectionOverrides: '--'で始まらない、または'='を含まない引数は無視する")
  void testConnectionOverridesIgnoresMalformedArgs() {
    Properties overrides =
        CliArguments.parse(new String[] {"db-url=jdbc:test", "--db-driver"}).connectionOverrides();
    assertTrue(overrides.isEmpty());
  }

  @Test
  @DisplayName("requireKnownArguments: フラグとDB接続情報の--キー=値形式の引数（値が空でも）は受け入れる")
  void testRequireKnownArgumentsAcceptsKnownArguments() {
    CliArguments args =
        CliArguments.parse(
            new String[] {
              "--check",
              "--rm-dist",
              "--db-driver=org.postgresql.Driver",
              "--db-url=jdbc:postgresql://localhost:5432/testdb",
              "--db-username=",
              "--db-password=pass"
            });

    assertDoesNotThrow(args::requireKnownArguments);
  }

  @Test
  @DisplayName("requireKnownArguments: 解釈できない引数（書き誤り・値の無い--db-*・値付きのフラグ）をすべて示して誤りとする")
  void testRequireKnownArgumentsRejectsUnknownArguments() {
    CliArguments args =
        CliArguments.parse(new String[] {"--chek", "--rm-dist", "--db-url", "--check=true", "x"});

    UserCorrectableException e =
        assertThrows(UserCorrectableException.class, args::requireKnownArguments);
    assertTrue(e.getMessage().contains("Unknown argument: --chek, --db-url, --check=true, x"));
    assertTrue(e.getMessage().contains("--db-url=<value>"));
  }

  @Test
  @DisplayName("parse: 解釈できない引数があっても、実行モードの判定はできる（誤りの報告はrequireKnownArgumentsで行う）")
  void testParseDoesNotThrowOnUnknownArguments() {
    CliArguments args = CliArguments.parse(new String[] {"--chek", "--rm-dist"});

    assertFalse(args.isCheck());
    assertTrue(args.isRmDist());
  }

  @Test
  @DisplayName("settingOverrides: 設定ファイルのキーごとに、キーから導いたCLI引数名で上書き値を取り込む（READMEの記載順）")
  void testSettingOverridesFromCliArgs() {
    Map<String, SettingOverride> overrides =
        CliArguments.parse(
                new String[] {
                  "--annotation-path=conf/annotations.yml",
                  "--schema=sample",
                  "--table=!flyway_schema_history,*_bk",
                  "--output-path=./docs/db",
                  "--chunk-size=100",
                  "--er-diagram-max-nodes=0",
                  "--output-objects=trigger"
                })
            .settingOverrides();

    assertEquals(
        List.of(
            "schema",
            "table",
            "outputPath",
            "chunkSize",
            "erDiagramMaxNodes",
            "outputObjects",
            "annotationPath"),
        List.copyOf(overrides.keySet()));
    assertEquals(new SettingOverride("sample", "--schema"), overrides.get("schema"));
    assertEquals(
        new SettingOverride("!flyway_schema_history,*_bk", "--table"), overrides.get("table"));
    assertEquals(new SettingOverride("./docs/db", "--output-path"), overrides.get("outputPath"));
    assertEquals(new SettingOverride("100", "--chunk-size"), overrides.get("chunkSize"));
    assertEquals(
        new SettingOverride("0", "--er-diagram-max-nodes"), overrides.get("erDiagramMaxNodes"));
    assertEquals(
        new SettingOverride("trigger", "--output-objects"), overrides.get("outputObjects"));
    assertEquals(
        new SettingOverride("conf/annotations.yml", "--annotation-path"),
        overrides.get("annotationPath"));
  }

  @Test
  @DisplayName("settingOverrides: 空の値は指定しなかったものとして扱い、設定ファイルの値に委ねる")
  void testSettingOverridesIgnoresBlankValues() {
    Map<String, SettingOverride> overrides =
        CliArguments.parse(new String[] {"--schema=", "--table= "}).settingOverrides();

    assertTrue(overrides.isEmpty());
  }

  @Test
  @DisplayName("requireKnownArguments: 実行時設定の--キー=値形式の引数を受け入れ、書き誤りは誤りとして使える引数に含めて示す")
  void testRequireKnownArgumentsAcceptsSettingArguments() {
    assertDoesNotThrow(
        CliArguments.parse(
                new String[] {"--output-path=./docs", "--er-diagram-max-nodes=0", "--table="})
            ::requireKnownArguments);

    UserCorrectableException e =
        assertThrows(
            UserCorrectableException.class,
            CliArguments.parse(new String[] {"--outputPath=./docs"})::requireKnownArguments);
    assertTrue(e.getMessage().contains("Unknown argument: --outputPath=./docs"));
    assertTrue(e.getMessage().contains("--output-path=<value>"));
    assertTrue(e.getMessage().contains("--annotation-path=<value>"));
  }
}
