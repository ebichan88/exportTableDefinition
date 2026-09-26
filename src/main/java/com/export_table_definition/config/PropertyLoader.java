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
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code conf}ディレクトリのプロパティファイルを読み込むクラス<br>
 * ファイルの探索（{@code conf}、無ければ{@code src/main/resources/conf}）と読み込みだけを担い、中身をキーと値の組として返す。
 * どのキーを書けるか・既定値・値の形式といった設定項目の仕様と検証は、ファイルごとに読み込む側が持つ （{@code
 * ExportTableDefinition.properties}は{@code ExportTableDefinitionProperties}、{@code
 * mybatis.properties}は{@code MyBatisSqlSessionFactory}）
 */
public class PropertyLoader {

  /** コンストラクタ（インスタンス化不可） */
  private PropertyLoader() {}

  /**
   * プロパティファイルを読み込むメソッド
   *
   * @param fileName プロパティファイル名（{@code conf}配下。拡張子を除く）
   * @return プロパティファイルのキーと値の組（変更不可）
   * @throws InvalidConfigurationException {@code conf}ディレクトリ、またはプロパティファイルが存在しない場合
   */
  public static Map<String, String> load(String fileName) {
    final ResourceBundle bundle = loadResourceBundle(fileName);
    return bundle.keySet().stream()
        .collect(Collectors.toUnmodifiableMap(Function.identity(), bundle::getString));
  }

  /**
   * プロパティファイルを{@link ResourceBundle}として読み込むメソッド
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
