package com.export_table_definition.application.impl;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.CheckDocumentDiffUsecase;
import com.export_table_definition.domain.model.snapshot.DiffResult;
import com.export_table_definition.domain.model.target.ExportTargets;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.export.SnapshotExportSinkFactory;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.snapshot.SnapshotDiffDomainService;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;

/**
 * DB vs ドキュメントの差分検知（{@code --check}モード）のユースケースクラス<br>
 * DBからの取得は通常実行と同じ{@link SchemaExporter}で行い、差分の判定に不要なMarkdownの描画・ER図の生成は行わず、
 * スナップショットのみを一時ディレクトリへ生成して、コミット済みのスナップショットとオブジェクト単位で比較する
 */
public class CheckDocumentDiffUsecaseImpl implements CheckDocumentDiffUsecase {

  private static final String CHECK_TEMP_DIR_PREFIX = "exportTableDefinition-check-";
  private final SchemaExporter schemaExporter;
  private final SnapshotExportSinkFactory snapshotSinkFactory;
  private final SnapshotDiffDomainService snapshotDiffDomainService;
  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;

  @Inject
  public CheckDocumentDiffUsecaseImpl(
      SchemaExporter schemaExporter,
      SnapshotExportSinkFactory snapshotSinkFactory,
      SnapshotDiffDomainService snapshotDiffDomainService,
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver) {
    this.schemaExporter = schemaExporter;
    this.snapshotSinkFactory = snapshotSinkFactory;
    this.snapshotDiffDomainService = snapshotDiffDomainService;
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
  }

  /** {@inheritDoc} */
  @Override
  public DiffResult checkDocumentDiff(CheckDiffRequest request) {
    final Path committedDir = outputPathResolver.resolveBaseOutputDir(request.outputPath());
    final Path generatedDir = fileRepository.createTempDirectory(CHECK_TEMP_DIR_PREFIX);
    try {
      final ExportTargets targets =
          schemaExporter.fetchTargets(request.targetSelection(), request.sidecarPath());
      schemaExporter.export(
          targets, List.of(snapshotSinkFactory.create(generatedDir)), request.chunkSize());
      final Path generatedSnapshotDir = outputPathResolver.resolveSnapshotDirectory(generatedDir);
      final Path committedSnapshotDir = outputPathResolver.resolveSnapshotDirectory(committedDir);
      return snapshotDiffDomainService.compare(generatedSnapshotDir, committedSnapshotDir);
    } finally {
      fileRepository.deleteDirectory(generatedDir);
    }
  }
}
