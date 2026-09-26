package com.export_table_definition;

import com.export_table_definition.ExportTableDefinitionProperties.SettingOverride;
import com.export_table_definition.shared.exception.UserCorrectableException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * コマンドライン引数の解析を行うクラス<br>
 * {@code --check}・{@code --rm-dist}フラグの判定と、DB接続情報・実行時設定（{@code
 * conf/ExportTableDefinition.properties}の設定値）の上書き値の解決を担う。 設定ファイルの読み込みと、上書きした値の検証は含まない（{@link
 * ExportTableDefinitionProperties}・{@code ConnectionSettings}が行う）。
 * 解釈できない引数（書き誤り等）は、意図しないモード・設定で実行されないよう誤りとする（{@link #requireKnownArguments()}）
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

  /** DB接続情報の上書きに対応するプロパティキーと、対応するCLI引数名（READMEの記載順） */
  private static final List<OverrideArg> CONNECTION_ARGS =
      List.of(
          new OverrideArg("driver", "--db-driver"),
          new OverrideArg("url", "--db-url"),
          new OverrideArg("username", "--db-username"),
          new OverrideArg("password", "--db-password"));

  /** 実行時設定の上書きに対応するプロパティキーと、対応するCLI引数名（設定ファイルに書けるキーから導く。READMEの記載順） */
  private static final List<OverrideArg> SETTING_ARGS =
      ExportTableDefinitionProperties.KEYS.stream().map(OverrideArg::forSetting).toList();

  /** {@code --キー=値}形式の引数のキーと値の区切り文字 */
  private static final char KEY_VALUE_SEPARATOR = '=';

  private final boolean check;
  private final boolean rmDist;
  private final Properties connectionOverrides;
  private final Map<String, SettingOverride> settingOverrides;
  private final List<String> unknownArguments;

  private CliArguments(
      boolean check,
      boolean rmDist,
      Properties connectionOverrides,
      Map<String, SettingOverride> settingOverrides,
      List<String> unknownArguments) {
    this.check = check;
    this.rmDist = rmDist;
    this.connectionOverrides = connectionOverrides;
    this.settingOverrides = settingOverrides;
    this.unknownArguments = unknownArguments;
  }

  /**
   * コマンドライン引数を解析するメソッド<br>
   * 解釈できない引数があっても例外は投げない（実行モードの判定に使えるよう解析は最後まで行い、誤りは {@link #requireKnownArguments()}で報告する）
   *
   * @param args コマンドライン引数（{@code --db-url=...}・{@code --output-path=...}のような{@code --キー=値}形式で
   *     DB接続情報・実行時設定を上書き可能。未指定の場合は設定ファイル（{@code conf/mybatis.properties}・{@code
   *     conf/ExportTableDefinition.properties}）の値が使用される）
   * @return 解析結果
   */
  static CliArguments parse(String[] args) {
    final List<String> argList = Arrays.asList(args);
    final Map<String, String> cliArgs = parseArgs(args);
    return new CliArguments(
        argList.contains(CHECK_FLAG),
        argList.contains(RM_DIST_FLAG),
        resolveConnectionOverrides(cliArgs),
        resolveSettingOverrides(cliArgs),
        argList.stream().filter(arg -> !isKnown(arg)).toList());
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
   * 解釈できない引数が指定されていないことを確かめるメソッド<br>
   * {@code --chek}のような書き誤りを黙って無視すると、差分検知のつもりで通常実行（{@code --rm-dist}なら出力先の削除）が
   * 行われてしまうため、処理を始める前に誤りとして報告する
   *
   * @throws UserCorrectableException 解釈できない引数が指定されている場合（該当する引数をすべて示す）
   */
  void requireKnownArguments() {
    if (unknownArguments.isEmpty()) {
      return;
    }
    throw new UserCorrectableException(
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
   * CLI引数からDB接続情報の上書き値を解決するメソッド<br>
   * 値が空（空白のみを含む）の場合は、指定しなかったものとして{@code conf/mybatis.properties}の値をそのまま使用する
   *
   * @param cliArgs {@code --キー=値}形式のコマンドライン引数
   * @return 上書きするDB接続情報（未指定のキーは含まれない）
   */
  private static Properties resolveConnectionOverrides(Map<String, String> cliArgs) {
    final Properties overrides = new Properties();
    CONNECTION_ARGS.forEach(
        connectionArg -> {
          final String value = cliArgs.get(connectionArg.cliName());
          if (value != null && !value.isBlank()) {
            overrides.setProperty(connectionArg.key(), value);
          }
        });
    return overrides;
  }

  /**
   * CLI引数から実行時設定の上書き値を解決するメソッド<br>
   * 値が空（空白のみを含む）の場合は、指定しなかったものとして{@code conf/ExportTableDefinition.properties}の値をそのまま使用する
   *
   * @param cliArgs {@code --キー=値}形式のコマンドライン引数
   * @return 設定ファイルのキーをキー、上書きする値とその指定元を値とするマップ（未指定のキーは含まれない）
   */
  private static Map<String, SettingOverride> resolveSettingOverrides(Map<String, String> cliArgs) {
    final Map<String, SettingOverride> overrides = new LinkedHashMap<>();
    SETTING_ARGS.forEach(
        settingArg -> {
          final String value = cliArgs.get(settingArg.cliName());
          if (value != null && !value.isBlank()) {
            overrides.put(settingArg.key(), new SettingOverride(value, settingArg.cliName()));
          }
        });
    return overrides;
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
   * 上書きできる1項目分のプロパティキーとCLI引数名の組
   *
   * @param key 設定ファイル（{@code conf/mybatis.properties}・{@code
   *     conf/ExportTableDefinition.properties}）のキー
   * @param cliName CLI引数名（{@code --}付き）
   */
  private record OverrideArg(String key, String cliName) {

    /**
     * 実行時設定のキーから、CLI引数名を導くメソッド<br>
     * キーの単語の区切り（キャメルケースの大文字）を、ハイフン区切りの小文字にする （例: {@code outputPath} → {@code
     * --output-path}）。設定項目を追加すれば、上書きにも自動で対応する
     *
     * @param key {@code conf/ExportTableDefinition.properties}のキー
     * @return 上書きできる1項目分の組
     */
    static OverrideArg forSetting(String key) {
      return new OverrideArg(
          key, "--" + key.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT));
    }
  }
}
