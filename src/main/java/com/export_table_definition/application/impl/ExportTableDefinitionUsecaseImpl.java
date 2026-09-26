package com.export_table_definition.application.impl;

import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.domain.model.target.ExportTargets;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.export.MarkdownExportSinkFactory;
import com.export_table_definition.domain.service.export.SnapshotExportSinkFactory;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * テーブル定義出力（通常実行）のユースケースクラス<br>
 * DBから取得したスキーマ情報を、Markdownのドキュメントとスキーマのスナップショットとして出力先へ書き出す。 取得・書き出しの段取りは{@link SchemaExporter}に委ねる
 */
public class ExportTableDefinitionUsecaseImpl implements ExportTableDefinitionUsecase {

  private static final Logger logger = LogManager.getLogger(ExportTableDefinitionUsecaseImpl.class);
  private final SchemaExporter schemaExporter;
  private final MarkdownExportSinkFactory markdownSinkFactory;
  private final SnapshotExportSinkFactory snapshotSinkFactory;
  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;

  /**
   * コンストラクタ
   *
   * @param schemaExporter DBからスキーマ情報を取得し、出力形式ごとに書き出す段取りを担うクラス
   * @param markdownSinkFactory Markdownのドキュメントを書き出すExportSinkの生成クラス
   * @param snapshotSinkFactory スキーマのスナップショットを書き出すExportSinkの生成クラス
   * @param fileRepository 出力先ディレクトリの削除（{@code --rm-dist}）に用いるファイルリポジトリ
   * @param outputPathResolver 出力先パス解決クラス
   */
  @Inject
  public ExportTableDefinitionUsecaseImpl(
      SchemaExporter schemaExporter,
      MarkdownExportSinkFactory markdownSinkFactory,
      SnapshotExportSinkFactory snapshotSinkFactory,
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver) {
    this.schemaExporter = schemaExporter;
    this.markdownSinkFactory = markdownSinkFactory;
    this.snapshotSinkFactory = snapshotSinkFactory;
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
  }

  /** {@inheritDoc} */
  @Override
  public void exportTableDefinition(ExportRequest request) {
    // ベースディレクトリパス取得
    final Path outputBaseDir = outputPathResolver.resolveBaseOutputDir(request.outputPath());
    final ExportTargets targets = schemaExporter.fetchTargets(request.targetSelection());
    if (request.rmDist()) {
      // 削除は一括取得（サイドカーの読み込みを含む）に成功してから行う。
      // 取得に失敗した場合に、既存の出力だけが削除されて何も残らない状態にしないため
      removeOutputBaseDir(outputBaseDir);
    }
    schemaExporter.export(
        targets,
        List.of(
            markdownSinkFactory.create(outputBaseDir, request.erDiagramMaxNodes()),
            snapshotSinkFactory.create(outputBaseDir)),
        request.chunkSize());
  }

  /**
   * {@code --rm-dist}指定時に、出力先ベースディレクトリを書き込み前に削除するメソッド<br>
   * 削除されたテーブル等の残骸ファイルを残さないため、書き込み前にディレクトリごと削除する。
   * 削除してよいディレクトリか（既存のファイルを指していないか、ルート・ホームディレクトリ等でないか）は、 ユースケースを呼ぶ前に入口で検証済みであること
   *
   * @param outputBaseDir 出力先ベースディレクトリ
   */
  private void removeOutputBaseDir(Path outputBaseDir) {
    logger.info(
        "Removing existing output directory before export. [outputBaseDir={}]",
        outputBaseDir.toAbsolutePath().normalize());
    fileRepository.deleteDirectory(outputBaseDir);
  }
}
