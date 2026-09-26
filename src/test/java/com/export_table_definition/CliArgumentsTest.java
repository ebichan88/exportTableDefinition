package com.export_table_definition;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.ExportTableDefinitionProperties.SettingOverride;
import com.export_table_definition.shared.exception.UserCorrectableException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CliArguments のフラグ判定・DB接続情報と実行時設定の上書き解決・解釈できない引数・環境変数の検知に関するテスト */
public class CliArgumentsTest {

  /** 環境変数が1つも設定されていない状態（テストを実行する環境の環境変数に左右されないようにする） */
  private static final Map<String, String> NO_ENV = Map.of();

  @Test
  @DisplayName("parse: --checkが無い場合はisCheck=false、--rm-distが無い場合はisRmDist=false")
  void testParseFlagsAbsent() {
    CliArguments args = CliArguments.parse(new String[] {}, NO_ENV);
    assertFalse(args.isCheck());
    assertFalse(args.isRmDist());
  }

  @Test
  @DisplayName("parse: --check・--rm-distが指定されている場合はそれぞれtrueを返す")
  void testParseFlagsPresent() {
    CliArguments args = CliArguments.parse(new String[] {"--check", "--rm-dist"}, NO_ENV);
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
            },
            NO_ENV);

    Properties overrides = args.connectionOverrides();
    assertEquals("org.postgresql.Driver", overrides.getProperty("driver"));
    assertEquals("jdbc:postgresql://localhost:5432/testdb", overrides.getProperty("url"));
    assertEquals("user", overrides.getProperty("username"));
    assertEquals("pass", overrides.getProperty("password"));
  }

  @Test
  @DisplayName("connectionOverrides: 対応するCLI引数・環境変数が無いキーは含まれない")
  void testConnectionOverridesEmptyWhenNothingSpecified() {
    Properties overrides = CliArguments.parse(new String[] {}, NO_ENV).connectionOverrides();
    assertTrue(overrides.isEmpty());
  }

  @Test
  @DisplayName("connectionOverrides: 空文字を指定した場合は上書きしない")
  void testConnectionOverridesIgnoresBlankValue() {
    Properties overrides =
        CliArguments.parse(new String[] {"--db-url="}, NO_ENV).connectionOverrides();
    assertFalse(overrides.containsKey("url"));
  }

  @Test
  @DisplayName("connectionOverrides: '--'で始まらない、または'='を含まない引数は無視する")
  void testConnectionOverridesIgnoresMalformedArgs() {
    Properties overrides =
        CliArguments.parse(new String[] {"db-url=jdbc:test", "--db-driver"}, NO_ENV)
            .connectionOverrides();
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
            },
            NO_ENV);

    assertDoesNotThrow(args::requireKnownArguments);
  }

  @Test
  @DisplayName("requireKnownArguments: 解釈できない引数（書き誤り・値の無い--db-*・値付きのフラグ）をすべて示して誤りとする")
  void testRequireKnownArgumentsRejectsUnknownArguments() {
    CliArguments args =
        CliArguments.parse(
            new String[] {"--chek", "--rm-dist", "--db-url", "--check=true", "x"}, NO_ENV);

    UserCorrectableException e =
        assertThrows(UserCorrectableException.class, args::requireKnownArguments);
    assertTrue(e.getMessage().contains("Unknown argument: --chek, --db-url, --check=true, x"));
    assertTrue(e.getMessage().contains("--db-url=<value>"));
  }

  @Test
  @DisplayName("parse: 解釈できない引数があっても、実行モードの判定はできる（誤りの報告はrequireKnownArgumentsで行う）")
  void testParseDoesNotThrowOnUnknownArguments() {
    CliArguments args = CliArguments.parse(new String[] {"--chek", "--rm-dist"}, NO_ENV);

    assertFalse(args.isCheck());
    assertTrue(args.isRmDist());
  }

  @Test
  @DisplayName("connectionOverrides: CLI引数が無い項目は環境変数から取り込み、両方ある項目はCLI引数を優先する")
  void testConnectionOverridesFromEnvironment() {
    Properties overrides =
        CliArguments.parse(
                new String[] {"--db-url=jdbc:cli"},
                Map.of("DB_URL", "jdbc:env", "DB_USERNAME", "env-user"))
            .connectionOverrides();

    assertEquals("jdbc:cli", overrides.getProperty("url"));
    assertEquals("env-user", overrides.getProperty("username"));
  }

  @Test
  @DisplayName("connectionOverrides: 空のCLI引数は指定しなかったものとして扱い、環境変数の値を用いる")
  void testConnectionOverridesFallsBackToEnvironmentWhenCliArgIsBlank() {
    Properties overrides =
        CliArguments.parse(new String[] {"--db-url="}, Map.of("DB_URL", "jdbc:env"))
            .connectionOverrides();

    assertEquals("jdbc:env", overrides.getProperty("url"));
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
                },
                NO_ENV)
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
  @DisplayName("settingOverrides: ETD_接頭辞の環境変数から取り込み、CLI引数と両方ある項目はCLI引数を優先する")
  void testSettingOverridesFromEnvironment() {
    Map<String, SettingOverride> overrides =
        CliArguments.parse(
                new String[] {"--output-path=./cli"},
                Map.of(
                    "ETD_OUTPUT_PATH", "./env",
                    "ETD_ER_DIAGRAM_MAX_NODES", "50",
                    "ETD_TABLE", "!tmp_*"))
            .settingOverrides();

    assertEquals(new SettingOverride("./cli", "--output-path"), overrides.get("outputPath"));
    assertEquals(
        new SettingOverride("50", "ETD_ER_DIAGRAM_MAX_NODES"), overrides.get("erDiagramMaxNodes"));
    assertEquals(new SettingOverride("!tmp_*", "ETD_TABLE"), overrides.get("table"));
  }

  @Test
  @DisplayName("settingOverrides: 空の値は指定しなかったものとして扱い、次の指定元（環境変数→設定ファイル）へ委ねる")
  void testSettingOverridesIgnoresBlankValues() {
    Map<String, SettingOverride> overrides =
        CliArguments.parse(
                new String[] {"--schema=", "--table= "},
                Map.of("ETD_SCHEMA", "sample", "ETD_OUTPUT_PATH", ""))
            .settingOverrides();

    assertEquals(new SettingOverride("sample", "ETD_SCHEMA"), overrides.get("schema"));
    assertFalse(overrides.containsKey("table"));
    assertFalse(overrides.containsKey("outputPath"));
  }

  @Test
  @DisplayName("settingOverrides: ETD_接頭辞の無い環境変数（SCHEMA等）は取り込まない")
  void testSettingOverridesIgnoresEnvironmentWithoutPrefix() {
    CliArguments args =
        CliArguments.parse(new String[] {}, Map.of("SCHEMA", "public", "OUTPUT_PATH", "/tmp"));

    assertTrue(args.settingOverrides().isEmpty());
    assertDoesNotThrow(args::requireKnownArguments);
  }

  @Test
  @DisplayName("requireKnownArguments: 実行時設定の--キー=値形式の引数を受け入れ、書き誤りは誤りとして使える引数に含めて示す")
  void testRequireKnownArgumentsAcceptsSettingArguments() {
    assertDoesNotThrow(
        CliArguments.parse(
                new String[] {"--output-path=./docs", "--er-diagram-max-nodes=0", "--table="},
                NO_ENV)
            ::requireKnownArguments);

    UserCorrectableException e =
        assertThrows(
            UserCorrectableException.class,
            CliArguments.parse(new String[] {"--outputPath=./docs"}, NO_ENV)
                ::requireKnownArguments);
    assertTrue(e.getMessage().contains("Unknown argument: --outputPath=./docs"));
    assertTrue(e.getMessage().contains("--output-path=<value>"));
    assertTrue(e.getMessage().contains("--annotation-path=<value>"));
  }

  @Test
  @DisplayName("requireKnownArguments: ETD_接頭辞の未知の環境変数（書き誤り）は、使える環境変数を添えて誤りとする")
  void testRequireKnownArgumentsRejectsUnknownEnvironmentVariables() {
    CliArguments args =
        CliArguments.parse(
            new String[] {"--chek"},
            Map.of("ETD_OUTPUTPATH", "./docs", "ETD_SCHEMAS", "sample", "ETD_SCHEMA", "sample"));

    UserCorrectableException e =
        assertThrows(UserCorrectableException.class, args::requireKnownArguments);
    assertTrue(e.getMessage().contains("Unknown argument: --chek"));
    assertTrue(
        e.getMessage().contains("Unknown environment variable: ETD_OUTPUTPATH, ETD_SCHEMAS"));
    assertTrue(e.getMessage().contains("ETD_OUTPUT_PATH"));
    assertFalse(args.settingOverrides().containsKey("outputPath"));
  }
}
