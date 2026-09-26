package com.export_table_definition.config;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * プロパティファイルに関するユーティリティクラス<br>
 * {@code conf}ディレクトリ配下のプロパティファイルの読み込みのみを担う。 設定項目の仕様（キー・既定値・値の形式）と検証は、ファイルごとに読み込む側が持つ
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
