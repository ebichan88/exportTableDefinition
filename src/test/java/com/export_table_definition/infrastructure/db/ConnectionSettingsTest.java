package com.export_table_definition.infrastructure.db;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.config.InvalidConfigurationException;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ConnectionSettings のDB接続情報の組み立て・検証（READMEに記載したmybatis.propertiesの仕様）に関するテスト */
public class ConnectionSettingsTest {

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

    assertTrue(e.getMessage().contains("driver is not set"));
    assertTrue(e.getMessage().contains("url is not set"));
  }

  @Test
  @DisplayName("of: 未知のキー（キー名の書き誤り等）は、書けるキーを添えて誤りとする")
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

    assertTrue(e.getMessage().contains("Unknown key: usrname"));
    assertTrue(e.getMessage().contains("username"));
  }

  @Test
  @DisplayName("merge: CLI引数・環境変数由来の値が設定ファイルの値より優先され、指定されなかったキーは設定ファイルの値を使う")
  void testOverridesTakePrecedenceOverBaseValues() {
    final ConnectionSettings settings =
        ConnectionSettings.merge(
            Map.of(
                "driver", "org.postgresql.Driver",
                "url", "jdbc:postgresql://localhost:5432/filedb",
                "username", "fileuser"),
            properties("url", "jdbc:postgresql://localhost:5432/clidb"));

    final Properties result = settings.toProperties();
    assertEquals("org.postgresql.Driver", result.getProperty("driver"));
    assertEquals("jdbc:postgresql://localhost:5432/clidb", result.getProperty("url"));
    assertEquals("fileuser", result.getProperty("username"));
  }

  @Test
  @DisplayName("merge: 設定ファイルが無くても、上書きする値だけで必須の項目がそろえば組み立てられる")
  void testBuildsFromOverridesOnly() {
    final ConnectionSettings settings =
        ConnectionSettings.merge(
            Map.of(),
            properties("driver", "org.postgresql.Driver", "url", "jdbc:postgresql://db/testdb"));

    assertEquals("jdbc:postgresql://db/testdb", settings.toProperties().getProperty("url"));
  }

  @Test
  @DisplayName("merge: 上書きした結果も検証し、必須の項目が欠けていれば誤りとする")
  void testValidatesMergedValues() {
    assertThrows(
        InvalidConfigurationException.class,
        () -> ConnectionSettings.merge(Map.of(), properties("username", "user")));
  }
}
