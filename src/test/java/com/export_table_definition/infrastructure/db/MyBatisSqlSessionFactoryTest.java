package com.export_table_definition.infrastructure.db;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.config.InvalidConfigurationException;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MyBatisSqlSessionFactory のDB接続情報の検証（READMEに記載したmybatis.propertiesの仕様）に関するテスト */
public class MyBatisSqlSessionFactoryTest {

  private static Properties properties(String... keyValues) {
    final Properties properties = new Properties();
    for (int i = 0; i < keyValues.length; i += 2) {
      properties.setProperty(keyValues[i], keyValues[i + 1]);
    }
    return properties;
  }

  @Test
  @DisplayName("requireValidConnectionSettings: driver・urlが指定されていれば、username・passwordは空でもよい")
  void testAcceptsSettingsWithoutCredentials() {
    assertDoesNotThrow(
        () ->
            MyBatisSqlSessionFactory.requireValidConnectionSettings(
                properties(
                    "driver", "org.postgresql.Driver",
                    "url", "jdbc:postgresql://localhost:5432/testdb",
                    "username", "",
                    "password", "")));
  }

  @Test
  @DisplayName("requireValidConnectionSettings: driver・urlが未指定（キーの省略・空）の場合は、すべて示して誤りとする")
  void testRejectsMissingRequiredSettings() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () ->
                MyBatisSqlSessionFactory.requireValidConnectionSettings(
                    properties("url", " ", "username", "user")));

    assertTrue(e.getMessage().contains("driver is not set"));
    assertTrue(e.getMessage().contains("url is not set"));
  }

  @Test
  @DisplayName("requireValidConnectionSettings: 未知のキー（キー名の書き誤り等）は、書けるキーを添えて誤りとする")
  void testRejectsUnknownKey() {
    InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () ->
                MyBatisSqlSessionFactory.requireValidConnectionSettings(
                    properties(
                        "driver", "org.postgresql.Driver",
                        "url", "jdbc:postgresql://localhost:5432/testdb",
                        "usrname", "user")));

    assertTrue(e.getMessage().contains("Unknown key: usrname"));
    assertTrue(e.getMessage().contains("username"));
  }
}
