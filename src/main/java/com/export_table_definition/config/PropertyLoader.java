package com.export_table_definition.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
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
   */
  public static String getString(String fileName, String key) {
    return getResourceBundle(fileName).getString(key);
  }

  /**
   * プロパティファイルの読み込みを行うメソッド（カンマ区切りの値をリストで取得）<br>
   * 各要素の前後の空白は除去する。{@code schema=public, sample}のようにカンマの後に空白を入れた場合に、 {@code "
   * sample"}が別の名前として扱われ、対象から黙って外れてしまうことを防ぐため
   *
   * @param fileName プロパティファイルのファイル名
   * @param key 取得するキー
   * @return キーに対応するカンマ区切りの値を分割し、前後の空白を除去したリスト（空要素は含めない）
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
   */
  public static int getInt(String fileName, String key, int defaultValue) {
    try {
      final String value = getString(fileName, key);
      if (value == null || value.isBlank()) {
        return defaultValue;
      }
      return Integer.parseInt(value.trim());
    } catch (MissingResourceException | NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * プロパティファイルの読み込みを行うメソッド（キャッシュを利用）
   *
   * @param fileName プロパティファイルのファイル名
   * @return プロパティファイルを読み込んだResourceBundleオブジェクト
   */
  public static ResourceBundle getResourceBundle(String fileName) {
    return CACHE.computeIfAbsent(
        fileName,
        f -> {
          try {
            return ResourceBundle.getBundle(
                f,
                Locale.JAPAN,
                new URLClassLoader(new URL[] {getPropertiesFileDir().toURI().toURL()}));
          } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to get the URL of the property file directory.", e);
          }
        });
  }

  /**
   * プロパティファイルの読み込みを行うメソッド（Propertiesオブジェクトで取得）
   *
   * @param fileName プロパティファイルのファイル名
   * @return プロパティファイルを読み込んだPropertiesオブジェクト
   */
  public static Properties getProperties(String fileName) {
    final Properties props = new Properties();
    final ResourceBundle res = getResourceBundle(fileName);
    res.keySet().stream().forEach(key -> props.setProperty(key, res.getString(key)));
    return props;
  }

  /**
   * プロパティファイルが存在するディレクトリを取得するメソッド
   *
   * @return プロパティファイルが存在するディレクトリのFileオブジェクト
   * @throws FileNotFoundException プロパティファイルが存在するディレクトリが見つからない場合
   */
  private static File getPropertiesFileDir() throws FileNotFoundException {
    return Stream.of(Path.of("conf"), Path.of("src", "main", "resources", "conf"))
        .filter(Files::exists)
        .findFirst()
        .map(Path::toFile)
        .orElseThrow(() -> new FileNotFoundException("conf directory does not exist."));
  }
}
