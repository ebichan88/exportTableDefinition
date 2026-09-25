package com.export_table_definition;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CliArguments のフラグ判定・DB接続情報の上書き解決に関するテスト */
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
  @DisplayName("connectionOverrides: 対応するCLI引数・環境変数が無いキーは含まれない")
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
}
