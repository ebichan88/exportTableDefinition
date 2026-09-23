package com.export_table_definition.domain.service.writer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PageLayout;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PagedSection;
import com.export_table_definition.domain.service.writer.template.TableDefinitionListTemplates;
import com.export_table_definition.domain.service.writer.template.TableDefinitionTemplates;
import com.google.inject.Inject;

/**
 * テーブル一覧・テーブル定義書を書き込むクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class TableDefinitionWriterDomainService {

    private static final Logger logger = LogManager.getLogger(TableDefinitionWriterDomainService.class);
    private final FileRepository fileRepository;
    private final OutputPathResolver outputPathResolver;
    private final PagedSectionWriter pagedSectionWriter;

    /**
     * コンストラクタ
     *
     * @param fileRepository     ファイルリポジトリ
     * @param outputPathResolver 出力パス解決クラス
     * @param pagedSectionWriter 行数の多い表のページ分割書き込みを行うクラス
     */
    @Inject
    public TableDefinitionWriterDomainService(FileRepository fileRepository, OutputPathResolver outputPathResolver,
            PagedSectionWriter pagedSectionWriter) {
        this.fileRepository = fileRepository;
        this.outputPathResolver = outputPathResolver;
        this.pagedSectionWriter = pagedSectionWriter;
    }

    /**
     * テーブル一覧の書き込み処理を行うメソッド<br>
     * 行数がMarkdownの表に表示できる最大件数を超える場合は、別ファイルへ分割し、
     * 本体ページにはリンクのみを掲載する
     *
     * @param tables              テーブル情報リスト
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    public void writeTableDefinitionList(List<TableEntity> tables, BaseInfoEntity baseInfo, Path outputDirectoryPath,
            Map<String, String> relatedDocuments) {
        fileRepository.createDirectory(outputDirectoryPath);
        final PagedSection<TableEntity> section = new PagedSection<>("テーブル情報",
                TableDefinitionListTemplates.tableListTableHeader(), tables,
                (no, table) -> TableDefinitionListTemplates.tableListLine(table));
        final PageLayout layout = new PageLayout(TableDefinitionListTemplates.fileHeader(baseInfo),
                page -> outputPathResolver.resolveTableListFile(baseInfo, outputDirectoryPath, page),
                page -> String.format("./tableList_%s_%d.md", baseInfo.dbName(), page),
                String.format("./tableList_%s.md", baseInfo.dbName()), "テーブル一覧へ");
        final List<String> contents = List.of(TableDefinitionListTemplates.fileHeader(baseInfo), // ヘッダー
                TableDefinitionListTemplates.baseInfo(baseInfo), // 基本情報
                TableDefinitionListTemplates.relatedDocuments(baseInfo, relatedDocuments), // 関連ドキュメント
                pagedSectionWriter.writePagedSection(section, layout) // テーブル一覧
        );
        fileRepository.writeFile(outputPathResolver.resolveTableListFile(baseInfo, outputDirectoryPath), contents);
    }

    /**
     * テーブル定義の書き込み処理を行うメソッド
     *
     * @param content テーブル定義出力に必要な情報をまとめたレコード
     */
    public void writeTableDefinition(TableDefinitionContent content) {
        final Path directoryPath = outputPathResolver.resolveTableDefinitionDirectory(content.baseInfo(),
                content.table(), content.outputBaseDir());
        final Path filePath = outputPathResolver.resolveTableDefinitionFile(content.baseInfo(), content.table(),
                content.outputBaseDir());
        final List<String> contents = List.of(TableDefinitionTemplates.fileHeader(content.table()), // ヘッダー
                TableDefinitionTemplates.baseInfo(content.baseInfo()), // 基本情報
                TableDefinitionTemplates.tableExplanation(), // テーブル説明
                TableDefinitionTemplates.tableInfo(content.table()), // テーブル情報
                TableDefinitionTemplates.columns(content.columns(), content.table()), // カラム情報
                TableDefinitionTemplates.view(content.table()), // View情報
                TableDefinitionTemplates.indexes(content.indexes(), content.table()), // インデックス情報
                TableDefinitionTemplates.constraints(content.constraints(), content.table()), // 制約情報
                TableDefinitionTemplates.foreignKeys(content.foreignKeys(), content.table()), // 外部キー情報
                TableDefinitionTemplates.triggers(content.triggers(), content.table()), // トリガー情報
                TableDefinitionTemplates.erDiagram(content.table(), content.columns(), content.foreignKeys(),
                        content.incomingForeignKeys()), // ER図
                TableDefinitionTemplates.footer(content.baseInfo()) // フッター
        );
        fileRepository.createDirectory(directoryPath);
        fileRepository.writeFile(filePath, contents);
        logger.debug("exportTableDefinition complete. [filePath={}]", filePath.toString());
    }
}
