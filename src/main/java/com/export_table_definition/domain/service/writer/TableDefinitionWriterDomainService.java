package com.export_table_definition.domain.service.writer;

import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.target.TableDefinitionContent;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.path.OutputRoot;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PageLayout;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PagedSection;
import com.export_table_definition.domain.service.writer.template.TableDefinitionListTemplates;
import com.export_table_definition.domain.service.writer.template.TableDefinitionTemplates;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** テーブル一覧・テーブル定義書を書き込むクラス */
public class TableDefinitionWriterDomainService {

  private static final Logger logger =
      LogManager.getLogger(TableDefinitionWriterDomainService.class);
  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;
  private final PagedSectionWriter pagedSectionWriter;

  /**
   * コンストラクタ
   *
   * @param fileRepository ファイルリポジトリ
   * @param outputPathResolver 出力パス解決クラス
   * @param pagedSectionWriter 行数の多い表のページ分割書き込みを行うクラス
   */
  @Inject
  public TableDefinitionWriterDomainService(
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver,
      PagedSectionWriter pagedSectionWriter) {
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
    this.pagedSectionWriter = pagedSectionWriter;
  }

  /**
   * テーブル一覧の書き込み処理を行うメソッド<br>
   * 行数がMarkdownの表に表示できる最大件数を超える場合は、別ファイルへ分割し、 本体ページにはリンクのみを掲載する
   *
   * @param tables テーブル情報リスト
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   * @param relatedDocuments 「関連ドキュメント」としてリンクを掲載する一覧の種別（掲載順）
   */
  public void writeTableDefinitionList(
      List<TableEntity> tables, OutputRoot outputRoot, List<ListDocumentType> relatedDocuments) {
    fileRepository.createDirectory(outputRoot.baseDir());
    final PagedSection<TableEntity> section =
        new PagedSection<>(
            "テーブル情報",
            TableDefinitionListTemplates.tableListTableHeader(),
            tables,
            TableDefinitionListTemplates::tableListLine);
    final PageLayout layout =
        new PageLayout(
            TableDefinitionListTemplates.fileHeader(outputRoot.baseInfo()),
            outputPathResolver.resolveListFile(outputRoot, ListDocumentType.TABLE),
            ListDocumentType.TABLE.getBackLinkLabel());
    final String tableListSection = pagedSectionWriter.writePagedSection(section, layout);
    final List<String> contents =
        List.of(
            layout.fileHeader(), // ヘッダー
            TableDefinitionListTemplates.baseInfo(outputRoot.baseInfo()), // 基本情報
            TableDefinitionListTemplates.relatedDocuments(
                outputRoot.baseInfo(), relatedDocuments), // 関連ドキュメント
            tableListSection);
    fileRepository.writeFile(layout.file(), contents);
  }

  /**
   * テーブル定義の書き込み処理を行うメソッド
   *
   * @param content テーブル定義出力に必要な情報をまとめたレコード
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  public void writeTableDefinition(TableDefinitionContent content, Path outputDirectoryPath) {
    final OutputRoot outputRoot = new OutputRoot(outputDirectoryPath, content.baseInfo());
    final Path directoryPath =
        outputPathResolver.resolveTableDefinitionDirectory(outputRoot, content.table());
    final Path filePath =
        outputPathResolver.resolveTableDefinitionFile(outputRoot, content.table());
    final List<String> contents =
        List.of(
            TableDefinitionTemplates.fileHeader(content.table()), // ヘッダー
            TableDefinitionTemplates.baseInfo(content.baseInfo()), // 基本情報
            TableDefinitionTemplates.tableExplanation(content.annotation()), // テーブル説明
            TableDefinitionTemplates.tableInfo(content.table(), content.annotation()), // テーブル情報
            TableDefinitionTemplates.viewpoints(content.viewpoints(), content.baseInfo()), // 所属する観点
            TableDefinitionTemplates.columns(content.columns(), content.annotation()), // カラム情報
            TableDefinitionTemplates.view(content.table()), // View情報
            TableDefinitionTemplates.indexes(content.indexes()), // インデックス情報
            TableDefinitionTemplates.constraints(content.constraints()), // 制約情報
            TableDefinitionTemplates.foreignKeys(content.foreignKeys()), // 外部キー情報
            TableDefinitionTemplates.logicalRelations(content.logicalRelations()), // 論理リレーション情報
            TableDefinitionTemplates.triggers(content.triggers()), // トリガー情報
            TableDefinitionTemplates.erDiagram(
                content.table(),
                content.columns(),
                content.outgoingRelations(),
                content.incomingRelations()), // ER図
            TableDefinitionTemplates.footer(content.baseInfo()) // フッター
            );
    fileRepository.createDirectory(directoryPath);
    fileRepository.writeFile(filePath, contents);
    logger.debug("exportTableDefinition complete. [filePath={}]", filePath.toString());
  }
}
