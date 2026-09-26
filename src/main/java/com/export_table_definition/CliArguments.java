package com.export_table_definition;

import com.export_table_definition.domain.UserCorrectableException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * コマンドライン引数の解析を行うクラス<br>
 * {@code --check}・{@code --rm-dist}フラグの判定と、DB接続情報の上書き値の解決（CLI引数・環境変数から）を担う。 {@code
 * conf/ExportTableDefinition.properties}に記述する実行時設定（出力対象・出力先等）の読み込みは含まない。
 * 解釈できない引数（書き誤り等）は、意図しないモードで実行されないよう誤りとする（{@link #requireKnownArguments()}）
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
  private static final List<ConnectionArg> CONNECTION_ARGS =
      List.of(
          new ConnectionArg("driver", "--db-driver", "DB_DRIVER"),
          new ConnectionArg("url", "--db-url", "DB_URL"),
          new ConnectionArg("username", "--db-username", "DB_USERNAME"),
          new ConnectionArg("password", "--db-password", "DB_PASSWORD"));

  /** {@code --キー=値}形式の引数のキーと値の区切り文字 */
  private static final char KEY_VALUE_SEPARATOR = '=';

  private final boolean check;
  private final boolean rmDist;
  private final Properties connectionOverrides;
  private final List<String> unknownArguments;

  private CliArguments(
      boolean check,
      boolean rmDist,
      Properties connectionOverrides,
      List<String> unknownArguments) {
    this.check = check;
    this.rmDist = rmDist;
    this.connectionOverrides = connectionOverrides;
    this.unknownArguments = unknownArguments;
  }

  /**
   * コマンドライン引数を解析するメソッド<br>
   * 解釈できない引数があっても例外は投げない（実行モードの判定に使えるよう解析は最後まで行い、誤りは {@link #requireKnownArguments()}で報告する）
   *
   * @param args コマンドライン引数（{@code --db-url=...}のような{@code --キー=値}形式でDB接続情報を上書き可能。 未指定の場合は同名の環境変数（例:
   *     {@code DB_URL}）、さらに未指定の場合は {@code conf/mybatis.properties}の値が使用される）
   * @return 解析結果
   */
  static CliArguments parse(String[] args) {
    final List<String> argList = Arrays.asList(args);
    return new CliArguments(
        argList.contains(CHECK_FLAG),
        argList.contains(RM_DIST_FLAG),
        resolveConnectionOverrides(args),
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
                    FLAGS.stream(), CONNECTION_ARGS.stream().map(arg -> arg.cliName() + "=<value>"))
                .collect(Collectors.joining(", "))
            + ")");
  }

  /**
   * 解釈できる引数か判定するメソッド
   *
   * @param arg コマンドライン引数
   * @return フラグ、またはDB接続情報の{@code --キー=値}形式の引数の場合はtrue
   */
  private static boolean isKnown(String arg) {
    if (FLAGS.contains(arg)) {
      return true;
    }
    final int separatorIndex = arg.indexOf(KEY_VALUE_SEPARATOR);
    return separatorIndex >= 0
        && CONNECTION_ARGS.stream()
            .anyMatch(
                connectionArg -> connectionArg.cliName().equals(arg.substring(0, separatorIndex)));
  }

  /**
   * CLI引数・環境変数からDB接続情報の上書き値を解決するメソッド<br>
   * 優先順位: CLI引数 &gt; 環境変数 &gt; （未指定の場合は{@code conf/mybatis.properties}の値をそのまま使用）
   *
   * @param args コマンドライン引数
   * @return 上書きするDB接続情報（未指定のキーは含まれない）
   */
  private static Properties resolveConnectionOverrides(String[] args) {
    final Map<String, String> cliArgs = parseArgs(args);
    final Properties overrides = new Properties();
    CONNECTION_ARGS.forEach(
        connectionArg -> {
          final String value =
              cliArgs.containsKey(connectionArg.cliName())
                  ? cliArgs.get(connectionArg.cliName())
                  : System.getenv(connectionArg.envName());
          if (value != null && !value.isBlank()) {
            overrides.setProperty(connectionArg.key(), value);
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
   * DB接続情報1項目分のプロパティキー・CLI引数名・環境変数名の組
   *
   * @param key {@code conf/mybatis.properties}のキー
   * @param cliName CLI引数名（{@code --}付き）
   * @param envName 環境変数名
   */
  private record ConnectionArg(String key, String cliName, String envName) {}
}
