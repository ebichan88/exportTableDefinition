package com.export_table_definition.application;

import com.export_table_definition.domain.model.relation.DiagramBoxes;
import com.export_table_definition.domain.model.relation.NodeLimit;
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
 * DBから取得したスキーマ情報を、Markdownのドキュメントとスキーマのスナップショットとして出力先へ書き出す。 取得・書き出しの段取りは{@link
 * SchemaExportPipeline}に委ねる
 */
public class ExportTableDefinitionUsecase {

  private static final Logger logger = LogManager.getLogger(ExportTableDefinitionUsecase.class);
  private final SchemaExportPipeline schemaExportPipeline;
  private final MarkdownExportSinkFactory markdownSinkFactory;
  private final SnapshotExportSinkFactory snapshotSinkFactory;
  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;

  @Inject
  public ExportTableDefinitionUsecase(
      SchemaExportPipeline schemaExportPipeline,
      MarkdownExportSinkFactory markdownSinkFactory,
      SnapshotExportSinkFactory snapshotSinkFactory,
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver) {
    this.schemaExportPipeline = schemaExportPipeline;
    this.markdownSinkFactory = markdownSinkFactory;
    this.snapshotSinkFactory = snapshotSinkFactory;
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
  }

  /** DBから取得したスキーマ情報を、Markdownのドキュメントとスキーマのスナップショットとして出力するメソッド */
  public void exportTableDefinition(ExportTableDefinitionRequest request) {
    final Path outputBaseDir = outputPathResolver.resolveBaseOutputDir(request.outputPath());
    final ExportTargets targets =
        schemaExportPipeline.fetchTargets(request.targetSelection(), request.sidecarPath());
    final DiagramBoxes diagramBoxes =
        schemaExportPipeline.fetchDiagramBoxes(targets, request.chunkSize());
    if (request.rmDist()) {
      // 削除は一括取得（サイドカーの読み込みを含む）に成功してから行う。
      // 取得に失敗した場合に、既存の出力だけが削除されて何も残らない状態にしないため
      removeOutputBaseDir(outputBaseDir);
    }
    schemaExportPipeline.export(
        targets,
        List.of(
            markdownSinkFactory.create(
                outputBaseDir, NodeLimit.of(request.erDiagramMaxNodes()), diagramBoxes),
            snapshotSinkFactory.create(outputBaseDir)),
        request.chunkSize());
  }

  /**
   * {@code --rm-dist}指定時に、出力先ベースディレクトリを書き込み前に削除するメソッド<br>
   * 削除されたテーブル等の残骸ファイルを残さないため、書き込み前にディレクトリごと削除する。
   * 削除してよいディレクトリか（既存のファイルを指していないか、ルート・ホームディレクトリ等でないか）は、 ユースケースを呼ぶ前に入口で検証済みであること
   */
  private void removeOutputBaseDir(Path outputBaseDir) {
    logger.info(
        "Removing existing output directory before export. [outputBaseDir={}]",
        outputBaseDir.toAbsolutePath().normalize());
    fileRepository.deleteDirectory(outputBaseDir);
  }
}
