package com.export_table_definition.config;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * プロパティファイルに関するユーティリティクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class PropertyLoader {

  private static final Map<String, ResourceBundle> CACHE = new ConcurrentHashMap<>();

  /** コンストラクタ（インスタンス化不可） */
  private PropertyLoader() {}

  /**
   * プロパティファイルの読み込みを行うメソッド
   *
   * @param fileName プロパティファイルのファイル名
   * @param key 取得するキー
   * @return キーに対応する値
   * @throws InvalidConfigurationException 設定ファイル、またはキーが存在しない場合
   */
  public static String getString(String fileName, String key) {
    final ResourceBundle bundle = getResourceBundle(fileName);
    if (!bundle.containsKey(key)) {
      throw new InvalidConfigurationException(
          "Required property is not set. [file=" + fileName + ".properties, key=" + key + "]");
    }
    return bundle.getString(key);
  }

  /**
   * プロパティファイルの読み込みを行うメソッド（カンマ区切りの値をリストで取得）<br>
   * 各要素の前後の空白は除去する。{@code schema=public, sample}のようにカンマの後に空白を入れた場合に、 {@code "
   * sample"}が別の名前として扱われ、対象から黙って外れてしまうことを防ぐため
   *
   * @param fileName プロパティファイルのファイル名
   * @param key 取得するキー
   * @return キーに対応するカンマ区切りの値を分割し、前後の空白を除去したリスト（空要素は含めない）
   * @throws InvalidConfigurationException 設定ファイル、またはキーが存在しない場合
   */
  public static List<String> getList(String fileName, String key) {
    return Arrays.stream(getString(fileName, key).split(","))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  /**
   * プロパティファイルの読み込みを行うメソッド（数値で取得）<br>
   * キーが存在しない・空・数値以外の場合はデフォルト値を返す
   *
   * @param fileName プロパティファイルのファイル名
   * @param key 取得するキー
   * @param defaultValue キーに対応する値が取得できない場合のデフォルト値
   * @return キーに対応する数値。取得できない場合はデフォルト値
   * @throws InvalidConfigurationException 設定ファイルが存在しない場合
   */
  public static int getInt(String fileName, String key, int defaultValue) {
    final ResourceBundle bundle = getResourceBundle(fileName);
    if (!bundle.containsKey(key)) {
      return defaultValue;
    }
    final String value = bundle.getString(key);
    if (value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * プロパティファイルの読み込みを行うメソッド（キャッシュを利用）
   *
   * @param fileName プロパティファイルのファイル名
   * @return プロパティファイルを読み込んだResourceBundleオブジェクト
   * @throws InvalidConfigurationException {@code conf}ディレクトリ、または設定ファイルが存在しない場合
   */
  public static ResourceBundle getResourceBundle(String fileName) {
    return CACHE.computeIfAbsent(fileName, PropertyLoader::loadResourceBundle);
  }

  /**
   * プロパティファイルの読み込みを行うメソッド（Propertiesオブジェクトで取得）
   *
   * @param fileName プロパティファイルのファイル名
   * @return プロパティファイルを読み込んだPropertiesオブジェクト
   * @throws InvalidConfigurationException {@code conf}ディレクトリ、または設定ファイルが存在しない場合
   */
  public static Properties getProperties(String fileName) {
    final Properties props = new Properties();
    final ResourceBundle res = getResourceBundle(fileName);
    res.keySet().stream().forEach(key -> props.setProperty(key, res.getString(key)));
    return props;
  }

  /**
   * プロパティファイルを読み込むメソッド（キャッシュに無い場合のみ呼ばれる）
   *
   * @param fileName プロパティファイルのファイル名
   * @return プロパティファイルを読み込んだResourceBundleオブジェクト
   * @throws InvalidConfigurationException {@code conf}ディレクトリ、または設定ファイルが存在しない場合
   */
  private static ResourceBundle loadResourceBundle(String fileName) {
    final Path propertiesFileDir = getPropertiesFileDir();
    final URL propertiesFileDirUrl;
    try {
      propertiesFileDirUrl = propertiesFileDir.toUri().toURL();
    } catch (MalformedURLException e) {
      // 実在するディレクトリのパスから組み立てるため、設定誤りでは起こり得ない（想定外の不具合として扱う）
      throw new UncheckedIOException("Failed to get the URL of the property file directory.", e);
    }
    try {
      return ResourceBundle.getBundle(
          fileName, Locale.JAPAN, new URLClassLoader(new URL[] {propertiesFileDirUrl}));
    } catch (MissingResourceException e) {
      throw new InvalidConfigurationException(
          "Property file does not exist. [file="
              + propertiesFileDir.resolve(fileName + ".properties")
              + "]",
          e);
    }
  }

  /**
   * プロパティファイルが存在するディレクトリを取得するメソッド
   *
   * @return プロパティファイルが存在するディレクトリ
   * @throws InvalidConfigurationException プロパティファイルが存在するディレクトリが見つからない場合
   */
  private static Path getPropertiesFileDir() {
    return Stream.of(Path.of("conf"), Path.of("src", "main", "resources", "conf"))
        .filter(Files::exists)
        .findFirst()
        .orElseThrow(
            () ->
                new InvalidConfigurationException(
                    "conf directory does not exist. Run the jar from the directory containing conf."));
  }
}
