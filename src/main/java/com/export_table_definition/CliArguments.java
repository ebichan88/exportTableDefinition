package com.export_table_definition;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * コマンドライン引数の解析を行うクラス<br>
 * {@code --check}・{@code --rm-dist}フラグの判定と、DB接続情報の上書き値の解決（CLI引数・環境変数から）を担う。 {@code
 * conf/ExportTableDefinition.properties}に記述する実行時設定（出力対象・出力先等）の読み込みは含まない
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

  /** DB接続情報の上書きに対応するプロパティキーと、対応するCLI引数名・環境変数名 */
  private static final Map<String, ConnectionArg> CONNECTION_ARGS =
      Map.of(
          "driver", new ConnectionArg("--db-driver", "DB_DRIVER"),
          "url", new ConnectionArg("--db-url", "DB_URL"),
          "username", new ConnectionArg("--db-username", "DB_USERNAME"),
          "password", new ConnectionArg("--db-password", "DB_PASSWORD"));

  private final boolean check;
  private final boolean rmDist;
  private final Properties connectionOverrides;

  private CliArguments(boolean check, boolean rmDist, Properties connectionOverrides) {
    this.check = check;
    this.rmDist = rmDist;
    this.connectionOverrides = connectionOverrides;
  }

  /**
   * コマンドライン引数を解析するメソッド
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
        resolveConnectionOverrides(args));
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
        (key, connectionArg) -> {
          final String value =
              cliArgs.containsKey(connectionArg.cliName())
                  ? cliArgs.get(connectionArg.cliName())
                  : System.getenv(connectionArg.envName());
          if (value != null && !value.isBlank()) {
            overrides.setProperty(key, value);
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
      final int separatorIndex = arg.indexOf('=');
      if (!arg.startsWith("--") || separatorIndex < 0) {
        continue;
      }
      result.put(arg.substring(0, separatorIndex), arg.substring(separatorIndex + 1));
    }
    return result;
  }

  /**
   * DB接続情報1項目分のCLI引数名・環境変数名の組
   *
   * @param cliName CLI引数名（{@code --}付き）
   * @param envName 環境変数名
   */
  private record ConnectionArg(String cliName, String envName) {}
}
