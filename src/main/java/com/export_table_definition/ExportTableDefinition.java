package com.export_table_definition;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.config.module.ExportTableDefinitionModule;
import com.export_table_definition.infrastructure.db.MyBatisSqlSessionFactory;
import com.export_table_definition.presentation.ExportTableDefinitionController;
import com.export_table_definition.presentation.FailureHandler;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ExitStatus;
import com.google.inject.Guice;

/**
 * テーブル定義出力処理を呼び出すクラス<br>
 * 処理全体（設定の読み込み・DBへの接続・DIコンテナの組み立てを含む）を{@link FailureHandler}経由で実行し、
 * 例外の捕捉と、終了状態からプロセスの終了コードへの変換をここで1箇所にまとめて行う
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinition {

  /** 通常実行が失敗した場合の報告の要旨 */
  private static final String EXPORT_FAILURE_SUMMARY =
      "Failed to output table definition document.";

  /** 差分検知（{@code --check}モード）が失敗した場合の報告の要旨 */
  private static final String CHECK_FAILURE_SUMMARY =
      "Failed to check table definition document diff.";

  /** コンストラクタ（インスタンス化不可） */
  private ExportTableDefinition() {}

  /**
   * テーブル定義出力処理のエントリーポイントメソッド<br>
   * 終了コードは、成功（{@code --check}で差分なしを含む）は0、{@code --check}で差分ありは1、失敗は2
   *
   * @param args コマンドライン引数（CLI引数・フラグの解析は{@link CliArguments}を参照）
   */
  public static void main(String[] args) {
    final CliArguments cliArguments = CliArguments.parse(args);
    final ExitStatus exitStatus =
        cliArguments.isCheck() ? runCheck(cliArguments) : run(cliArguments);
    System.exit(exitStatus.code());
  }

  /**
   * テーブル定義出力処理実行メソッド
   *
   * @param cliArguments コマンドライン引数の解析結果
   * @return 終了状態
   */
  private static ExitStatus run(CliArguments cliArguments) {
    // 処理開始メッセージ出力
    System.out.println(
        """
                Starting output of table definition document.
                Please wait a moment ...
                """);
    return new FailureHandler(EXPORT_FAILURE_SUMMARY, System.out::println)
        .run(
            () -> {
              // CLI引数・設定ファイルの検証（DBへの接続・問い合わせや出力先の削除より前に行う）
              cliArguments.requireKnownArguments();
              final ExportRequest request =
                  ExportTableDefinitionProperties.load().toExportRequest(cliArguments.isRmDist());
              // テーブル定義出力処理実行
              final ResultDto resultDto = createController(cliArguments).execute(request);
              // 処理終了メッセージ出力
              System.out.println(resultDto.getResultMessage());
              return ExitStatus.SUCCESS;
            });
  }

  /**
   * DB vs ドキュメントの差分検知処理実行メソッド（{@code --check}モード）
   *
   * @param cliArguments コマンドライン引数の解析結果
   * @return 終了状態（差分が見つかった場合は{@link ExitStatus#DIFFERENCE_FOUND}）
   */
  private static ExitStatus runCheck(CliArguments cliArguments) {
    if (cliArguments.isRmDist()) {
      System.out.println("Note: --rm-dist is ignored in --check mode.");
    }
    // 処理開始メッセージ出力
    System.out.println(
        """
                Starting check of table definition document diff.
                Please wait a moment ...
                """);
    return new FailureHandler(CHECK_FAILURE_SUMMARY, System.out::println)
        .run(
            () -> {
              // CLI引数・設定ファイルの検証（DBへの接続・問い合わせより前に行う）
              cliArguments.requireKnownArguments();
              final CheckDiffRequest request =
                  ExportTableDefinitionProperties.load().toCheckDiffRequest();
              // DB vs ドキュメントの差分検知処理実行
              final DiffCheckResultDto diffCheckResultDto =
                  createController(cliArguments).checkDiff(request);
              // 処理終了メッセージ出力
              System.out.println(diffCheckResultDto.getResultMessage());
              return diffCheckResultDto.exitStatus();
            });
  }

  /**
   * DBへ接続して接続先のDB種別を判定し、DIコンテナからコントローラーを取得するメソッド
   *
   * @param cliArguments コマンドライン引数の解析結果（DB接続情報の上書き値を含む）
   * @return コントローラー
   */
  private static ExportTableDefinitionController createController(CliArguments cliArguments) {
    MyBatisSqlSessionFactory.setConnectionOverrides(cliArguments.connectionOverrides());
    return Guice.createInjector(
            new ExportTableDefinitionModule(MyBatisSqlSessionFactory.getConnectionDbName()))
        .getInstance(ExportTableDefinitionController.class);
  }
}
