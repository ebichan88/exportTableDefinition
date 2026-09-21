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
    public void exportTableDefinition(List<String> targetSchemaList, List<String> targetTableList, String outputPath) {
        // ベースディレクトリパス取得
        final Path outputBaseDir = Optional.ofNullable(outputPath).filter(StringUtils::isNotBlank).map(Paths::get)
                .orElse(Paths.get(OUTPUT_BASE_DIRECTORY));

        // 基本情報・テーブル一覧（1テーブル1行の軽量情報）のみ先に取得する
        final BaseInfoEntity baseInfoEntity = repository.selectBaseInfo();
        final List<TableEntity> tableEntityList = repository.selectTableList(targetSchemaList, targetTableList);

        // テーブル一覧出力 -> ./output/ or {設定ファイルのFileParh}/tableList_{DB名}.md
        writer.writeTableDefinitionList(tableEntityList, baseInfoEntity, outputBaseDir);

        // テーブル定義出力 -> ./output/ or {設定ファイルのFileParh}/{DB名}/{スキーマ名}/{TBL分類}/{物理テーブル名}.md
        // カラム・インデックス・制約・外部キーはスキーマ単位で取得・出力・破棄することで、
        // 全テーブル分を同時にメモリ保持せず、ピークメモリを最大1スキーマ分に抑える
        final Map<String, List<TableEntity>> tablesBySchema = tableEntityList.stream()
                .collect(Collectors.groupingBy(TableEntity::schemaName, LinkedHashMap::new, Collectors.toList()));
        tablesBySchema.forEach((schemaName, tablesInSchema) -> exportSchemaTableDefinitions(schemaName, tablesInSchema,
                targetSchemaList, targetTableList, baseInfoEntity, outputBaseDir));
    }

    /**
     * 指定スキーマに属するテーブルの定義書を出力するメソッド<br>
     * 当該スキーマ分の詳細情報（カラム・インデックス・制約・外部キー）のみを取得し、
     * 出力後にローカル変数のスコープを抜けることでメモリ解放対象とする
     *
     * @param schemaName       出力対象のスキーマ名
     * @param tablesInSchema   当該スキーマに属するテーブルのリスト
     * @param targetSchemaList テーブル定義出力対象のスキーマのリスト（書き込み要否判定に利用）
     * @param targetTableList  テーブル定義出力対象のテーブルのリスト（取得・書き込み要否判定に利用）
     * @param baseInfoEntity   データベースの基本情報
     * @param outputBaseDir    出力先のベースディレクトリパス
     */
    private void exportSchemaTableDefinitions(String schemaName, List<TableEntity> tablesInSchema,
            List<String> targetSchemaList, List<String> targetTableList, BaseInfoEntity baseInfoEntity,
            Path outputBaseDir) {
        final List<String> schemaList = List.of(schemaName);
        final Columns columns = Columns.of(repository.selectColumnList(schemaList, targetTableList));
        final Indexes indexes = Indexes.of(repository.selectIndexList(schemaList, targetTableList));
        final Constraints constraints = Constraints.of(repository.selectConstraintList(schemaList, targetTableList));
        final ForeignKeys foreignKeys = ForeignKeys.of(repository.selectForeignKeyList(schemaList, targetTableList));

        tablesInSchema.stream()
                .filter(tableEntity -> tableEntity.needsWriteTableDefinition(targetSchemaList, targetTableList))
                .map(tableEntity -> TableDefinitionContent.assemble(baseInfoEntity, tableEntity, columns, indexes,
                        constraints, foreignKeys, outputBaseDir))
                .forEach(writer::writeTableDefinition);
    }
}
