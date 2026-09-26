package com.export_table_definition.application.impl;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.application.TargetSelection;
import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.domain.model.ExportTargets;
import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.Sidecar;
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
import com.export_table_definition.domain.model.value.TableTargetScope;
import com.export_table_definition.domain.repository.AnnotationRepository;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.domain.service.export.ExportSink;
import com.export_table_definition.domain.service.export.MarkdownExportSinkFactory;
import com.export_table_definition.domain.service.export.SnapshotExportSinkFactory;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.snapshot.SnapshotDiffDomainService;
import com.export_table_definition.domain.service.target.ExportTargetConsistencyDomainService;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * テーブル定義出力に関するユースケースクラス<br>
 * DBからの取得（一括取得・スキーマ単位・チャンク単位）の段取りを担い、取得した情報の書き出しは出力形式ごとの {@link ExportSink}に、取得した情報同士の突き合わせは{@link
 * ExportTargetConsistencyDomainService}に委ねる
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinitionUsecaseImpl implements ExportTableDefinitionUsecase {

  private static final String CHECK_TEMP_DIR_PREFIX = "exportTableDefinition-check-";
  private static final Logger logger = LogManager.getLogger(ExportTableDefinitionUsecaseImpl.class);
  private final TableDefinitionRepository repository;
  private final AnnotationRepository annotationRepository;
  private final ExportTargetConsistencyDomainService consistencyDomainService;
  private final MarkdownExportSinkFactory markdownSinkFactory;
  private final SnapshotExportSinkFactory snapshotSinkFactory;
  private final SnapshotDiffDomainService snapshotDiffDomainService;
  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;

  /**
   * コンストラクタ
   *
   * @param repository テーブル定義出力に関するリポジトリクラス
   * @param annotationRepository 手動付帯情報（サイドカーYAML）の読み込みを行うリポジトリクラス
   * @param consistencyDomainService 出力対象のテーブルと外部キー・サイドカーの突き合わせを行うドメインサービス
   * @param markdownSinkFactory Markdownのドキュメントを書き出すExportSinkの生成クラス
   * @param snapshotSinkFactory スキーマのスナップショットを書き出すExportSinkの生成クラス
   * @param snapshotDiffDomainService 生成したスナップショットとコミット済みスナップショットの比較を行うドメインサービス
   * @param fileRepository 出力先ディレクトリ・差分比較用の一時ディレクトリの作成・削除に用いるファイルリポジトリ
   * @param outputPathResolver 出力先パス解決クラス
   */
  @Inject
  public ExportTableDefinitionUsecaseImpl(
      TableDefinitionRepository repository,
      AnnotationRepository annotationRepository,
      ExportTargetConsistencyDomainService consistencyDomainService,
      MarkdownExportSinkFactory markdownSinkFactory,
      SnapshotExportSinkFactory snapshotSinkFactory,
      SnapshotDiffDomainService snapshotDiffDomainService,
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver) {
    this.repository = repository;
    this.annotationRepository = annotationRepository;
    this.consistencyDomainService = consistencyDomainService;
    this.markdownSinkFactory = markdownSinkFactory;
    this.snapshotSinkFactory = snapshotSinkFactory;
    this.snapshotDiffDomainService = snapshotDiffDomainService;
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
  }

  /** {@inheritDoc} */
  @Override
  public void exportTableDefinition(ExportRequest request) {
    // ベースディレクトリパス取得
    final Path outputBaseDir = outputPathResolver.resolveBaseOutputDir(request.outputPath());
    if (request.rmDist()) {
      removeOutputBaseDir(outputBaseDir);
    }
    export(
        fetchTargets(request.targetSelection()),
        List.of(
            markdownSinkFactory.create(outputBaseDir, request.erDiagramMaxNodes()),
            snapshotSinkFactory.create(outputBaseDir)),
        request.chunkSize());
  }

  /** {@inheritDoc} */
  @Override
  public DiffResult checkDocumentDiff(CheckDiffRequest request) {
    final Path committedDir = outputPathResolver.resolveBaseOutputDir(request.outputPath());
    final Path generatedDir = fileRepository.createTempDirectory(CHECK_TEMP_DIR_PREFIX);
    try {
      final ExportTargets targets = fetchTargets(request.targetSelection());
      // DBからの取得は通常実行と同じだが、差分の判定に不要なMarkdownの描画・ER図の生成は行わず、
      // スナップショットのみを生成してオブジェクト単位で比較する
      export(targets, List.of(snapshotSinkFactory.create(generatedDir)), request.chunkSize());
      return snapshotDiffDomainService.compare(
          outputPathResolver.resolveSnapshotDirectory(generatedDir),
          outputPathResolver.resolveSnapshotDirectory(committedDir));
    } finally {
      fileRepository.deleteDirectory(generatedDir);
    }
  }

  /**
   * 出力対象のうち、一括取得する軽量な情報（基本情報・テーブル一覧・外部キー・トリガー・関数/シーケンス/型の一覧・ 手動付帯情報）を取得するメソッド<br>
   * テーブル数に比例して重くなる詳細情報（カラム・インデックス・制約）と関数の定義本体は、 出力時（{@link #export}）にスキーマ・チャンク単位で取得する
   *
   * @param targetSelection 出力対象の絞り込み条件（スキーマ・テーブル・outputObjects・annotationPath）
   * @return 一括取得した出力対象の情報
   */
  private ExportTargets fetchTargets(TargetSelection targetSelection) {
    final List<String> targetSchemaList = targetSelection.targetSchemaList();
    final List<String> targetTableList = targetSelection.targetTableList();
    // 出力対象とするPostgreSQL固有オブジェクト種別（トリガー/関数/シーケンス/型）
    final Set<OutputObjectType> outputObjectTypes =
        OutputObjectType.parse(targetSelection.outputObjectList());
    // サイドカーYAML（手動付帯情報・論理リレーション）を読み込む。未設定・ファイル不存在の場合は空となりマージは行われない
    final Sidecar sidecar = annotationRepository.load(targetSelection.annotationPath());
    final Annotations annotations = sidecar.annotations();
    // スキーマ・テーブルの絞り込み条件を1回だけ組み立てる（テーブルごとにワイルドカードパターンを解析し直さない）
    final TableTargetScope targetScope = TableTargetScope.of(targetSchemaList, targetTableList);
    final boolean isFiltered = targetScope.isFiltered();

    // 基本情報・テーブル一覧（1テーブル1行の軽量情報）のみ先に取得する。
    // targetTableListにはワイルドカード（*）・除外（!）・スキーマ修飾（schema.table）を指定できるため、
    // SQLの完全一致IN句では絞り込めない。スキーマのみSQLで絞り込み、テーブル単位の絞り込みは
    // TableTargetScopeによりJava側で行う。
    // ここで絞り込んでおくことで、以降のテーブル一覧・ER図・詳細情報取得はすべて対象テーブルのみを扱う
    final BaseInfoEntity baseInfoEntity = repository.selectBaseInfo();
    final List<TableEntity> tableEntityList =
        repository.selectTableList(targetSchemaList, List.of()).stream()
            .filter(targetScope::matches)
            .toList();
    // 実在しないテーブルに対する付帯情報（リネーム・削除の可能性）を検出して警告する
    consistencyDomainService.warnOrphanTableAnnotations(annotations, tableEntityList, isFiltered);
    // 外部キーはテーブル数ではなく制約数に比例する軽量な情報のため、チャンク化せず対象範囲全体を一括取得する。
    // ER図で「他チャンク・他スキーマのテーブルから自テーブルが参照されている」関係も正しく解決するために、
    // 特定のチャンクに限定せず全件を保持しておく必要がある。テーブル名は上記の理由によりSQLで絞り込まず、
    // スキーマ全体を取得する（tableListによる絞り込みが利く分、schemaのみ指定時よりDB負荷が増え得る）。
    // その代わり、参照元・参照先の一方でもtableListの絞り込みで除外された関係は、テーブル一覧・ER図の
    // 双方から一貫して除外されるよう、出力対象のテーブルに含まれるものだけへ絞り込む。
    // サイドカー由来の論理リレーションも、出力対象に含まれるテーブル同士のものだけを同じ集合へ合流させる
    final ForeignKeys foreignKeys =
        consistencyDomainService.resolveForeignKeys(
            repository.selectForeignKeyList(targetSchemaList, List.of()),
            sidecar.logicalRelations(),
            tableEntityList,
            isFiltered);
    // トリガーはテーブルに属する軽量な情報のため、外部キーと同様にチャンク化せず対象範囲全体を一括取得し、
    // テーブル定義書内のセクションとトリガー一覧の両方で利用する。
    // outputObjectListでトリガーが対象外とされた場合は、取得自体を行わず一覧・テーブル定義書双方から除外する
    final List<TriggerEntity> triggerEntityList =
        outputObjectTypes.contains(OutputObjectType.TRIGGER)
            ? repository.selectTriggerList(targetSchemaList, List.of())
            : List.of();
    // スキーマレベルのオブジェクト（関数/シーケンス/型）はテーブルフィルタの対象外。スキーマフィルタのみ適用する。
    // 関数一覧は定義本体を含まない軽量情報のみ先に取得する（定義本体はスキーマ単位で別途取得する）。
    // outputObjectListで対象外とされた種別は取得自体を行わない（一覧・個別定義とも出力されなくなる）
    final List<FunctionEntity> functionList =
        outputObjectTypes.contains(OutputObjectType.FUNCTION)
            ? repository.selectFunctionList(targetSchemaList)
            : List.of();
    final List<SequenceEntity> sequenceList =
        outputObjectTypes.contains(OutputObjectType.SEQUENCE)
            ? repository.selectSequenceList(targetSchemaList)
            : List.of();
    final List<TypeEntity> typeList =
        outputObjectTypes.contains(OutputObjectType.TYPE)
            ? repository.selectTypeList(targetSchemaList)
            : List.of();
    return new ExportTargets(
        baseInfoEntity,
        tableEntityList,
        foreignKeys,
        triggerEntityList,
        functionList,
        sequenceList,
        typeList,
        annotations);
  }

  /**
   * 一括取得した情報をもとに、指定された出力形式で書き出すメソッド<br>
   * 一括取得した情報から出力できるもの（一覧・ER図等）を先に書き出し、その後に関数の定義本体をスキーマ単位で、
   * テーブルの詳細情報をスキーマ・チャンク単位で取得・書き出し・破棄する。どの形式で書き出す場合も取得処理は共通
   *
   * @param targets 一括取得した出力対象の情報
   * @param sinks 書き出し先の出力形式
   * @param chunkSize 1回の取得でまとめて処理するテーブル数の上限。0以下の場合は分割しない
   */
  private void export(ExportTargets targets, List<ExportSink> sinks, int chunkSize) {
    sinks.forEach(sink -> sink.writeOverview(targets));

    // 関数・プロシージャの個別出力。定義本体が大きくなり得るため、スキーマ単位で本体を取得・出力・破棄する
    targets.functions().stream()
        .map(FunctionEntity::schemaName)
        .distinct()
        .forEach(schemaName -> exportSchemaFunctionDefinitions(schemaName, targets, sinks));

    // カラム・インデックス・制約は、スキーマ内でさらにchunkSize件ずつに分割して取得・出力・破棄する。
    // これにより、テーブルが1スキーマに集中していても、同時にメモリ保持する詳細情報を最大chunkSize件分に抑える
    final Triggers triggers = Triggers.of(targets.triggers());
    final Map<String, List<TableEntity>> tablesBySchema =
        targets.tables().stream()
            .collect(
                Collectors.groupingBy(
                    TableEntity::schemaName, LinkedHashMap::new, Collectors.toList()));
    tablesBySchema.forEach(
        (schemaName, tablesInSchema) ->
            exportSchemaTableDefinitions(
                schemaName, tablesInSchema, targets, triggers, chunkSize, sinks));
  }

  /**
   * {@code --rm-dist}指定時に、出力先ベースディレクトリを事前に削除するメソッド<br>
   * 削除されたテーブル等の残骸ファイルを残さないため、書き込み前にディレクトリごと削除する。 ルート・ホームディレクトリ・カレントディレクトリ自体など、設定誤りによる被害が甚大な
   * パスを解決した場合は削除を拒否する
   *
   * @param outputBaseDir 出力先ベースディレクトリ
   */
  private void removeOutputBaseDir(Path outputBaseDir) {
    final Path absolute = outputBaseDir.toAbsolutePath().normalize();
    if (!outputPathResolver.isRemovableOutputDir(outputBaseDir)) {
      throw new IllegalStateException(
          "Refusing to run --rm-dist because outputPath resolves to an unsafe directory. "
              + "[outputBaseDir="
              + absolute
              + "]");
    }
    logger.info("Removing existing output directory before export. [outputBaseDir={}]", absolute);
    fileRepository.deleteDirectory(outputBaseDir);
  }

  /**
   * 指定スキーマに属する関数・プロシージャの定義本体を取得し、書き出すメソッド<br>
   * 定義本体はスキーマ単位で取得・出力・破棄することで、同時にメモリ保持する定義本体を抑える
   *
   * @param schemaName 出力対象のスキーマ名
   * @param targets 一括取得した出力対象の情報（基本情報を参照する）
   * @param sinks 書き出し先の出力形式
   */
  private void exportSchemaFunctionDefinitions(
      String schemaName, ExportTargets targets, List<ExportSink> sinks) {
    final List<FunctionEntity> functions = repository.selectFunctionDefList(List.of(schemaName));
    sinks.forEach(sink -> sink.writeFunctionDefinitions(schemaName, functions, targets.baseInfo()));
  }

  /**
   * 指定スキーマに属するテーブルの定義書を、chunkSize件ずつに分割して書き出すメソッド
   *
   * @param schemaName 出力対象のスキーマ名
   * @param tablesInSchema 当該スキーマに属するテーブルのリスト（呼び出し元で絞り込み済み）
   * @param targets 一括取得した出力対象の情報（基本情報・外部キー・手動付帯情報を参照する）
   * @param triggers 対象範囲全体のトリガー情報
   * @param chunkSize 1回の取得でまとめて処理するテーブル数の上限。0以下の場合は分割しない
   * @param sinks 書き出し先の出力形式
   */
  private void exportSchemaTableDefinitions(
      String schemaName,
      List<TableEntity> tablesInSchema,
      ExportTargets targets,
      Triggers triggers,
      int chunkSize,
      List<ExportSink> sinks) {
    sinks.forEach(sink -> sink.beginSchemaTables(schemaName, targets.baseInfo()));
    final int total = tablesInSchema.size();
    // chunkSizeが0以下の場合はスキーマ全体を1チャンクとして扱う
    final int step = chunkSize > 0 ? chunkSize : total;
    for (int from = 0; from < total; from += step) {
      final int to = Math.min(from + step, total);
      exportTableDefinitionChunk(
          schemaName, tablesInSchema.subList(from, to), targets, triggers, sinks);
    }
  }

  /**
   * 1チャンク分のテーブルの定義書を書き出すメソッド<br>
   * 当該チャンクのテーブルに紐づく詳細情報（カラム・インデックス・制約）のみを取得し、 出力後にローカル変数のスコープを抜けることでメモリ解放対象とする
   *
   * @param schemaName 出力対象のスキーマ名
   * @param chunk 1チャンク分のテーブルのリスト（呼び出し元で絞り込み済み）
   * @param targets 一括取得した出力対象の情報（基本情報・外部キー・手動付帯情報を参照する）
   * @param triggers 対象範囲全体のトリガー情報
   * @param sinks 書き出し先の出力形式
   */
  private void exportTableDefinitionChunk(
      String schemaName,
      List<TableEntity> chunk,
      ExportTargets targets,
      Triggers triggers,
      List<ExportSink> sinks) {
    final List<String> schemaList = List.of(schemaName);
    // 当該チャンクのテーブル名のみを条件に詳細情報を取得する
    final List<String> chunkTableList =
        chunk.stream().map(TableEntity::physicalTableName).distinct().toList();
    final Columns columns = Columns.of(repository.selectColumnList(schemaList, chunkTableList));
    final Indexes indexes = Indexes.of(repository.selectIndexList(schemaList, chunkTableList));
    final Constraints constraints =
        Constraints.of(repository.selectConstraintList(schemaList, chunkTableList));

    chunk.forEach(
        tableEntity -> {
          consistencyDomainService.warnOrphanColumnAnnotations(
              tableEntity, columns, targets.annotations());
          final TableDefinitionContent content =
              TableDefinitionContent.assemble(
                  targets.baseInfo(),
                  tableEntity,
                  columns,
                  indexes,
                  constraints,
                  targets.foreignKeys(),
                  triggers,
                  targets.annotations());
          sinks.forEach(sink -> sink.writeTableDefinition(content));
        });
  }
}
