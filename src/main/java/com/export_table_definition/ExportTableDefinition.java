package com.export_table_definition;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.config.module.DatabaseDependentModule;
import com.export_table_definition.config.module.ExportTableDefinitionModule;
import com.export_table_definition.infrastructure.db.ConnectionSettings;
import com.export_table_definition.infrastructure.db.DatabaseTypeDetector;
import com.export_table_definition.infrastructure.db.MyBatisSqlSessionFactories;
import com.export_table_definition.presentation.ExportTableDefinitionController;
import com.export_table_definition.presentation.FailureReporter;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ExitStatus;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * テーブル定義出力処理を呼び出すクラス<br>
 * 処理全体（入力の検証・DBへの接続・DIコンテナの組み立てを含む）で起きた例外を{@link #main}の1箇所で捕捉し、 {@link
 * FailureReporter}で報告したうえで、終了状態をプロセスの終了コードへ変換する
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
   * 終了コードは、成功（{@code --check}で差分なしを含む）は0、{@code --check}で差分ありは1、失敗は2以上（現在は2のみ）
   *
   * @param args コマンドライン引数（CLI引数・フラグの解析は{@link CliArguments}を参照）
   */
  public static void main(String[] args) {
    final CliArguments cliArguments = CliArguments.parse(args);
    ExitStatus exitStatus;
    try {
      exitStatus = cliArguments.isCheck() ? runCheck(cliArguments) : run(cliArguments);
    } catch (Throwable e) {
      // 例外はここで1箇所にまとめて捕捉する（途中の層では捕捉しない）。JVMのエラー（Error）も捕捉するのは、
      // 捕捉しないとJVMが終了コード1で終了し、--checkの「差分あり」と区別できなくなるため
      new FailureReporter(
              cliArguments.isCheck() ? CHECK_FAILURE_SUMMARY : EXPORT_FAILURE_SUMMARY,
              System.out::println)
          .report(e);
      exitStatus = ExitStatus.FAILURE;
    }
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
    // CLI引数・設定ファイル（CLI引数で上書きした値を含む）・出力先・DB接続情報の検証
    // （DBに接続できない環境でも入力の誤りを報告できるよう、DBへの接続より前に行う）
    cliArguments.requireKnownArguments();
    final ExportRequest request =
        ExportTableDefinitionProperties.load(cliArguments.settingOverrides())
            .toExportRequest(cliArguments.isRmDist());
    final Injector injector = createInjector();
    injector.getInstance(OutputDirectoryValidator.class).validate(request);
    final ConnectionSettings connectionSettings =
        ConnectionSettings.load(cliArguments.connectionOverrides());
    // テーブル定義出力処理実行
    final ResultDto resultDto = createController(injector, connectionSettings).execute(request);
    // 処理終了メッセージ出力
    System.out.println(resultDto.getResultMessage());
    return ExitStatus.SUCCESS;
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
    // CLI引数・設定ファイル（CLI引数で上書きした値を含む）・出力先・DB接続情報の検証
    // （DBに接続できない環境でも入力の誤りを報告できるよう、DBへの接続より前に行う）
    cliArguments.requireKnownArguments();
    final CheckDiffRequest request =
        ExportTableDefinitionProperties.load(cliArguments.settingOverrides()).toCheckDiffRequest();
    final Injector injector = createInjector();
    injector.getInstance(OutputDirectoryValidator.class).validate(request);
    final ConnectionSettings connectionSettings =
        ConnectionSettings.load(cliArguments.connectionOverrides());
    // DB vs ドキュメントの差分検知処理実行
    final DiffCheckResultDto diffCheckResultDto =
        createController(injector, connectionSettings).checkDiff(request);
    // 処理終了メッセージ出力
    System.out.println(diffCheckResultDto.getResultMessage());
    return diffCheckResultDto.exitStatus();
  }

  /**
   * DB種別に依存しない部品のDIコンテナを組み立てるメソッド<br>
   * 入力の検証は、DBへ接続する前に行う。接続した後に検証すると、DBに接続できない環境（接続情報の誤り・DBの停止中）では
   * 接続エラーだけが報告され、それを直して再実行するまで入力の誤りに気付けないため。<br>
   * ただし、出力先の検証に使う部品（出力先パスの解決・パスの状態の問い合わせ）はDIコンテナから取得する一方で、
   * DB種別で実装が変わる部品（TableDefinitionRepositoryと、それに依存するユースケース）は、DB種別が接続して初めて分かるため、
   * 接続した後にしか束縛できない。そこでDIコンテナを2段階に分け、DB種別に依存しない部品だけのコンテナをここで先に組み立てて
   * 検証に使い、DB種別に依存する部品は、接続した後に子のコンテナとして足す（{@link #createController}）
   *
   * @return DB種別に依存しない部品のDIコンテナ
   */
  private static Injector createInjector() {
    return Guice.createInjector(new ExportTableDefinitionModule());
  }

  /**
   * DBへ接続して接続先のDB種別を判定し、DB種別に依存する部品を束縛した子のDIコンテナからコントローラーを取得するメソッド<br>
   * {@link SqlSessionFactory}はここで1回だけ生成し、子のDIコンテナを通じてリポジトリで使い回す
   *
   * @param injector DB種別に依存しない部品のDIコンテナ（{@link #createInjector()}）
   * @param connectionSettings 検証済みのDB接続情報
   * @return コントローラー
   */
  private static ExportTableDefinitionController createController(
      Injector injector, ConnectionSettings connectionSettings) {
    final SqlSessionFactory sqlSessionFactory =
        MyBatisSqlSessionFactories.create(connectionSettings);
    return injector
        .createChildInjector(
            new DatabaseDependentModule(
                DatabaseTypeDetector.detect(sqlSessionFactory), sqlSessionFactory))
        .getInstance(ExportTableDefinitionController.class);
  }
}
