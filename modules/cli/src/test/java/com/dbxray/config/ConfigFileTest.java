package com.dbxray.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ConfigFile の設定ファイルの読み込み・解析に関するテスト */
public class ConfigFileTest {

  private static final Path PATH = Path.of("conf", "config.yml");

  @Test
  @DisplayName("load: ファイルを読み込み、最上位のキーと値の組を書いた順に返す")
  void testLoadReadsFile(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("config.yml");
    Files.writeString(
        file, "output:\n  path: ./docs\ntarget:\n  schemas: [sample]\n", StandardCharsets.UTF_8);

    ConfigFile configFile = ConfigFile.load(file);

    assertEquals(file, configFile.path());
    assertEquals(List.of("output", "target"), List.copyOf(configFile.root().keySet()));
    assertEquals(Map.of("path", "./docs"), configFile.section("output"));
  }

  @Test
  @DisplayName("load: ファイルが存在しない場合は、--configでの指定を案内し、パスを添えて誤りとする")
  void testLoadRejectsMissingFile(@TempDir Path dir) {
    Path file = dir.resolve("missing.yml");

    InvalidConfigurationException e =
        assertThrows(InvalidConfigurationException.class, () -> ConfigFile.load(file));

    assertTrue(e.getMessage().startsWith("Configuration file does not exist."));
    assertTrue(e.getMessage().contains("--config"));
    assertTrue(e.getMessage().contains(file.toAbsolutePath().normalize().toString()));
  }

  @Test
  @DisplayName("load: ディレクトリを指定した場合も、ファイルが存在しないものとして誤りとする")
  void testLoadRejectsDirectory(@TempDir Path dir) {
    InvalidConfigurationException e =
        assertThrows(InvalidConfigurationException.class, () -> ConfigFile.load(dir));

    assertTrue(e.getMessage().startsWith("Configuration file does not exist."));
  }

  @Test
  @DisplayName("parse: 空のファイル・コメントだけのファイルは、何も書いていないものとして扱う")
  void testParseEmptyFile() {
    assertEquals(Map.of(), ConfigFile.parse(PATH, "").root());
    assertEquals(Map.of(), ConfigFile.parse(PATH, "# comment only\n").root());
  }

  @Test
  @DisplayName("parse: YAMLとして解釈できない場合は、ファイルを添えて誤りとする")
  void testParseRejectsInvalidYaml() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () -> ConfigFile.parse(PATH, "output:\n  path: [unclosed\n"));

    assertTrue(e.getMessage().startsWith("Failed to parse the configuration file."));
    assertTrue(e.getMessage().contains(PATH.toString()));
    assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  @Test
  @DisplayName("parse: YAMLとして解釈できない場合も、誤りの行の内容（書き誤ったパスワード等）は報告に含めない")
  void testParseDoesNotQuoteInvalidLine() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () -> ConfigFile.parse(PATH, "database:\n  password: \"secret-value\n"));

    assertFalse(e.getMessage().contains("secret-value"), e.getMessage());
    // FailureReporterは原因の例外のメッセージも表示するため、引用を含む原因も渡さない
    assertNull(e.getCause());
  }

  @Test
  @DisplayName("parse: 最上位がキーと値の組でない場合は誤りとする")
  void testParseRejectsNonMappingRoot() {
    InvalidConfigurationException e =
        assertThrows(InvalidConfigurationException.class, () -> ConfigFile.parse(PATH, "- a\n"));

    assertTrue(e.getMessage().contains("must be a mapping of keys and values"));
  }

  @Test
  @DisplayName("parse: 文字列以外のキーも、未知のキーとして報告できるよう文字列にそろえる")
  void testParseConvertsKeysToString() {
    assertEquals(List.of("1"), List.copyOf(ConfigFile.parse(PATH, "1: a\n").root().keySet()));
  }

  @Test
  @DisplayName("section: キーの省略・値が空の場合は空、キーと値の組でない場合は誤りとする")
  void testSection() {
    ConfigFile configFile = ConfigFile.parse(PATH, "database:\noutput: ./docs\n");

    assertEquals(Map.of(), configFile.section("target"));
    assertEquals(Map.of(), configFile.section("database"));
    InvalidConfigurationException e =
        assertThrows(InvalidConfigurationException.class, () -> configFile.section("output"));
    assertTrue(e.getMessage().contains("output in " + PATH + " must be a mapping"));
  }

  @Test
  @DisplayName("scalarSection: 値を文字列にし、キーだけ書いた項目は空文字とする")
  void testScalarSection() {
    ConfigFile configFile =
        ConfigFile.parse(PATH, "database:\n  url: jdbc:x\n  username:\n  port: 5432\n");

    assertEquals(
        Map.of("url", "jdbc:x", "username", "", "port", "5432"),
        configFile.scalarSection("database"));
  }

  @Test
  @DisplayName("scalarSection: 項目の値がリスト・キーと値の組の場合は、項目の位置を示して誤りとする")
  void testScalarSectionRejectsNestedValue() {
    ConfigFile configFile = ConfigFile.parse(PATH, "database:\n  url: [a, b]\n");

    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class, () -> configFile.scalarSection("database"));

    assertTrue(e.getMessage().contains("database.url"));
    assertTrue(e.getMessage().contains("must be a single value"));
  }
}
