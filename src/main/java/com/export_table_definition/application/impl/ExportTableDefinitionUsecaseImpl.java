package com.export_table_definition.application.impl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.collection.Columns;
import com.export_table_definition.domain.model.collection.Constraints;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.collection.Indexes;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.domain.service.writer.TableDefinitionWriterDomainService;
import com.google.inject.Inject;

/**
 * テーブル定義出力に関するユースケースクラス
 * 
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinitionUsecaseImpl implements ExportTableDefinitionUsecase {

    private static final String OUTPUT_BASE_DIRECTORY = "./output";
    private final TableDefinitionRepository repository;
    private final TableDefinitionWriterDomainService writer;

    /**
     * コンストラクタ
     * 
     * @param repository テーブル定義出力に関するリポジトリクラス
     * @param writer     テーブル定義を書き込むクラス
     */
    @Inject
    public ExportTableDefinitionUsecaseImpl(TableDefinitionRepository repository,
            TableDefinitionWriterDomainService writer) {
        this.repository = repository;
        this.writer = writer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exportTableDefinition(List<String> targetSchemaList, List<String> targetTableList, String outputPath,
            int chunkSize) {
        // ベースディレクトリパス取得
        final Path outputBaseDir = Optional.ofNullable(outputPath).filter(StringUtils::isNotBlank).map(Paths::get)
                .orElse(Paths.get(OUTPUT_BASE_DIRECTORY));

        // 基本情報・テーブル一覧（1テーブル1行の軽量情報）のみ先に取得する
        final BaseInfoEntity baseInfoEntity = repository.selectBaseInfo();
        final List<TableEntity> tableEntityList = repository.selectTableList(targetSchemaList, targetTableList);

        // テーブル一覧出力 -> ./output/ or {設定ファイルのFileParh}/tableList_{DB名}.md
        writer.writeTableDefinitionList(tableEntityList, baseInfoEntity, outputBaseDir);

        // テーブル定義出力 -> ./output/ or {設定ファイルのFileParh}/{DB名}/{スキーマ名}/{TBL分類}/{物理テーブル名}.md
        // カラム・インデックス・制約・外部キーは、スキーマ内でさらにchunkSize件ずつに分割して取得・出力・破棄する。
        // これにより、テーブルが1スキーマに集中していても、同時にメモリ保持する詳細情報を最大chunkSize件分に抑える
        final Map<String, List<TableEntity>> tablesBySchema = tableEntityList.stream()
                .collect(Collectors.groupingBy(TableEntity::schemaName, LinkedHashMap::new, Collectors.toList()));
        tablesBySchema.forEach((schemaName, tablesInSchema) -> exportSchemaTableDefinitions(schemaName, tablesInSchema,
                targetSchemaList, targetTableList, baseInfoEntity, outputBaseDir, chunkSize));
    }

    /**
     * 指定スキーマに属するテーブルの定義書を、chunkSize件ずつに分割して出力するメソッド
     *
     * @param schemaName       出力対象のスキーマ名
     * @param tablesInSchema   当該スキーマに属するテーブルのリスト
     * @param targetSchemaList テーブル定義出力対象のスキーマのリスト（書き込み要否判定に利用）
     * @param targetTableList  テーブル定義出力対象のテーブルのリスト（書き込み要否判定に利用）
     * @param baseInfoEntity   データベースの基本情報
     * @param outputBaseDir    出力先のベースディレクトリパス
     * @param chunkSize        1回の取得でまとめて処理するテーブル数の上限。0以下の場合は分割しない
     */
    private void exportSchemaTableDefinitions(String schemaName, List<TableEntity> tablesInSchema,
            List<String> targetSchemaList, List<String> targetTableList, BaseInfoEntity baseInfoEntity,
            Path outputBaseDir, int chunkSize) {
        final int total = tablesInSchema.size();
        // chunkSizeが0以下の場合はスキーマ全体を1チャンクとして扱う
        final int step = chunkSize > 0 ? chunkSize : total;
        for (int from = 0; from < total; from += step) {
            final int to = Math.min(from + step, total);
            exportTableDefinitionChunk(schemaName, tablesInSchema.subList(from, to), targetSchemaList, targetTableList,
                    baseInfoEntity, outputBaseDir);
        }
    }

    /**
     * 1チャンク分のテーブルの定義書を出力するメソッド<br>
     * 当該チャンクのテーブルに紐づく詳細情報（カラム・インデックス・制約・外部キー）のみを取得し、
     * 出力後にローカル変数のスコープを抜けることでメモリ解放対象とする
     *
     * @param schemaName       出力対象のスキーマ名
     * @param chunk            1チャンク分のテーブルのリスト
     * @param targetSchemaList テーブル定義出力対象のスキーマのリスト（書き込み要否判定に利用）
     * @param targetTableList  テーブル定義出力対象のテーブルのリスト（書き込み要否判定に利用）
     * @param baseInfoEntity   データベースの基本情報
     * @param outputBaseDir    出力先のベースディレクトリパス
     */
    private void exportTableDefinitionChunk(String schemaName, List<TableEntity> chunk, List<String> targetSchemaList,
            List<String> targetTableList, BaseInfoEntity baseInfoEntity, Path outputBaseDir) {
        final List<String> schemaList = List.of(schemaName);
        // 当該チャンクのテーブル名のみを条件に詳細情報を取得する
        final List<String> chunkTableList = chunk.stream().map(TableEntity::physicalTableName).distinct().toList();
        final Columns columns = Columns.of(repository.selectColumnList(schemaList, chunkTableList));
        final Indexes indexes = Indexes.of(repository.selectIndexList(schemaList, chunkTableList));
        final Constraints constraints = Constraints.of(repository.selectConstraintList(schemaList, chunkTableList));
        final ForeignKeys foreignKeys = ForeignKeys.of(repository.selectForeignKeyList(schemaList, chunkTableList));

        chunk.stream()
                .filter(tableEntity -> tableEntity.needsWriteTableDefinition(targetSchemaList, targetTableList))
                .map(tableEntity -> TableDefinitionContent.assemble(baseInfoEntity, tableEntity, columns, indexes,
                        constraints, foreignKeys, outputBaseDir))
                .forEach(writer::writeTableDefinition);
    }
}
