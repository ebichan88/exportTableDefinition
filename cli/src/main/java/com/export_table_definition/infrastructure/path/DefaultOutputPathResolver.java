package com.export_table_definition.infrastructure.path;

import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.snapshot.SnapshotKind;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import com.export_table_definition.domain.service.path.DocumentLocations;
import com.export_table_definition.domain.service.path.InsightLocations;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.path.OutputRoot;
import com.export_table_definition.domain.service.path.SnapshotLocations;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 出力パス解決のデフォルト実装<br>
 * Markdownドキュメントのファイル名・配置は{@link DocumentLocations}、スナップショットのそれは{@link SnapshotLocations}の規則に従い、
 * 出力ベースディレクトリを起点に解決する
 */
public class DefaultOutputPathResolver implements OutputPathResolver {

  private static final String DEFAULT_OUTPUT_DIRECTORY = "./output";

  /** {@inheritDoc} */
  @Override
  public Path resolveBaseOutputDir(String outputPath) {
    return Optional.ofNullable(outputPath)
        .filter(Predicate.not(String::isBlank))
        .map(Paths::get)
        .orElse(Paths.get(DEFAULT_OUTPUT_DIRECTORY));
  }

  /** {@inheritDoc} */
  @Override
  public boolean isRemovableOutputDir(Path baseOutputDir) {
    final Path absolute = baseOutputDir.toAbsolutePath().normalize();
    final Path cwd = Path.of("").toAbsolutePath().normalize();
    final Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    return absolute.getParent() != null && !absolute.equals(cwd) && !absolute.equals(home);
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveDatabaseDirectory(OutputRoot root) {
    return root.baseDir().resolve(root.baseInfo().dbName());
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveTableDefinitionDirectory(OutputRoot root, TableEntity table) {
    return resolveTableDefinitionFile(root, table).getParent();
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveTableDefinitionFile(OutputRoot root, TableEntity table) {
    return resolveDatabaseDirectory(root).resolve(DocumentLocations.tableDefinitionFile(table));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveListFile(OutputRoot root, ListDocumentType type) {
    return resolveDatabaseDirectory(root)
        .resolve(DocumentLocations.listFile(type, root.baseInfo().dbName()));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveErDiagramFile(OutputRoot root, String schemaName) {
    return resolveDatabaseDirectory(root)
        .resolve(DocumentLocations.erDiagramFile(root.baseInfo().dbName(), schemaName));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveErDiagramGroupFile(OutputRoot root, String schemaName, int groupNo) {
    return resolveDatabaseDirectory(root)
        .resolve(
            DocumentLocations.erDiagramGroupFile(root.baseInfo().dbName(), schemaName, groupNo));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveViewpointFile(OutputRoot root, Viewpoint viewpoint) {
    return resolveDatabaseDirectory(root)
        .resolve(DocumentLocations.viewpointFile(root.baseInfo().dbName(), viewpoint));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveReadmeFile(OutputRoot root) {
    return resolveDatabaseDirectory(root).resolve(DocumentLocations.readmeFile());
  }

  /** {@inheritDoc} */
  @Override
  public Path resolvePageFile(Path file, int pageIndex) {
    return file.resolveSibling(
        DocumentLocations.pageFile(String.valueOf(file.getFileName()), pageIndex));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveSchemaObjectDirectory(
      OutputRoot root, String schemaName, ListDocumentType kind) {
    return resolveDatabaseDirectory(root)
        .resolve(DocumentLocations.schemaObjectDirectory(schemaName, kind));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveSchemaObjectFile(
      OutputRoot root, String schemaName, ListDocumentType kind, String name) {
    return resolveDatabaseDirectory(root)
        .resolve(DocumentLocations.schemaObjectFile(schemaName, kind, name));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveSnapshotDirectory(Path baseOutputDir) {
    return baseOutputDir.resolve(SnapshotLocations.snapshotDirectory());
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveSnapshotDatabaseFile(OutputRoot root) {
    return resolveSnapshotDirectory(root.baseDir())
        .resolve(SnapshotLocations.databaseFile(root.baseInfo().dbName()));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveSnapshotFile(OutputRoot root, String schemaName, SnapshotKind kind) {
    return resolveSnapshotDirectory(root.baseDir())
        .resolve(SnapshotLocations.objectFile(root.baseInfo().dbName(), schemaName, kind));
  }

  /** {@inheritDoc} */
  @Override
  public Optional<SnapshotKind> resolveSnapshotKind(Path snapshotFile) {
    return SnapshotLocations.kindOf(String.valueOf(snapshotFile.getFileName()));
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveInsightsDirectory(Path baseOutputDir) {
    return baseOutputDir.resolve(InsightLocations.insightsDirectory());
  }

  /** {@inheritDoc} */
  @Override
  public Path resolveViewpointsInsightFile(OutputRoot root) {
    return resolveInsightsDirectory(root.baseDir())
        .resolve(InsightLocations.viewpointsFile(root.baseInfo().dbName()));
  }
}
