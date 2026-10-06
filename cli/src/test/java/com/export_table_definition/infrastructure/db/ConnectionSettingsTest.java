package com.export_table_definition.infrastructure.db;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.config.InvalidConfigurationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ConnectionSettings のDB接続情報の組み立て・検証（READMEに記載したconfig.ymlのdatabase・パスワードの環境変数の仕様）に関するテスト */
public class ConnectionSettingsTest {

  /** READMEに記載した環境変数名（実装の定数を参照せず、仕様として固定する） */
  private static final String PASSWORD_ENVIRONMENT_VARIABLE = "EXPORT_TABLE_DEFINITION_DB_PASSWORD";

  private static final Map<String, String> BASE_VALUES =
      Map.of(
          "driver", "org.postgresql.Driver",
          "url", "jdbc:postgresql://localhost:5432/testdb",
          "username", "user");

  private static Properties properties(String... keyValues) {
    final Properties properties = new Properties();
    for (int i = 0; i < keyValues.length; i += 2) {
      properties.setProperty(keyValues[i], keyValues[i + 1]);
    }
    return properties;
  }

  @Test
  @DisplayName("of: driver・urlが指定されていれば、username・passwordは空でもよい")
  void testAcceptsSettingsWithoutCredentials() {
    assertDoesNotThrow(
        () ->
            ConnectionSettings.of(
                Map.of(
                    "driver", "org.postgresql.Driver",
                    "url", "jdbc:postgresql://localhost:5432/testdb",
                    "username", "",
                    "password", "")));
  }

