package com.export_table_definition.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PropertyLoader のプロパティファイル読み込みに関するテスト<br>
 * PropertyLoaderはプロパティファイルの探索先ディレクトリ（{@code conf} または {@code
 * src/main/resources/conf}）をハードコードで解決する設計のため、 専用のテスト用プロパティファイル（{@code
 * src/main/resources/conf/PropertyLoaderTestFixture.properties}）を 用意し、実際のファイルシステム経由での読み込みを検証する
 */
public class PropertyLoaderTest {

  private static final String FIXTURE = "PropertyLoaderTestFixture";

  @Test
  @DisplayName("load: プロパティファイルの全キー・値を、キーと値の組として返す")
  void testLoadReturnsAllKeyValuePairs() {
    assertEquals(Map.of("stringValue", "hello", "intValid", "42"), PropertyLoader.load(FIXTURE));
  }

  @Test
  @DisplayName("load: プロパティファイル自体が存在しない場合は、ファイル名を含むInvalidConfigurationExceptionをスローする")
  void testLoadMissingFileThrows() {
    final InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class, () -> PropertyLoader.load("DoesNotExistFixture"));
    assertTrue(e.getMessage().contains("DoesNotExistFixture.properties"));
  }
}
