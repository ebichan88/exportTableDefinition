package com.export_table_definition.domain.service.writer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.writer.template.ObjectDefinitionTemplates;
import com.export_table_definition.domain.service.writer.template.ObjectListTemplates;
import com.export_table_definition.domain.service.writer.template.TableDefinitionListTemplates;
import com.export_table_definition.domain.service.writer.template.TableDefinitionTemplates;
import com.google.inject.Inject;

/**
 * テーブル定義を書き込むクラス
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class TableDefinitionWriterDomainService {

    private static final int MAX_TABLE_LIST_SIZE = 3000;
    private static final Logger logger = LogManager.getLogger(TableDefinitionWriterDomainService.class);
    private final FileRepository fileRepository;
    private final OutputPathResolver outputPathResolver;

    /**
     * コンストラクタ
     * 
     * @param fileRepository     ファイルリポジトリ
     * @param outputPathResolver 出力パス解決クラス
     */
    @Inject
    public TableDefinitionWriterDomainService(FileRepository fileRepository, OutputPathResolver outputPathResolver) {
        this.fileRepository = fileRepository;
        this.outputPathResolver = outputPathResolver;
    }

    /**
     * テーブル一覧の書き込み処理を行うメソッド
     * 
     * @param tables              テーブル情報リスト
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    public void writeTableDefinitionList(List<TableEntity> tables, BaseInfoEntity baseInfo, Path outputDirectoryPath,
            Map<String, String> relatedDocuments) {
        fileRepository.createDirectory(outputDirectoryPath);
        if (tables.size() > MAX_TABLE_LIST_SIZE) {
            // テーブル一覧の件数がMarkdownの表に表示できる最大件数を超える場合、テーブル一覧を分割して出力する
            writeMultipleTableFiles(tables, baseInfo, outputDirectoryPath);
            writeSummaryFile(tables.size(), baseInfo, outputDirectoryPath, relatedDocuments);
        } else {
            writeSingleTableFile(tables, baseInfo, outputDirectoryPath, relatedDocuments);
        }
    }

    /**
     * テーブル一覧を分割して出力するメソッド（3000テーブル毎）
     * 
     * @param tables              テーブル情報リスト
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    private void writeMultipleTableFiles(List<TableEntity> tables, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        final int total = tables.size();
        int processedCount = 0;
        // Markdownの表に表示できる最大件数毎に、テーブル一覧を分割して出力する
        for (int from = 0, page = 1; from < total; from += MAX_TABLE_LIST_SIZE, page++) {
            final int to = Math.min(from + MAX_TABLE_LIST_SIZE, total);
            final List<TableEntity> slice = tables.subList(from, to);
            final List<String> contents = new ArrayList<>();
            // ヘッダー
            contents.add(TableDefinitionListTemplates.fileHeader(baseInfo));
            // 基本情報
            contents.add(TableDefinitionListTemplates.baseInfo(baseInfo));
            // テーブル一覧ヘッダー
            contents.add(TableDefinitionListTemplates.tableListTableHeader());
            // 対象範囲テーブル行
            slice.forEach(table -> contents.add(TableDefinitionListTemplates.tableListLine(table)));
            contents.add(TableDefinitionListTemplates.lineSeparator());
            // フッター
            processedCount = to;
            contents.add(TableDefinitionListTemplates.writeTableListFooter(baseInfo, total, processedCount, page));
            fileRepository.writeFile(outputPathResolver.resolveTableListFile(baseInfo, outputDirectoryPath, page),
                    contents);
        }
    }

    /**
     * テーブル一覧のサマリーファイルを書き込むメソッド<br>
     * 分割したテーブル一覧のファイルへのリンクを記載する
     * 
     * @param totalFiles          分割したテーブル一覧ファイルの総数
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    private void writeSummaryFile(int totalFiles, BaseInfoEntity baseInfo, Path outputDirectoryPath,
            Map<String, String> relatedDocuments) {
        final List<String> contents = new ArrayList<>();
        // ヘッダー
        contents.add(TableDefinitionListTemplates.fileHeader(baseInfo));
        // 基本情報
        contents.add(TableDefinitionListTemplates.baseInfo(baseInfo));
        // 関連ドキュメント（トリガー・関数・シーケンス・型の一覧へのリンク）
        contents.add(TableDefinitionListTemplates.relatedDocuments(baseInfo, relatedDocuments));
        // 分割したテーブル一覧のリンク
        for (int i = 1; i <= totalFiles / MAX_TABLE_LIST_SIZE + (totalFiles % MAX_TABLE_LIST_SIZE > 0 ? 1 : 0); i++) {
            contents.add(TableDefinitionListTemplates.subTableListLink(baseInfo, i));
        }
        fileRepository.writeFile(outputPathResolver.resolveTableListFile(baseInfo, outputDirectoryPath), contents);
    }

    /**
     * テーブル一覧を1ファイルにまとめて出力するメソッド
     * 
     * @param tables              テーブル情報リスト
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    private void writeSingleTableFile(List<TableEntity> tables, BaseInfoEntity baseInfo, Path outputDirectoryPath,
            Map<String, String> relatedDocuments) {
        final List<String> contents = List.of(TableDefinitionListTemplates.fileHeader(baseInfo), // ヘッダー
                TableDefinitionListTemplates.baseInfo(baseInfo), // 基本情報
                TableDefinitionListTemplates.relatedDocuments(baseInfo, relatedDocuments), // 関連ドキュメント
                TableDefinitionListTemplates.tableList(tables) // テーブル一覧
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

    /**
     * トリガー一覧の書き込み処理を行うメソッド<br>
     * トリガーが存在しない場合は何も出力しない
     *
     * @param triggers            トリガー情報リスト
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    public void writeTriggerList(List<TriggerEntity> triggers, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        if (triggers.isEmpty()) {
            return;
        }
        final List<String> contents = List.of(ObjectListTemplates.fileHeader("トリガー一覧", baseInfo),
                ObjectListTemplates.baseInfo(baseInfo), ObjectListTemplates.triggerList(triggers));
        fileRepository.writeFile(outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, "trigger"),
                contents);
    }

    /**
     * 関数・プロシージャ一覧の書き込み処理を行うメソッド<br>
     * 対象が存在しない場合は何も出力しない
     *
     * @param functions           関数・プロシージャの一覧情報リスト
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    public void writeFunctionList(List<FunctionEntity> functions, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        if (functions.isEmpty()) {
            return;
        }
        final List<String> contents = List.of(ObjectListTemplates.fileHeader("関数・プロシージャ一覧", baseInfo),
                ObjectListTemplates.baseInfo(baseInfo), ObjectListTemplates.functionList(functions));
        fileRepository.writeFile(outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, "function"),
                contents);
    }

    /**
     * 関数・プロシージャの個別定義書き込み処理を行うメソッド
     *
     * @param function            関数・プロシージャ情報（定義本体を含む）
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    public void writeFunctionDefinition(FunctionEntity function, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        final Path directoryPath = outputPathResolver.resolveSchemaObjectDirectory(baseInfo, outputDirectoryPath,
                function.schemaName(), "function");
        final Path filePath = outputPathResolver.resolveSchemaObjectFile(baseInfo, outputDirectoryPath,
                function.schemaName(), "function", function.fileName());
        fileRepository.createDirectory(directoryPath);
        fileRepository.writeFile(filePath, List.of(ObjectDefinitionTemplates.functionFile(function, baseInfo)));
        logger.debug("exportFunctionDefinition complete. [filePath={}]", filePath.toString());
    }

    /**
     * シーケンス一覧の書き込み処理を行うメソッド<br>
     * 対象が存在しない場合は何も出力しない
     *
     * @param sequences           シーケンス情報リスト
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    public void writeSequenceList(List<SequenceEntity> sequences, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        if (sequences.isEmpty()) {
            return;
        }
        final List<String> contents = List.of(ObjectListTemplates.fileHeader("シーケンス一覧", baseInfo),
                ObjectListTemplates.baseInfo(baseInfo), ObjectListTemplates.sequenceList(sequences));
        fileRepository.writeFile(outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, "sequence"),
                contents);
    }

    /**
     * シーケンスの個別定義書き込み処理を行うメソッド
     *
     * @param sequence            シーケンス情報
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    public void writeSequenceDefinition(SequenceEntity sequence, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        final Path directoryPath = outputPathResolver.resolveSchemaObjectDirectory(baseInfo, outputDirectoryPath,
                sequence.schemaName(), "sequence");
        final Path filePath = outputPathResolver.resolveSchemaObjectFile(baseInfo, outputDirectoryPath,
                sequence.schemaName(), "sequence", sequence.sequenceName());
        fileRepository.createDirectory(directoryPath);
        fileRepository.writeFile(filePath, List.of(ObjectDefinitionTemplates.sequenceFile(sequence, baseInfo)));
        logger.debug("exportSequenceDefinition complete. [filePath={}]", filePath.toString());
    }

    /**
     * ユーザー定義型一覧の書き込み処理を行うメソッド<br>
     * 対象が存在しない場合は何も出力しない
     *
     * @param types               ユーザー定義型情報リスト
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    public void writeTypeList(List<TypeEntity> types, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        if (types.isEmpty()) {
            return;
        }
        final List<String> contents = List.of(ObjectListTemplates.fileHeader("ユーザー定義型一覧", baseInfo),
                ObjectListTemplates.baseInfo(baseInfo), ObjectListTemplates.typeList(types));
        fileRepository.writeFile(outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, "type"),
                contents);
    }

    /**
     * ユーザー定義型の個別定義書き込み処理を行うメソッド
     *
     * @param type                ユーザー定義型情報
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    public void writeTypeDefinition(TypeEntity type, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        final Path directoryPath = outputPathResolver.resolveSchemaObjectDirectory(baseInfo, outputDirectoryPath,
                type.schemaName(), "type");
        final Path filePath = outputPathResolver.resolveSchemaObjectFile(baseInfo, outputDirectoryPath,
                type.schemaName(), "type", type.typeName());
        fileRepository.createDirectory(directoryPath);
        fileRepository.writeFile(filePath, List.of(ObjectDefinitionTemplates.typeFile(type, baseInfo)));
        logger.debug("exportTypeDefinition complete. [filePath={}]", filePath.toString());
    }
}
