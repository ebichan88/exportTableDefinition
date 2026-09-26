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
    // テーブル定義出力処理実行
    final ResultDto resultDto = controller.execute(loadExportRequest(rmDist));
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
    // DB vs ドキュメントの差分検知処理実行
    final DiffCheckResultDto diffCheckResultDto = controller.checkDiff(loadCheckDiffRequest());
    // 処理終了メッセージ出力
    System.out.println(diffCheckResultDto.getResultMessage());
    // 比較処理自体が失敗した場合、または差分が見つかった場合は異常終了とする
    if (diffCheckResultDto.result() == ProcessResult.FAIL || diffCheckResultDto.hasDifference()) {
      System.exit(1);
    }
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
        PropertyLoader.getString("ExportTableDefinition", "outputPath"),
        PropertyLoader.getInt("ExportTableDefinition", "chunkSize", DEFAULT_CHUNK_SIZE),
        PropertyLoader.getInt(
            "ExportTableDefinition", "erDiagramMaxNodes", DEFAULT_ER_DIAGRAM_MAX_NODES),
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
        PropertyLoader.getString("ExportTableDefinition", "outputPath"),
        PropertyLoader.getInt("ExportTableDefinition", "chunkSize", DEFAULT_CHUNK_SIZE));
  }

  /**
   * {@code conf/ExportTableDefinition.properties}から出力対象の絞り込み条件を読み込むメソッド<br>
   * 通常実行・{@code --check}実行の双方で共通の読み込み処理
   *
   * @return 読み込んだ出力対象の絞り込み条件
   */
  private static TargetSelection loadTargetSelection() {
    return new TargetSelection(
        PropertyLoader.getList("ExportTableDefinition", "schema"),
        PropertyLoader.getList("ExportTableDefinition", "table"),
        PropertyLoader.getList("ExportTableDefinition", "outputObjects"),
        PropertyLoader.getString("ExportTableDefinition", "annotationPath"));
  }
}