  @Test
  @DisplayName("of: driver・urlが未指定（キーの省略・空）の場合は、すべて示して誤りとする")
  void testRejectsMissingRequiredSettings() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () -> ConnectionSettings.of(Map.of("url", " ", "username", "user")));

    assertTrue(e.getMessage().contains("database.driver is not set"));
    assertTrue(e.getMessage().contains("--db-driver"));
    assertTrue(e.getMessage().contains("database.url is not set"));
    assertTrue(e.getMessage().contains("--db-url"));
  }

  @Test
  @DisplayName("of: 未知のキー（キー名の書き誤り等）は、設定ファイルでの位置で示し、書けるキーを添えて誤りとする")
  void testRejectsUnknownKey() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () ->
                ConnectionSettings.of(
                    Map.of(
                        "driver", "org.postgresql.Driver",
                        "url", "jdbc:postgresql://localhost:5432/testdb",
                        "usrname", "user")));

    assertTrue(e.getMessage().contains("Unknown key: database.usrname"));
    assertTrue(e.getMessage().contains("database.username"));
    assertFalse(e.getMessage().contains("database.password"));
  }

  @Test
  @DisplayName("merge: CLI引数由来の値が設定ファイルの値より優先され、指定されなかったキーは設定ファイルの値を使う")
  void testOverridesTakePrecedenceOverBaseValues() {
    final ConnectionSettings settings =
        ConnectionSettings.merge(
            Map.of(
                "driver", "org.postgresql.Driver",
                "url", "jdbc:postgresql://localhost:5432/filedb",
                "username", "fileuser"),
            properties("url", "jdbc:postgresql://localhost:5432/clidb"),
            Map.of());

    final Properties result = settings.toProperties();
    assertEquals("org.postgresql.Driver", result.getProperty("driver"));
    assertEquals("jdbc:postgresql://localhost:5432/clidb", result.getProperty("url"));
    assertEquals("fileuser", result.getProperty("username"));
  }

  @Test
  @DisplayName("merge: 設定ファイルにdatabaseを書いていなくても、上書きする値だけで必須の項目がそろえば組み立てられる")
  void testBuildsFromOverridesOnly() {
    final ConnectionSettings settings =
        ConnectionSettings.merge(
            Map.of(),
            properties("driver", "org.postgresql.Driver", "url", "jdbc:postgresql://db/testdb"),
            Map.of());

    assertEquals("jdbc:postgresql://db/testdb", settings.toProperties().getProperty("url"));
  }

  @Test
  @DisplayName("merge: 上書きした結果も検証し、必須の項目が欠けていれば誤りとする")
  void testValidatesMergedValues() {
    assertThrows(
        InvalidConfigurationException.class,
        () -> ConnectionSettings.merge(Map.of(), properties("username", "user"), Map.of()));
  }

  @Test
  @DisplayName("merge: パスワードは環境変数から読む")
  void testReadsPasswordFromEnvironment() {
    final ConnectionSettings settings =
        ConnectionSettings.merge(
            BASE_VALUES, new Properties(), Map.of(PASSWORD_ENVIRONMENT_VARIABLE, "envpass"));

    assertEquals("envpass", settings.toProperties().getProperty("password"));
  }

  @Test
  @DisplayName("merge: CLI引数のパスワードが環境変数より優先される")
  void testCliPasswordTakesPrecedenceOverEnvironment() {
    final ConnectionSettings settings =
        ConnectionSettings.merge(
            BASE_VALUES,
            properties("password", "clipass"),
            Map.of(PASSWORD_ENVIRONMENT_VARIABLE, "envpass"));

    assertEquals("clipass", settings.toProperties().getProperty("password"));
  }

  @Test
  @DisplayName("merge: 環境変数の値が空の場合は、指定しなかったものとして扱う")
  void testIgnoresBlankEnvironmentPassword() {
    final ConnectionSettings settings =
        ConnectionSettings.merge(
            BASE_VALUES, new Properties(), Map.of(PASSWORD_ENVIRONMENT_VARIABLE, " "));

    assertNull(settings.toProperties().getProperty("password"));
  }

  @Test
  @DisplayName("merge: 設定ファイルのpasswordは、値が空でも環境変数への移行を案内して誤りとする")
  void testRejectsPasswordInFile() {
    final Map<String, String> baseValues = new HashMap<>(BASE_VALUES);
    baseValues.put("password", "");

    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () ->
                ConnectionSettings.merge(
                    baseValues,
                    properties("password", "clipass"),
                    Map.of(PASSWORD_ENVIRONMENT_VARIABLE, "envpass")));

    assertTrue(
        e.getMessage().contains("database.password cannot be set in the configuration file"));
    assertTrue(e.getMessage().contains(PASSWORD_ENVIRONMENT_VARIABLE));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:postgresql://localhost:5432/testdb?user=app&password=s3cret",
        "jdbc:postgresql://localhost:5432/testdb?sslpassword=s3cret",
        "jdbc:oracle:thin:scott/s3cret@//localhost:1521/orclpdb",
        "jdbc:oracle:thin:@//localhost:1521/orclpdb;PASSWORD = s3cret"
      })
  @DisplayName("merge: 設定ファイルのurlに埋め込んだパスワードは、値を示さずに誤りとする")
  void testRejectsPasswordInFileUrl(String url) {
    final Map<String, String> baseValues = new HashMap<>(BASE_VALUES);
    baseValues.put("url", url);

    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () -> ConnectionSettings.merge(baseValues, new Properties(), Map.of()));

    assertTrue(e.getMessage().contains("database.url in the configuration file"));
    assertFalse(e.getMessage().contains("s3cret"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:postgresql://localhost:5432/testdb?sslmode=require",
        "jdbc:oracle:thin:@//localhost:1521/orclpdb",
        "jdbc:oracle:thin:@localhost:1521:orcl"
      })
  @DisplayName("merge: パスワードを含まないurlは受け入れる")
  void testAcceptsUrlWithoutPassword(String url) {
    final Map<String, String> baseValues = new HashMap<>(BASE_VALUES);
    baseValues.put("url", url);

    assertDoesNotThrow(() -> ConnectionSettings.merge(baseValues, new Properties(), Map.of()));
  }
}
