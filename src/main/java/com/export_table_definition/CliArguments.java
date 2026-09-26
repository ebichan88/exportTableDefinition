package com.export_table_definition;

import com.export_table_definition.ExportTableDefinitionProperties.SettingOverride;
import com.export_table_definition.shared.exception.UserCorrectableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * コマンドライン引数の解析を行うクラス<br>
 * {@code --check}・{@code --rm-dist}フラグの判定と、DB接続情報・実行時設定（{@code
 * conf/ExportTableDefinition.properties}の設定値）の上書き値の解決（CLI引数・環境変数から）を担う。
 * 設定ファイルの読み込みと、上書きした値の検証は含まない（{@link ExportTableDefinitionProperties}が行う）。
 * 解釈できない引数・環境変数（書き誤り等）は、意図しないモード・設定で実行されないよう誤りとする（{@link #requireKnownArguments()}）
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
final class CliArguments {

  /** DB vs ドキュメントの差分検知モードを指定するCLIフラグ（値を持たないブールフラグ） */
  private static final String CHECK_FLAG = "--check";

  /** 書き込み前に出力先ディレクトリを事前に削除するCLIフラグ（値を持たないブールフラグ。{@code --check}指定時は無視される） */
  private static final String RM_DIST_FLAG = "--rm-dist";

  /** 値を持たないフラグ */
  private static final List<String> FLAGS = List.of(CHECK_FLAG, RM_DIST_FLAG);

  /** DB接続情報の上書きに対応するプロパティキーと、対応するCLI引数名・環境変数名（READMEの記載順） */
  private static final List<OverrideArg> CONNECTION_ARGS =
      List.of(
          new OverrideArg("driver", "--db-driver", "DB_DRIVER"),
          new OverrideArg("url", "--db-url", "DB_URL"),
          new OverrideArg("username", "--db-username", "DB_USERNAME"),
          new OverrideArg("password", "--db-password", "DB_PASSWORD"));

  /**
   * 実行時設定の上書きに使う環境変数名の接頭辞<br>
   * {@code SCHEMA}・{@code TABLE}のような汎用的な名前の環境変数を、CI等の環境に別の用途で設定されていたものまで
   * 拾ってしまわないよう接頭辞を付ける。接頭辞があることで、接頭辞に続く部分の書き誤りを未知の環境変数として検知できる
   */
  private static final String SETTING_ENV_PREFIX = "ETD_";

  /** 実行時設定の上書きに対応するプロパティキーと、対応するCLI引数名・環境変数名（設定ファイルに書けるキーから導く。READMEの記載順） */
  private static final List<OverrideArg> SETTING_ARGS =
      ExportTableDefinitionProperties.KEYS.stream().map(OverrideArg::forSetting).toList();

  /** {@code --キー=値}形式の引数のキーと値の区切り文字 */
  private static final char KEY_VALUE_SEPARATOR = '=';

  private final boolean check;
  private final boolean rmDist;
  private final Properties connectionOverrides;
  private final Map<String, SettingOverride> settingOverrides;
  private final List<String> unknownArguments;
  private final List<String> unknownEnvironmentVariables;

  private CliArguments(
      boolean check,
      boolean rmDist,
      Properties connectionOverrides,
      Map<String, SettingOverride> settingOverrides,
      List<String> unknownArguments,
      List<String> unknownEnvironmentVariables) {
    this.check = check;
    this.rmDist = rmDist;
    this.connectionOverrides = connectionOverrides;
    this.settingOverrides = settingOverrides;
    this.unknownArguments = unknownArguments;
    this.unknownEnvironmentVariables = unknownEnvironmentVariables;
  }

  /**
   * コマンドライン引数と、実行環境の環境変数を解析するメソッド
   *
   * @param args コマンドライン引数
   * @return 解析結果
   * @see #parse(String[], Map)
   */
  static CliArguments parse(String[] args) {
    return parse(args, System.getenv());
  }

  /**
   * コマンドライン引数と環境変数を解析するメソッド<br>
   * 解釈できない引数・環境変数があっても例外は投げない（実行モードの判定に使えるよう解析は最後まで行い、誤りは {@link #requireKnownArguments()}で報告する）。
   * <br>
   * DB接続情報・実行時設定は、{@code --db-url=...}・{@code --output-path=...}のような{@code --キー=値}形式の引数で上書きできる。
   * 未指定の場合は対応する環境変数（例: {@code DB_URL}・{@code ETD_OUTPUT_PATH}）、さらに未指定の場合は設定ファイル （{@code
   * conf/mybatis.properties}・{@code conf/ExportTableDefinition.properties}）の値が使用される
   *
   * @param args コマンドライン引数
   * @param env 環境変数（名前と値）
   * @return 解析結果
   */
  static CliArguments parse(String[] args, Map<String, String> env) {
    final List<String> argList = Arrays.asList(args);
    final Map<String, String> cliArgs = parseArgs(args);
    return new CliArguments(
        argList.contains(CHECK_FLAG),
        argList.contains(RM_DIST_FLAG),
        resolveConnectionOverrides(cliArgs, env),
        resolveSettingOverrides(cliArgs, env),
        argList.stream().filter(arg -> !isKnown(arg)).toList(),
        env.keySet().stream()
            .filter(name -> name.startsWith(SETTING_ENV_PREFIX))
            .filter(name -> SETTING_ARGS.stream().noneMatch(arg -> arg.envName().equals(name)))
            .sorted()
            .toList());
  }

  /**
   * {@code --check}が指定されているか判定するメソッド
   *
   * @return DB vs ドキュメントの差分検知モードで実行する場合はtrue
   */
  boolean isCheck() {
    return check;
  }

  /**
   * {@code --rm-dist}が指定されているか判定するメソッド
   *
   * @return 通常実行時に、書き込み前へ出力先ディレクトリを事前に削除する場合はtrue
   */
  boolean isRmDist() {
    return rmDist;
  }

  /**
   * DB接続情報の上書き値を取得するメソッド
   *
   * @return 上書きするDB接続情報（未指定のキーは含まれない）
   */
  Properties connectionOverrides() {
    return connectionOverrides;
  }

  /**
   * 実行時設定（{@code conf/ExportTableDefinition.properties}の設定値）の上書き値を取得するメソッド
   *
   * @return 設定ファイルのキーをキー、上書きする値とその指定元を値とするマップ（未指定のキーは含まれない。READMEの記載順）
   */
  Map<String, SettingOverride> settingOverrides() {
    return settingOverrides;
  }

  /**
   * 解釈できない引数・環境変数が指定されていないことを確かめるメソッド<br>
   * {@code --chek}のような書き誤りを黙って無視すると、差分検知のつもりで通常実行（{@code --rm-dist}なら出力先の削除）が
   * 行われてしまうため、処理を始める前に誤りとして報告する。実行時設定の環境変数も、接頭辞（{@code ETD_}）に続く部分の 書き誤り（{@code
   * ETD_OUTPUTPATH}等）を黙って無視すると設定ファイルの値で実行されてしまうため、同様に誤りとする
   *
   * @throws UserCorrectableException 解釈できない引数・環境変数が指定されている場合（該当するものをすべて示す）
   */
  void requireKnownArguments() {
    final List<String> errors = new ArrayList<>();
    if (!unknownArguments.isEmpty()) {
      errors.add(
          "Unknown argument: "
              + String.join(", ", unknownArguments)
              + " (available arguments: "
              + Stream.concat(
                      FLAGS.stream(),
                      Stream.concat(CONNECTION_ARGS.stream(), SETTING_ARGS.stream())
                          .map(arg -> arg.cliName() + "=<value>"))
                  .collect(Collectors.joining(", "))
              + ")");
    }
    if (!unknownEnvironmentVariables.isEmpty()) {
      errors.add(
          "Unknown environment variable: "
              + String.join(", ", unknownEnvironmentVariables)
              + " (available environment variables with the "
              + SETTING_ENV_PREFIX
              + " prefix: "
              + SETTING_ARGS.stream().map(OverrideArg::envName).collect(Collectors.joining(", "))
              + ")");
    }
    if (!errors.isEmpty()) {
      throw new UserCorrectableException(String.join(System.lineSeparator(), errors));
    }
  }

  /**
   * 解釈できる引数か判定するメソッド
   *
   * @param arg コマンドライン引数
   * @return フラグ、またはDB接続情報・実行時設定の{@code --キー=値}形式の引数の場合はtrue
   */
  private static boolean isKnown(String arg) {
    if (FLAGS.contains(arg)) {
      return true;
    }
    final int separatorIndex = arg.indexOf(KEY_VALUE_SEPARATOR);
    return separatorIndex >= 0
        && Stream.concat(CONNECTION_ARGS.stream(), SETTING_ARGS.stream())
            .anyMatch(
                overrideArg -> overrideArg.cliName().equals(arg.substring(0, separatorIndex)));
  }

  /**
   * CLI引数・環境変数からDB接続情報の上書き値を解決するメソッド<br>
   * 優先順位: CLI引数 &gt; 環境変数 &gt; （未指定の場合は{@code conf/mybatis.properties}の値をそのまま使用）
   *
   * @param cliArgs {@code --キー=値}形式のコマンドライン引数
   * @param env 環境変数
   * @return 上書きするDB接続情報（未指定のキーは含まれない）
   */
  private static Properties resolveConnectionOverrides(
      Map<String, String> cliArgs, Map<String, String> env) {
    final Properties overrides = new Properties();
    CONNECTION_ARGS.forEach(
        connectionArg ->
            resolve(connectionArg, cliArgs, env)
                .ifPresent(
                    override -> overrides.setProperty(connectionArg.key(), override.value())));
    return overrides;
  }

  /**
   * CLI引数・環境変数から実行時設定の上書き値を解決するメソッド<br>
   * 優先順位: CLI引数 &gt; 環境変数 &gt; （未指定の場合は{@code conf/ExportTableDefinition.properties}の値をそのまま使用）
   *
   * @param cliArgs {@code --キー=値}形式のコマンドライン引数
   * @param env 環境変数
   * @return 設定ファイルのキーをキー、上書きする値とその指定元を値とするマップ（未指定のキーは含まれない）
   */
  private static Map<String, SettingOverride> resolveSettingOverrides(
      Map<String, String> cliArgs, Map<String, String> env) {
    final Map<String, SettingOverride> overrides = new LinkedHashMap<>();
    SETTING_ARGS.forEach(
        settingArg ->
            resolve(settingArg, cliArgs, env)
                .ifPresent(override -> overrides.put(settingArg.key(), override)));
    return overrides;
  }

  /**
   * 1項目分の上書き値を、CLI引数・環境変数の順に探すメソッド<br>
   * 値が空（空白のみを含む）の場合は、指定しなかったものとして次の指定元を探す
   *
   * @param overrideArg 上書きする項目
   * @param cliArgs {@code --キー=値}形式のコマンドライン引数
   * @param env 環境変数
   * @return 上書きする値とその指定元。いずれにも指定されていない場合は空
   */
  private static Optional<SettingOverride> resolve(
      OverrideArg overrideArg, Map<String, String> cliArgs, Map<String, String> env) {
    return Stream.of(
            new SettingOverride(cliArgs.get(overrideArg.cliName()), overrideArg.cliName()),
            new SettingOverride(env.get(overrideArg.envName()), overrideArg.envName()))
        .filter(override -> override.value() != null && !override.value().isBlank())
        .findFirst();
  }

  /**
   * {@code --キー=値}形式のコマンドライン引数を解析するメソッド
   *
   * @param args コマンドライン引数
   * @return 引数名（{@code --}付き）と値のマップ
   */
  private static Map<String, String> parseArgs(String[] args) {
    final Map<String, String> result = new HashMap<>();
    for (final String arg : args) {
      final int separatorIndex = arg.indexOf(KEY_VALUE_SEPARATOR);
      if (!arg.startsWith("--") || separatorIndex < 0) {
        continue;
      }
      result.put(arg.substring(0, separatorIndex), arg.substring(separatorIndex + 1));
    }
    return result;
  }

  /**
   * 上書きできる1項目分のプロパティキー・CLI引数名・環境変数名の組
   *
   * @param key 設定ファイル（{@code conf/mybatis.properties}・{@code
   *     conf/ExportTableDefinition.properties}）のキー
   * @param cliName CLI引数名（{@code --}付き）
   * @param envName 環境変数名
   */
  private record OverrideArg(String key, String cliName, String envName) {

    /**
     * 実行時設定のキーから、CLI引数名・環境変数名を導くメソッド<br>
     * キーの単語の区切り（キャメルケースの大文字）を、CLI引数はハイフン区切りの小文字、環境変数は接頭辞付きのアンダースコア区切りの大文字にする （例: {@code outputPath}
     * → {@code --output-path}・{@code ETD_OUTPUT_PATH}）。設定項目を追加すれば、上書きにも自動で対応する
     *
     * @param key {@code conf/ExportTableDefinition.properties}のキー
     * @return 上書きできる1項目分の組
     */
    static OverrideArg forSetting(String key) {
      final String words = key.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
      return new OverrideArg(
          key,
          "--" + words.toLowerCase(Locale.ROOT).replace('_', '-'),
          SETTING_ENV_PREFIX + words.toUpperCase(Locale.ROOT));
    }
  }
}
