package com.export_table_definition.application.impl;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.collection.Columns;
import com.export_table_definition.domain.model.collection.Constraints;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.collection.Indexes;
import com.export_table_definition.domain.model.collection.Triggers;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import com.export_table_definition.domain.model.type.OutputObjectType;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.domain.service.writer.ErDiagramWriterDomainService;
import com.export_table_definition.domain.service.writer.ObjectListWriterDomainService;
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
    private final ErDiagramWriterDomainService erDiagramWriter;
    private final ObjectListWriterDomainService objectListWriter;

    /**
     * コンストラクタ
     *
     * @param repository       テーブル定義出力に関するリポジトリクラス
     * @param writer           テーブル一覧・テーブル定義書を書き込むクラス
     * @param erDiagramWriter  ER図を書き込むクラス
     * @param objectListWriter トリガー・関数・シーケンス・型の一覧および個別定義を書き込むクラス
     */
    @Inject
    public ExportTableDefinitionUsecaseImpl(TableDefinitionRepository repository,
            TableDefinitionWriterDomainService writer, ErDiagramWriterDomainService erDiagramWriter,
            ObjectListWriterDomainService objectListWriter) {
        this.repository = repository;
        this.writer = writer;
        this.erDiagramWriter = erDiagramWriter;
        this.objectListWriter = objectListWriter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exportTableDefinition(List<String> targetSchemaList, List<String> targetTableList, String outputPath,
            int chunkSize, int erDiagramMaxNodes, List<String> outputObjectList) {
        // ベースディレクトリパス取得
        final Path outputBaseDir = Optional.ofNullable(outputPath).filter(StringUtils::isNotBlank).map(Paths::get)
                .orElse(Paths.get(OUTPUT_BASE_DIRECTORY));
        // 出力対象とするPostgreSQL固有オブジェクト種別（トリガー/関数/シーケンス/型）
        final Set<OutputObjectType> outputObjectTypes = OutputObjectType.parse(outputObjectList);

        // 基本情報・テーブル一覧（1テーブル1行の軽量情報）のみ先に取得する
        final BaseInfoEntity baseInfoEntity = repository.selectBaseInfo();
        final List<TableEntity> tableEntityList = repository.selectTableList(targetSchemaList, targetTableList);
        // 外部キーはテーブル数ではなく制約数に比例する軽量な情報のため、チャンク化せず対象範囲全体を一括取得する。
        // ER図で「他チャンク・他スキーマのテーブルから自テーブルが参照されている」関係も正しく解決するために、
        // 特定のチャンクに限定せず全件を保持しておく必要がある
        final ForeignKeys foreignKeys = ForeignKeys.of(repository.selectForeignKeyList(targetSchemaList, targetTableList));
        // トリガーはテーブルに属する軽量な情報のため、外部キーと同様にチャンク化せず対象範囲全体を一括取得し、
        // テーブル定義書内のセクションとトリガー一覧の両方で利用する。
        // outputObjectListでトリガーが対象外とされた場合は、取得自体を行わず一覧・テーブル定義書双方から除外する
        final List<TriggerEntity> triggerEntityList = outputObjectTypes.contains(OutputObjectType.TRIGGER)
                ? repository.selectTriggerList(targetSchemaList, targetTableList)
                : List.of();
        final Triggers triggers = Triggers.of(triggerEntityList);
        // スキーマレベルのオブジェクト（関数/シーケンス/型）はテーブルフィルタの対象外。スキーマフィルタのみ適用する。
        // 関数一覧は定義本体を含まない軽量情報のみ先に取得する（定義本体はスキーマ単位で別途取得する）。
        // outputObjectListで対象外とされた種別は取得自体を行わない（一覧・個別定義とも出力されなくなる）
        final List<FunctionEntity> functionList = outputObjectTypes.contains(OutputObjectType.FUNCTION)
                ? repository.selectFunctionList(targetSchemaList)
                : List.of();
        final List<SequenceEntity> sequenceList = outputObjectTypes.contains(OutputObjectType.SEQUENCE)
                ? repository.selectSequenceList(targetSchemaList)
                : List.of();
        final List<TypeEntity> typeList = outputObjectTypes.contains(OutputObjectType.TYPE)
                ? repository.selectTypeList(targetSchemaList)
                : List.of();

        // テーブル一覧の関連ドキュメント導線（存在するカテゴリのみ）
        final Map<String, String> relatedDocuments = buildRelatedDocuments(tableEntityList, triggerEntityList,
                functionList, sequenceList, typeList);

        // テーブル一覧出力 -> ./output/ or {設定ファイルのFileParh}/tableList_{DB名}.md
        writer.writeTableDefinitionList(tableEntityList, baseInfoEntity, outputBaseDir, relatedDocuments);

        // スキーマ別ER図と、その索引の出力。テーブル一覧と外部キー一覧のみで生成できるため、
        // テーブル詳細をチャンク単位で取得する前のこの時点で出力できる
        erDiagramWriter.writeErDiagram(tableEntityList, foreignKeys, baseInfoEntity, outputBaseDir, erDiagramMaxNodes);

        // トリガー・関数・シーケンス・型の一覧出力（対象が存在しない場合は出力されない）
        objectListWriter.writeTriggerList(triggerEntityList, baseInfoEntity, outputBaseDir);
        objectListWriter.writeFunctionList(functionList, baseInfoEntity, outputBaseDir);
        objectListWriter.writeSequenceList(sequenceList, baseInfoEntity, outputBaseDir);
        objectListWriter.writeTypeList(typeList, baseInfoEntity, outputBaseDir);

        // シーケンス・型の個別ファイル出力（情報が小さいため一覧取得結果をそのまま利用する）
        sequenceList
                .forEach(sequence -> objectListWriter.writeSequenceDefinition(sequence, baseInfoEntity, outputBaseDir));
        typeList.forEach(type -> objectListWriter.writeTypeDefinition(type, baseInfoEntity, outputBaseDir));

        // 関数・プロシージャの個別ファイル出力。定義本体が大きくなり得るため、スキーマ単位で本体を取得・出力・破棄する
        functionList.stream().map(FunctionEntity::schemaName).distinct()
                .forEach(schemaName -> exportSchemaFunctionDefinitions(schemaName, baseInfoEntity, outputBaseDir));

        // テーブル定義出力 -> ./output/ or {設定ファイルのFileParh}/{DB名}/{スキーマ名}/{TBL分類}/{物理テーブル名}.md
        // カラム・インデックス・制約は、スキーマ内でさらにchunkSize件ずつに分割して取得・出力・破棄する。
        // これにより、テーブルが1スキーマに集中していても、同時にメモリ保持する詳細情報を最大chunkSize件分に抑える
        final Map<String, List<TableEntity>> tablesBySchema = tableEntityList.stream()
                .collect(Collectors.groupingBy(TableEntity::schemaName, LinkedHashMap::new, Collectors.toList()));
        tablesBySchema.forEach((schemaName, tablesInSchema) -> exportSchemaTableDefinitions(schemaName, tablesInSchema,
                targetSchemaList, targetTableList, baseInfoEntity, foreignKeys, triggers, outputBaseDir, chunkSize));
    }

    /**
     * テーブル一覧に掲載する関連ドキュメント（各オブジェクト一覧へのリンク）を組み立てるメソッド<br>
     * 対象が1件以上存在するカテゴリのみをリンク対象とする
     *
     * @param tables    テーブル情報のリスト
     * @param triggers  トリガー情報のリスト
     * @param functions 関数・プロシージャ情報のリスト
     * @param sequences シーケンス情報のリスト
     * @param types     ユーザー定義型情報のリスト
     * @return リンク表示名をキー、一覧ファイル名の接頭辞を値とするマップ（挿入順を保持する）
     */
    private Map<String, String> buildRelatedDocuments(List<TableEntity> tables, List<TriggerEntity> triggers,
            List<FunctionEntity> functions, List<SequenceEntity> sequences, List<TypeEntity> types) {
        final Map<String, String> relatedDocuments = new LinkedHashMap<>();
        if (!tables.isEmpty()) {
            relatedDocuments.put("ER図一覧", "erDiagram");
        }
        if (!functions.isEmpty()) {
            relatedDocuments.put("関数・プロシージャ一覧", "function");
        }
        if (!sequences.isEmpty()) {
            relatedDocuments.put("シーケンス一覧", "sequence");
        }
        if (!types.isEmpty()) {
            relatedDocuments.put("ユーザー定義型一覧", "type");
        }
        if (!triggers.isEmpty()) {
            relatedDocuments.put("トリガー一覧", "trigger");
        }
        return relatedDocuments;
    }

    /**
     * 指定スキーマに属する関数・プロシージャの定義本体を取得し、個別ファイルとして出力するメソッド<br>
     * 定義本体はスキーマ単位で取得・出力・破棄することで、同時にメモリ保持する定義本体を抑える
     *
     * @param schemaName    出力対象のスキーマ名
     * @param baseInfo      データベースの基本情報
     * @param outputBaseDir 出力先のベースディレクトリパス
     */
    private void exportSchemaFunctionDefinitions(String schemaName, BaseInfoEntity baseInfo, Path outputBaseDir) {
        repository.selectFunctionDefList(List.of(schemaName))
                .forEach(function -> objectListWriter.writeFunctionDefinition(function, baseInfo, outputBaseDir));
    }

    /**
     * 指定スキーマに属するテーブルの定義書を、chunkSize件ずつに分割して出力するメソッド
     *
     * @param schemaName       出力対象のスキーマ名
     * @param tablesInSchema   当該スキーマに属するテーブルのリスト
     * @param targetSchemaList テーブル定義出力対象のスキーマのリスト（書き込み要否判定に利用）
     * @param targetTableList  テーブル定義出力対象のテーブルのリスト（書き込み要否判定に利用）
     * @param baseInfoEntity   データベースの基本情報
     * @param foreignKeys      対象範囲全体の外部キー情報
     * @param triggers         対象範囲全体のトリガー情報
     * @param outputBaseDir    出力先のベースディレクトリパス
     * @param chunkSize        1回の取得でまとめて処理するテーブル数の上限。0以下の場合は分割しない
     */
    private void exportSchemaTableDefinitions(String schemaName, List<TableEntity> tablesInSchema,
            List<String> targetSchemaList, List<String> targetTableList, BaseInfoEntity baseInfoEntity,
            ForeignKeys foreignKeys, Triggers triggers, Path outputBaseDir, int chunkSize) {
        final int total = tablesInSchema.size();
        // chunkSizeが0以下の場合はスキーマ全体を1チャンクとして扱う
        final int step = chunkSize > 0 ? chunkSize : total;
        for (int from = 0; from < total; from += step) {
            final int to = Math.min(from + step, total);
            exportTableDefinitionChunk(schemaName, tablesInSchema.subList(from, to), targetSchemaList, targetTableList,
                    baseInfoEntity, foreignKeys, triggers, outputBaseDir);
        }
    }

    /**
     * 1チャンク分のテーブルの定義書を出力するメソッド<br>
     * 当該チャンクのテーブルに紐づく詳細情報（カラム・インデックス・制約）のみを取得し、
     * 出力後にローカル変数のスコープを抜けることでメモリ解放対象とする
     *
     * @param schemaName       出力対象のスキーマ名
     * @param chunk            1チャンク分のテーブルのリスト
     * @param targetSchemaList テーブル定義出力対象のスキーマのリスト（書き込み要否判定に利用）
     * @param targetTableList  テーブル定義出力対象のテーブルのリスト（書き込み要否判定に利用）
     * @param baseInfoEntity   データベースの基本情報
     * @param foreignKeys      対象範囲全体の外部キー情報
     * @param triggers         対象範囲全体のトリガー情報
     * @param outputBaseDir    出力先のベースディレクトリパス
     */
    private void exportTableDefinitionChunk(String schemaName, List<TableEntity> chunk, List<String> targetSchemaList,
            List<String> targetTableList, BaseInfoEntity baseInfoEntity, ForeignKeys foreignKeys, Triggers triggers,
            Path outputBaseDir) {
        final List<String> schemaList = List.of(schemaName);
        // 当該チャンクのテーブル名のみを条件に詳細情報を取得する
        final List<String> chunkTableList = chunk.stream().map(TableEntity::physicalTableName).distinct().toList();
        final Columns columns = Columns.of(repository.selectColumnList(schemaList, chunkTableList));
        final Indexes indexes = Indexes.of(repository.selectIndexList(schemaList, chunkTableList));
        final Constraints constraints = Constraints.of(repository.selectConstraintList(schemaList, chunkTableList));

        chunk.stream()
                .filter(tableEntity -> tableEntity.needsWriteTableDefinition(targetSchemaList, targetTableList))
                .map(tableEntity -> TableDefinitionContent.assemble(baseInfoEntity, tableEntity, columns, indexes,
                        constraints, foreignKeys, triggers, outputBaseDir))
                .forEach(writer::writeTableDefinition);
    }
}
