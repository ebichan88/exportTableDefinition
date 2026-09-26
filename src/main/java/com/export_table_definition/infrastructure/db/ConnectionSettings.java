package com.export_table_definition.infrastructure.db;

import com.export_table_definition.config.InvalidConfigurationException;
import com.export_table_definition.config.PropertyLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 検証済みのDB接続情報を表すクラス<br>
 * {@code conf/mybatis.properties}の値を、CLI引数の値で上書きしたもの。組み立てる時に検証するため、
 * インスタンスがあれば必須の項目がそろい、未知のキーを含まないことが保証される
 */
public final class ConnectionSettings {

  private static final Logger logger = LogManager.getLogger(ConnectionSettings.class);
  private static final String PROPERTY_BUNDLE_NAME = "mybatis";

  /** DB接続情報のキー（mybatis-config.xmlが参照する。conf/mybatis.properties・CLI引数で指定する） */
  private static final List<String> CONNECTION_KEYS =
      List.of("driver", "url", "username", "password");

  /** DB接続情報のうち、既定値が無く指定が必須のキー（username・passwordはDBの認証方式によっては空でよい） */
  private static final List<String> REQUIRED_CONNECTION_KEYS = List.of("driver", "url");

  private final Map<String, String> values;

  private ConnectionSettings(Map<String, String> values) {
    this.values = Map.copyOf(values);
  }

  /**
   * {@code conf/mybatis.properties}を読み込み、CLI引数の値で上書きしたDB接続情報を組み立てるメソッド<br>
   * {@code conf/mybatis.properties}が存在しない場合は、上書きする値だけで組み立てる（CLI引数のみで接続情報を賄うケースを許容するため）
   *
   * @param overrides 上書きする接続情報（CLI引数由来。未指定のキーは含まない）
   * @return 検証済みのDB接続情報
   * @throws InvalidConfigurationException 未知のキーがある場合や、必須の項目が未指定の場合
   */
  public static ConnectionSettings load(Properties overrides) {
    return merge(loadBaseProperties(), overrides);
  }

  /**
   * DB接続情報を組み立てるメソッド
   *
   * @param values DB接続情報のキーと値の組
   * @return 検証済みのDB接続情報
   * @throws InvalidConfigurationException 未知のキーがある場合や、必須の項目が未指定の場合（見つかった誤りをすべて示す）
   */
  public static ConnectionSettings of(Map<String, String> values) {
    requireValid(values);
    return new ConnectionSettings(values);
  }

  /**
   * {@code conf/mybatis.properties}の値を、上書きする値で上書きしてDB接続情報を組み立てるメソッド
   *
   * @param baseValues {@code conf/mybatis.properties}の値
   * @param overrides 上書きする接続情報
   * @return 検証済みのDB接続情報
   * @throws InvalidConfigurationException 未知のキーがある場合や、必須の項目が未指定の場合
   */
  static ConnectionSettings merge(Map<String, String> baseValues, Properties overrides) {
    final Map<String, String> values = new HashMap<>(baseValues);
    overrides.stringPropertyNames().forEach(key -> values.put(key, overrides.getProperty(key)));
    return of(values);
  }

  /**
   * mybatis-config.xmlのプレースホルダ（{@code ${driver}}等）へ渡す形に変換するメソッド
   *
   * @return DB接続情報のキーと値の組
   */
  Properties toProperties() {
    final Properties properties = new Properties();
    properties.putAll(values);
    return properties;
  }

  /**
   * DB接続情報を検証するメソッド<br>
   * 必須の項目が未指定のまま接続すると、置換されないプレースホルダ（{@code ${driver}}等）で接続を試みて 原因の分かりにくい失敗になるため、接続する前に報告する
   *
   * @param values DB接続情報のキーと値の組
   * @throws InvalidConfigurationException 未知のキーがある場合や、必須の項目が未指定の場合（見つかった誤りをすべて示す）
   */
  private static void requireValid(Map<String, String> values) {
    final List<String> errors = new ArrayList<>();
    final List<String> unknownKeys =
        values.keySet().stream().filter(key -> !CONNECTION_KEYS.contains(key)).sorted().toList();
    if (!unknownKeys.isEmpty()) {
      errors.add(
          "Unknown key: "
              + String.join(", ", unknownKeys)
              + " (available keys: "
              + String.join(", ", CONNECTION_KEYS)
              + ")");
    }
    REQUIRED_CONNECTION_KEYS.stream()
        .filter(key -> values.getOrDefault(key, "").isBlank())
        .forEach(
            key ->
                errors.add(
                    key
                        + " is not set. Set it in conf/"
                        + PROPERTY_BUNDLE_NAME
                        + ".properties, or with the --db-* argument."));
    if (!errors.isEmpty()) {
      throw new InvalidConfigurationException(
          "Invalid database connection settings."
              + errors.stream()
                  .map(error -> System.lineSeparator() + "  - " + error)
                  .collect(Collectors.joining()));
    }
  }

  /**
   * conf/mybatis.propertiesを読み込む<br>
   * ファイルが存在しない場合は空を返す（CLI引数のみで接続情報を賄うケースを許容するため）
   *
   * @return 読み込んだキーと値の組（ファイルが存在しない場合は空）
   */
  private static Map<String, String> loadBaseProperties() {
    try {
      return PropertyLoader.load(PROPERTY_BUNDLE_NAME);
    } catch (InvalidConfigurationException e) {
      logger.info("conf/mybatis.properties not found. Relying on the --db-* arguments only.");
      return Map.of();
    }
  }
}
