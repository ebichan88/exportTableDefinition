package com.export_table_definition;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.TargetSelection;
import com.export_table_definition.config.PropertyLoader;
import com.export_table_definition.config.module.ExportTableDefinitionModule;
import com.export_table_definition.infrastructure.db.MyBatisSqlSessionFactory;
import com.export_table_definition.presentation.ExportTableDefinitionController;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ProcessResult;
import com.google.inject.Guice;
import java.util.MissingResourceException;

/**
 * テーブル定義出力処理を呼び出すクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinition {

  /** 実行時設定を記述したプロパティファイル名（{@code conf/}配下。拡張子を除く） */
  private static final String PROPERTY_FILE_NAME = "ExportTableDefinition";

  /** chunkSize未設定時のデフォルト値（1スキーマあたりこの件数ごとに詳細情報を取得・出力する） */
  private static final int DEFAULT_CHUNK_SIZE = 3000;

  /** erDiagramMaxNodes未設定時のデフォルト値（スキーマ別ER図1枚に描画するテーブル数の上限） */
  private static final int DEFAULT_ER_DIAGRAM_MAX_NODES = 80;

  private ExportTableDefinitionController controller;

  ExportTableDefinition(ExportTableDefinitionController controller) {
    this.controller = controller;
  }

  /**
   * テーブル定義出力処理のエントリーポイントメソッド
   *
   * @param args コマンドライン引数（CLI引数・フラグの解析は{@link CliArguments}を参照）
   */
  public static void main(String[] args) {
    final CliArguments cliArguments = CliArguments.parse(args);
    MyBatisSqlSessionFactory.setConnectionOverrides(cliArguments.connectionOverrides());
    final ExportTableDefinition exportTableDefinition =
        new ExportTableDefinition(
            Guice.createInjector(
                    new ExportTableDefinitionModule(MyBatisSqlSessionFactory.getConnectionDbName()))
                .getInstance(ExportTableDefinitionController.class));
    if (cliArguments.isCheck()) {
      if (cliArguments.isRmDist()) {
        System.out.println("Note: --rm-dist is ignored in --check mode.");
      }
      exportTableDefinition.runCheck();
    } else {
      exportTableDefinition.run(cliArguments.isRmDist());
    }
  }

  /**
   * テーブル定義出力処理実行メソッド
   *
   * @param rmDist trueの場合、書き込み前に出力先ディレクトリを事前に削除する（{@code --rm-dist}）
   */
  void run(boolean rmDist) {
    // 処理開始メッセージ出力
    System.out.println(
        """
                Starting output of table definition document.
                Please wait a moment ...
                """);
    // 設定ファイルの読み込み・検証（DBへの問い合わせや出力先の削除より前に行う）
    final ExportRequest request;
    try {
      request = loadExportRequest(rmDist);
    } catch (IllegalArgumentException | MissingResourceException e) {
      System.out.println(invalidConfigurationMessage(e));
      return;
    }
    // テーブル定義出力処理実行
    final ResultDto resultDto = controller.execute(request);
    // 処理終了メッセージ出力
    System.out.println(resultDto.getResultMessage());
  }

  /** DB vs ドキュメントの差分検知処理実行メソッド（{@code --check}モード） */
  void runCheck() {
    // 処理開始メッセージ出力
    System.out.println(
        """
                Starting check of table definition document diff.
                Please wait a moment ...
                """);
    // 設定ファイルの読み込み・検証（DBへの問い合わせより前に行う）
    final CheckDiffRequest request;
    try {
      request = loadCheckDiffRequest();
    } catch (IllegalArgumentException | MissingResourceException e) {
      System.out.println(invalidConfigurationMessage(e));
      System.exit(1);
      return;
    }
    // DB vs ドキュメントの差分検知処理実行
    final DiffCheckResultDto diffCheckResultDto = controller.checkDiff(request);
    // 処理終了メッセージ出力
    System.out.println(diffCheckResultDto.getResultMessage());
    // 比較処理自体が失敗した場合、または差分が見つかった場合は異常終了とする
    if (diffCheckResultDto.result() == ProcessResult.FAIL || diffCheckResultDto.hasDifference()) {
      System.exit(1);
    }
  }

  /**
   * 設定ファイルの読み込み・検証に失敗した場合の処理結果メッセージを組み立てるメソッド
   *
   * @param e 読み込み・検証時に発生した例外（未知の出力対象オブジェクト種別・キーの記載漏れ等）
   * @return 処理結果メッセージ
   */
  private static String invalidConfigurationMessage(RuntimeException e) {
    return ProcessResult.FAIL.formatMessage(
        String.format(
            "Invalid configuration in conf/%s.properties. %s [errmsg]:%s",
            PROPERTY_FILE_NAME, System.lineSeparator(), e.getMessage()));
  }

  /**
   * {@code conf/ExportTableDefinition.properties}からテーブル定義出力（通常実行）の入力を読み込むメソッド
   *
   * @param rmDist trueの場合、書き込みを開始する前に出力先ディレクトリを再帰的に削除する（{@code --rm-dist}）
   * @return 読み込んだ入力
   */
  private static ExportRequest loadExportRequest(boolean rmDist) {
    return new ExportRequest(
        loadTargetSelection(),
        PropertyLoader.getString(PROPERTY_FILE_NAME, "outputPath"),
        PropertyLoader.getInt(PROPERTY_FILE_NAME, "chunkSize", DEFAULT_CHUNK_SIZE),
        PropertyLoader.getInt(
            PROPERTY_FILE_NAME, "erDiagramMaxNodes", DEFAULT_ER_DIAGRAM_MAX_NODES),
        rmDist);
  }

  /**
   * {@code conf/ExportTableDefinition.properties}からDB vs ドキュメントの差分検知（{@code --check}モード）の
   * 入力を読み込むメソッド<br>
   * 通常実行と異なり、Markdownの描画・ER図の生成を行わないため{@code erDiagramMaxNodes}は読み込まない
   *
   * @return 読み込んだ入力
   */
  private static CheckDiffRequest loadCheckDiffRequest() {
    return new CheckDiffRequest(
        loadTargetSelection(),
        PropertyLoader.getString(PROPERTY_FILE_NAME, "outputPath"),
        PropertyLoader.getInt(PROPERTY_FILE_NAME, "chunkSize", DEFAULT_CHUNK_SIZE));
  }

  /**
   * {@code conf/ExportTableDefinition.properties}から出力対象の絞り込み条件を読み込むメソッド<br>
   * 通常実行・{@code --check}実行の双方で共通の読み込み処理。生の文字列のまま後続へ渡さず、ここで型へ変換・検証する
   *
   * @return 読み込んだ出力対象の絞り込み条件
   * @throws IllegalArgumentException 未知の出力対象オブジェクト種別名が指定されている場合
   */
  private static TargetSelection loadTargetSelection() {
    return TargetSelection.of(
        PropertyLoader.getList(PROPERTY_FILE_NAME, "schema"),
        PropertyLoader.getList(PROPERTY_FILE_NAME, "table"),
        PropertyLoader.getList(PROPERTY_FILE_NAME, "outputObjects"),
        // サイドカーYAMLのパスは、既存の設定ファイルとの互換のためプロパティキーannotationPathで指定する
        PropertyLoader.getString(PROPERTY_FILE_NAME, "annotationPath"));
  }
}
