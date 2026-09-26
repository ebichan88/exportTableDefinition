package com.export_table_definition.config.module;

import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.repository.SidecarRepository;
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
import com.export_table_definition.domain.service.writer.ViewpointWriterDomainService;
import com.export_table_definition.infrastructure.file.repository.LocalFileRepository;
import com.export_table_definition.infrastructure.file.repository.SidecarYamlRepository;
import com.export_table_definition.infrastructure.path.DefaultOutputPathResolver;
import com.export_table_definition.infrastructure.snapshot.JacksonSnapshotSerializer;
import com.google.inject.AbstractModule;
import java.time.Clock;

/**
 * DB種別に依存しない依存関係を束縛するモジュール<br>
 * DBへ接続する前（入力の検証の時点）に組み立てるDIコンテナの束縛を定義する。ファイル入出力・出力先パスの解決・ドメインサービス等、
 * 接続先のDB種別で実装が変わらないものはここに束縛する。DB種別が決まってから束縛するもの（{@link
 * com.export_table_definition.domain.repository.TableDefinitionRepository}と、それに依存するユースケース）は、
 * このモジュールで組み立てたコンテナの子として{@link DatabaseDependentModule}で束縛する
 */
public class ExportTableDefinitionModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(FileRepository.class).to(LocalFileRepository.class);
    bind(SidecarRepository.class).to(SidecarYamlRepository.class);
    bind(OutputPathResolver.class).to(DefaultOutputPathResolver.class);
    bind(SnapshotSerializer.class).to(JacksonSnapshotSerializer.class);
    // ドキュメントの生成日は実行環境のタイムゾーンでの日付とする
    bind(Clock.class).toInstance(Clock.systemDefaultZone());
    bind(PagedSectionWriter.class);
    bind(TableDefinitionWriterDomainService.class);
    bind(ErDiagramWriterDomainService.class);
    bind(ObjectListWriterDomainService.class);
    bind(ViewpointWriterDomainService.class);
    bind(SchemaSnapshotWriterDomainService.class);
    bind(SnapshotDiffDomainService.class);
    bind(UnifiedDiffGenerator.class);
    bind(ExportTargetConsistencyDomainService.class);
    bind(MarkdownExportSinkFactory.class);
    bind(SnapshotExportSinkFactory.class);
  }
}
