package com.export_table_definition.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.MissingResourceException;
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
  @DisplayName("getString: キーに対応する値を返す")
  void testGetStringReturnsConfiguredValue() {
    assertEquals("hello", PropertyLoader.getString(FIXTURE, "stringValue"));
  }

  @Test
  @DisplayName("getString: 存在しないキーの場合はMissingResourceExceptionをスローする")
  void testGetStringMissingKeyThrows() {
    assertThrows(
        MissingResourceException.class, () -> PropertyLoader.getString(FIXTURE, "doesNotExist"));
  }

  @Test
  @DisplayName("getList: カンマ区切りの値を分割し、各要素の前後の空白を除去して空要素を除く")
  void testGetListSplitsCommaSeparatedAndStripsElements() {
    assertEquals(
        List.of("alpha", "beta", "gamma", "delta"), PropertyLoader.getList(FIXTURE, "csvValue"));
  }

  @Test
  @DisplayName("getList: 値が空の場合は空リストを返す")
  void testGetListOnBlankValueReturnsEmptyList() {
    assertEquals(List.of(), PropertyLoader.getList(FIXTURE, "blankValue"));
  }

  @Test
  @DisplayName("getInt: 数値文字列の場合はパースした値を返す")
  void testGetIntReturnsParsedValue() {
    assertEquals(42, PropertyLoader.getInt(FIXTURE, "intValid", 999));
  }

  @Test
  @DisplayName("getInt: 値が空の場合はデフォルト値を返す")
  void testGetIntFallsBackOnBlankValue() {
    assertEquals(999, PropertyLoader.getInt(FIXTURE, "intBlank", 999));
  }

  @Test
  @DisplayName("getInt: 数値に変換できない場合はデフォルト値を返す")
  void testGetIntFallsBackOnNonNumericValue() {
    assertEquals(999, PropertyLoader.getInt(FIXTURE, "intInvalid", 999));
  }

  @Test
  @DisplayName("getInt: キー自体が存在しない場合はデフォルト値を返す")
  void testGetIntFallsBackOnMissingKey() {
    assertEquals(999, PropertyLoader.getInt(FIXTURE, "doesNotExist", 999));
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
