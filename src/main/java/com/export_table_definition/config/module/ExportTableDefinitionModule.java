package com.export_table_definition.config.module;

import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.application.impl.ExportTableDefinitionUsecaseImpl;
import com.export_table_definition.domain.repository.AnnotationRepository;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.domain.service.UnifiedDiffGenerator;
import com.export_table_definition.domain.service.export.MarkdownExportSinkFactory;
import com.export_table_definition.domain.service.export.SnapshotExportSinkFactory;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.snapshot.SchemaSnapshotWriterDomainService;
import com.export_table_definition.domain.service.snapshot.SnapshotDiffDomainService;
import com.export_table_definition.domain.service.snapshot.SnapshotSerializer;
import com.export_table_definition.domain.service.target.ExportTargetConsistencyDomainService;
import com.export_table_definition.domain.service.writer.ErDiagramWriterDomainService;
import com.export_table_definition.domain.service.writer.ObjectListWriterDomainService;
import com.export_table_definition.domain.service.writer.PagedSectionWriter;
import com.export_table_definition.domain.service.writer.TableDefinitionWriterDomainService;
import com.export_table_definition.infrastructure.db.type.DatabaseType;
import com.export_table_definition.infrastructure.file.repository.AnnotationYamlRepository;
import com.export_table_definition.infrastructure.file.repository.LocalFileRepository;
import com.export_table_definition.infrastructure.path.DefaultOutputPathResolver;
import com.export_table_definition.infrastructure.snapshot.JacksonSnapshotSerializer;
import com.google.inject.AbstractModule;

/**
 * 依存関係を管理するクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinitionModule extends AbstractModule {

  private final DatabaseType databaseType;

  /**
   * コンストラクタ<br>
   * 束縛の定義（{@link #configure()}）の中でDBへ接続しないよう、接続先DBの種別は呼び出し元で判定して受け取る
   *
   * @param databaseType 接続先DBの種別（{@link TableDefinitionRepository}の実装クラスの選択に用いる）
   */
  public ExportTableDefinitionModule(DatabaseType databaseType) {
    this.databaseType = databaseType;
  }

  @Override
  protected void configure() {
    bind(TableDefinitionRepository.class).to(databaseType.getRepositoryClass());
    bind(ExportTableDefinitionUsecase.class).to(ExportTableDefinitionUsecaseImpl.class);
    bind(FileRepository.class).to(LocalFileRepository.class);
    bind(AnnotationRepository.class).to(AnnotationYamlRepository.class);
    bind(OutputPathResolver.class).to(DefaultOutputPathResolver.class);
    bind(SnapshotSerializer.class).to(JacksonSnapshotSerializer.class);
    bind(PagedSectionWriter.class);
    bind(TableDefinitionWriterDomainService.class);
    bind(ErDiagramWriterDomainService.class);
    bind(ObjectListWriterDomainService.class);
    bind(SchemaSnapshotWriterDomainService.class);
    bind(SnapshotDiffDomainService.class);
    bind(UnifiedDiffGenerator.class);
    bind(ExportTargetConsistencyDomainService.class);
    bind(MarkdownExportSinkFactory.class);
    bind(SnapshotExportSinkFactory.class);
  }
}
