package com.export_table_definition;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.TargetSelection;
import com.export_table_definition.config.InvalidConfigurationException;
import com.export_table_definition.config.PropertyLoader;
import com.export_table_definition.config.module.ExportTableDefinitionModule;
import com.export_table_definition.infrastructure.db.MyBatisSqlSessionFactory;
import com.export_table_definition.presentation.ExportTableDefinitionController;
import com.export_table_definition.presentation.FailureHandler;
import com.export_table_definition.presentation.dto.DiffCheckResultDto;
import com.export_table_definition.presentation.dto.ResultDto;
import com.export_table_definition.presentation.type.ExitStatus;
import com.google.inject.Guice;
import java.util.List;

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

  /** 実行時設定を記述したプロパティファイル名（{@code conf/}配下。拡張子を除く） */
  private static final String PROPERTY_FILE_NAME = "ExportTableDefinition";

  /** chunkSize未設定時のデフォルト値（1スキーマあたりこの件数ごとに詳細情報を取得・出力する） */
  private static final int DEFAULT_CHUNK_SIZE = 3000;

  /** erDiagramMaxNodes未設定時のデフォルト値（スキーマ別ER図1枚に描画するテーブル数の上限） */
  private static final int DEFAULT_ER_DIAGRAM_MAX_NODES = 80;

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
              // 設定ファイルの読み込み・検証（DBへの接続・問い合わせや出力先の削除より前に行う）
              final ExportRequest request = loadExportRequest(cliArguments.isRmDist());
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
              // 設定ファイルの読み込み・検証（DBへの接続・問い合わせより前に行う）
              final CheckDiffRequest request = loadCheckDiffRequest();
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

  /**
   * {@code conf/ExportTableDefinition.properties}からテーブル定義出力（通常実行）の入力を読み込むメソッド
   *
   * @param rmDist trueの場合、書き込みを開始する前に出力先ディレクトリを再帰的に削除する（{@code --rm-dist}）
   * @return 読み込んだ入力
   * @throws InvalidConfigurationException 設定ファイル・キーが見つからない場合や、値が不正な場合
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
   * @throws InvalidConfigurationException 設定ファイル・キーが見つからない場合や、値が不正な場合
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
   * @throws InvalidConfigurationException キーの記載漏れや、未知の出力対象オブジェクト種別名が指定されている場合
   */
  private static TargetSelection loadTargetSelection() {
    final List<String> schemas = PropertyLoader.getList(PROPERTY_FILE_NAME, "schema");
    final List<String> tables = PropertyLoader.getList(PROPERTY_FILE_NAME, "table");
    final List<String> outputObjects = PropertyLoader.getList(PROPERTY_FILE_NAME, "outputObjects");
    // サイドカーYAMLのパスは、既存の設定ファイルとの互換のためプロパティキーannotationPathで指定する
    final String sidecarPath = PropertyLoader.getString(PROPERTY_FILE_NAME, "annotationPath");
    try {
      return TargetSelection.of(schemas, tables, outputObjects, sidecarPath);
    } catch (IllegalArgumentException e) {
      // 値の検証（TargetSelection.ofの契約）で見つかった誤りを、どの設定ファイルの誤りかを添えて設定誤りとして伝える
      throw new InvalidConfigurationException(
          "Invalid value in " + PROPERTY_FILE_NAME + ".properties. " + e.getMessage(), e);
    }
  }
}
