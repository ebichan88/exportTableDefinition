package com.export_table_definition.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import java.util.ResourceBundle;
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
  @DisplayName("getResourceBundle: 設定ファイル自体が存在しない場合は、ファイル名を含むInvalidConfigurationExceptionをスローする")
  void testGetResourceBundleMissingFileThrows() {
    final InvalidConfigurationException e =
        assertThrows(
            InvalidConfigurationException.class,
            () -> PropertyLoader.getResourceBundle("DoesNotExistFixture"));
    assertTrue(e.getMessage().contains("DoesNotExistFixture.properties"));
  }

  @Test
  @DisplayName("getResourceBundle: 同一ファイル名の呼び出しはキャッシュされ、同一インスタンスを返す")
  void testGetResourceBundleIsCached() {
    ResourceBundle first = PropertyLoader.getResourceBundle(FIXTURE);
    ResourceBundle second = PropertyLoader.getResourceBundle(FIXTURE);
    assertSame(first, second);
  }

  @Test
  @DisplayName("getProperties: 全キー・値をPropertiesオブジェクトとして返す")
  void testGetPropertiesReturnsAllKeyValuePairs() {
    Properties props = PropertyLoader.getProperties(FIXTURE);
    assertEquals("hello", props.getProperty("stringValue"));
    assertEquals("42", props.getProperty("intValid"));
  }
}
