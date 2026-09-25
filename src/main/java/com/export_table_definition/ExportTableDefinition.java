package com.export_table_definition;

import com.export_table_definition.config.PropertyLoader;
import com.export_table_definition.config.module.ExportTableDefinitionModule;
import com.export_table_definition.infrastructure.db.MyBatisSqlSessionFactory;
import com.export_table_definition.presentation.ExportTableDefinitionController;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ProcessResult;
import com.google.inject.Guice;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * テーブル定義出力処理を呼び出すクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinition {

  /** chunkSize未設定時のデフォルト値（1スキーマあたりこの件数ごとに詳細情報を取得・出力する） */
  private static final int DEFAULT_CHUNK_SIZE = 3000;

  /** erDiagramMaxNodes未設定時のデフォルト値（スキーマ別ER図1枚に描画するテーブル数の上限） */
  private static final int DEFAULT_ER_DIAGRAM_MAX_NODES = 80;

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

  private ExportTableDefinitionController controller;

  ExportTableDefinition(ExportTableDefinitionController controller) {
    this.controller = controller;
  }

  /**
   * テーブル定義出力処理のエントリーポイントメソッド
   *
   * @param args コマンドライン引数（{@code --db-url=...}のような{@code --キー=値}形式でDB接続情報を上書き可能。 未指定の場合は同名の環境変数（例:
   *     {@code DB_URL}）、さらに未指定の場合は {@code conf/mybatis.properties}の値が使用される。{@code --check}を指定すると、
   *     通常のドキュメント出力の代わりにDB vs ドキュメントの差分検知モードで実行する。{@code --rm-dist}を指定すると、
   *     通常実行時に書き込み前へ出力先ディレクトリを事前に削除する）
   */
  public static void main(String[] args) {
    MyBatisSqlSessionFactory.setConnectionOverrides(resolveConnectionOverrides(args));
    final ExportTableDefinition exportTableDefinition =
        new ExportTableDefinition(
            Guice.createInjector(new ExportTableDefinitionModule())
                .getInstance(ExportTableDefinitionController.class));
    final boolean rmDist = Arrays.asList(args).contains(RM_DIST_FLAG);
    switch (ExecutionMode.from(args)) {
      case EXPORT -> exportTableDefinition.run(rmDist);
      case CHECK -> {
        if (rmDist) {
          System.out.println("Note: --rm-dist is ignored in --check mode.");
        }
        exportTableDefinition.runCheck();
      }
    }
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

  /** 実行モードの種別 */
  private enum ExecutionMode {
    /** テーブル定義出力（通常実行） */
    EXPORT,
    /** DB vs ドキュメントの差分検知（{@code --check}モード） */
    CHECK;

    /**
     * コマンドライン引数から実行モードを判定するメソッド
     *
     * @param args コマンドライン引数
     * @return {@code --check}が指定されている場合は{@link #CHECK}、それ以外は{@link #EXPORT}
     */
    static ExecutionMode from(String[] args) {
      return Arrays.asList(args).contains(CHECK_FLAG) ? CHECK : EXPORT;
    }
  }

  /**
   * テーブル定義出力処理実行メソッド
   *
   * @param rmDist trueの場合、書き込み前に出力先ディレクトリを事前に削除する（{@code --rm-dist}）
   */
  void run(boolean rmDist) {
    final ExecutionSettings settings = loadExecutionSettings();
    // 処理開始メッセージ出力
    System.out.println(
        """
                Starting output of table definition document.
                Please wait a moment ...
                """);
    // テーブル定義出力処理実行
    final ResultDto resultDto =
        controller.execute(
            settings.schemaList(),
            settings.tableList(),
            settings.outputPath(),
            settings.chunkSize(),
            settings.erDiagramMaxNodes(),
            settings.outputObjectList(),
            settings.annotationPath(),
            settings.outputSnapshot(),
            rmDist);
    // 処理終了メッセージ出力
    System.out.println(resultDto.getResultMessage());
  }

  /** DB vs ドキュメントの差分検知処理実行メソッド（{@code --check}モード） */
  void runCheck() {
    final ExecutionSettings settings = loadExecutionSettings();
    // 処理開始メッセージ出力
    System.out.println(
        """
                Starting check of table definition document diff.
                Please wait a moment ...
                """);
    // DB vs ドキュメントの差分検知処理実行
    final DiffCheckResultDto diffCheckResultDto =
        controller.checkDiff(
            settings.schemaList(),
            settings.tableList(),
            settings.outputPath(),
            settings.chunkSize(),
            settings.erDiagramMaxNodes(),
            settings.outputObjectList(),
            settings.annotationPath(),
            settings.outputSnapshot());
    // 処理終了メッセージ出力
    System.out.println(diffCheckResultDto.getResultMessage());
    // 比較処理自体が失敗した場合、または差分が見つかった場合は異常終了とする
    if (diffCheckResultDto.result() == ProcessResult.FAIL || diffCheckResultDto.hasDifference()) {
      System.exit(1);
    }
  }

  /**
   * 実行時設定を{@code conf/ExportTableDefinition.properties}から読み込むメソッド
   *
   * @return 読み込んだ実行時設定
   */
  private static ExecutionSettings loadExecutionSettings() {
    return new ExecutionSettings(
        PropertyLoader.getList("ExportTableDefinition", "schema"),
        PropertyLoader.getList("ExportTableDefinition", "table"),
        PropertyLoader.getString("ExportTableDefinition", "outputPath"),
        PropertyLoader.getInt("ExportTableDefinition", "chunkSize", DEFAULT_CHUNK_SIZE),
        PropertyLoader.getInt(
            "ExportTableDefinition", "erDiagramMaxNodes", DEFAULT_ER_DIAGRAM_MAX_NODES),
        PropertyLoader.getList("ExportTableDefinition", "outputObjects"),
        PropertyLoader.getString("ExportTableDefinition", "annotationPath"),
        PropertyLoader.getBoolean("ExportTableDefinition", "outputSnapshot", false));
  }

  /**
   * {@code conf/ExportTableDefinition.properties}から読み込む実行時設定の組
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @param tableList テーブル定義出力対象のテーブルのリスト
   * @param outputPath テーブル定義出力の出力先のパス
   * @param chunkSize 詳細情報をまとめて取得するテーブル数の上限
   * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限
   * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名のリスト
   * @param annotationPath 手動付帯情報を記述したサイドカーYAMLのパス
   * @param outputSnapshot Markdownに加えてスキーマのスナップショットを出力するか
   */
  private record ExecutionSettings(
      List<String> schemaList,
      List<String> tableList,
      String outputPath,
      int chunkSize,
      int erDiagramMaxNodes,
      List<String> outputObjectList,
      String annotationPath,
      boolean outputSnapshot) {}
}
