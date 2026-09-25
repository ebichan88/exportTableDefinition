package com.export_table_definition.application.impl;

import com.export_table_definition.application.ExportTableDefinitionUsecase;
import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.Sidecar;
import com.export_table_definition.domain.model.collection.Columns;
import com.export_table_definition.domain.model.collection.Constraints;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.collection.Indexes;
import com.export_table_definition.domain.model.collection.Triggers;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import com.export_table_definition.domain.model.type.ListDocumentType;
import com.export_table_definition.domain.model.type.OutputObjectType;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.domain.repository.AnnotationRepository;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.snapshot.SchemaSnapshotWriterDomainService;
import com.export_table_definition.domain.service.snapshot.SnapshotDiffDomainService;
import com.export_table_definition.domain.service.writer.ErDiagramWriterDomainService;
import com.export_table_definition.domain.service.writer.ObjectListWriterDomainService;
import com.export_table_definition.domain.service.writer.TableDefinitionWriterDomainService;
import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * テーブル定義出力に関するユースケースクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTableDefinitionUsecaseImpl implements ExportTableDefinitionUsecase {

  private static final String CHECK_TEMP_DIR_PREFIX = "exportTableDefinition-check-";
  private static final Logger logger = LogManager.getLogger(ExportTableDefinitionUsecaseImpl.class);
  private final TableDefinitionRepository repository;
  private final TableDefinitionWriterDomainService writer;
  private final ErDiagramWriterDomainService erDiagramWriter;
  private final ObjectListWriterDomainService objectListWriter;
  private final AnnotationRepository annotationRepository;
  private final SchemaSnapshotWriterDomainService snapshotWriter;
  private final SnapshotDiffDomainService snapshotDiffDomainService;
  private final FileRepository fileRepository;
  private final OutputPathResolver outputPathResolver;

  /**
   * コンストラクタ
   *
   * @param repository テーブル定義出力に関するリポジトリクラス
   * @param writer テーブル一覧・テーブル定義書を書き込むクラス
   * @param erDiagramWriter ER図を書き込むクラス
   * @param objectListWriter トリガー・関数・シーケンス・型の一覧および個別定義を書き込むクラス
   * @param snapshotWriter スキーマのスナップショットを書き込むクラス
   * @param annotationRepository 手動付帯情報（サイドカーYAML）の読み込みを行うリポジトリクラス
   * @param snapshotDiffDomainService 生成したスナップショットとコミット済みスナップショットの比較を行うドメインサービス
   * @param fileRepository 差分比較用の一時ディレクトリの作成・削除に用いるファイルリポジトリ
   * @param outputPathResolver 出力先パス解決クラス
   */
  @Inject
  public ExportTableDefinitionUsecaseImpl(
      TableDefinitionRepository repository,
      TableDefinitionWriterDomainService writer,
      ErDiagramWriterDomainService erDiagramWriter,
      ObjectListWriterDomainService objectListWriter,
      SchemaSnapshotWriterDomainService snapshotWriter,
      AnnotationRepository annotationRepository,
      SnapshotDiffDomainService snapshotDiffDomainService,
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver) {
    this.repository = repository;
    this.writer = writer;
    this.erDiagramWriter = erDiagramWriter;
    this.objectListWriter = objectListWriter;
    this.snapshotWriter = snapshotWriter;
    this.annotationRepository = annotationRepository;
    this.snapshotDiffDomainService = snapshotDiffDomainService;
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
  }

  /** {@inheritDoc} */
  @Override
  public void exportTableDefinition(
      List<String> targetSchemaList,
      List<String> targetTableList,
      String outputPath,
      int chunkSize,
      int erDiagramMaxNodes,
      List<String> outputObjectList,
      String annotationPath,
      boolean rmDist) {
    // ベースディレクトリパス取得
    final Path outputBaseDir = outputPathResolver.resolveBaseOutputDir(outputPath);
    if (rmDist) {
      removeOutputBaseDir(outputBaseDir);
    }
    export(
        fetchTargets(targetSchemaList, targetTableList, outputObjectList, annotationPath),
        outputBaseDir,
        chunkSize,
        erDiagramMaxNodes,
        EnumSet.of(OutputFormat.MARKDOWN, OutputFormat.SNAPSHOT));
  }

  /**
   * 出力対象のうち、一括取得する軽量な情報（基本情報・テーブル一覧・外部キー・トリガー・関数/シーケンス/型の一覧・ 手動付帯情報）を取得するメソッド<br>
   * テーブル数に比例して重くなる詳細情報（カラム・インデックス・制約）と関数の定義本体は、 出力時（{@link #export}）にスキーマ・チャンク単位で取得する
   *
   * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
   * @param targetTableList テーブル定義出力対象のテーブルのリスト
   * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名のリスト
   * @param annotationPath 手動付帯情報を記述したサイドカーYAMLのパス
   * @return 一括取得した出力対象の情報
   */
  private ExportTargets fetchTargets(
      List<String> targetSchemaList,
      List<String> targetTableList,
      List<String> outputObjectList,
      String annotationPath) {
    // 出力対象とするPostgreSQL固有オブジェクト種別（トリガー/関数/シーケンス/型）
    final Set<OutputObjectType> outputObjectTypes = OutputObjectType.parse(outputObjectList);
    // サイドカーYAML（手動付帯情報・論理リレーション）を読み込む。未設定・ファイル不存在の場合は空となりマージは行われない
    final Sidecar sidecar = annotationRepository.load(annotationPath);
    final Annotations annotations = sidecar.annotations();

    // 基本情報・テーブル一覧（1テーブル1行の軽量情報）のみ先に取得する。
    // targetTableListにはワイルドカード（*）・除外（!）・スキーマ修飾（schema.table）を指定できるため、
    // SQLの完全一致IN句では絞り込めない。スキーマのみSQLで絞り込み、テーブル単位の絞り込みは
    // TableEntity#needsWriteTableDefinitionによりJava側で行う（TableTargetFilter参照）。
    // ここで絞り込んでおくことで、以降のテーブル一覧・ER図・詳細情報取得はすべて対象テーブルのみを扱う
    final BaseInfoEntity baseInfoEntity = repository.selectBaseInfo();
    final List<TableEntity> tableEntityList =
        repository.selectTableList(targetSchemaList, List.of()).stream()
            .filter(table -> table.needsWriteTableDefinition(targetSchemaList, targetTableList))
            .toList();
    // 実在しないテーブルに対する付帯情報（リネーム・削除の可能性）を検出して警告する
    warnOrphanTableAnnotations(annotations, tableEntityList, targetSchemaList, targetTableList);
    // 外部キーはテーブル数ではなく制約数に比例する軽量な情報のため、チャンク化せず対象範囲全体を一括取得する。
    // ER図で「他チャンク・他スキーマのテーブルから自テーブルが参照されている」関係も正しく解決するために、
    // 特定のチャンクに限定せず全件を保持しておく必要がある。テーブル名は上記の理由によりSQLで絞り込まず、
    // スキーマ全体を取得する（tableListによる絞り込みが利く分、schemaのみ指定時よりDB負荷が増え得る）。
    // その代わり、参照元・参照先の一方でもtableListの絞り込みで除外された関係は、テーブル一覧・ER図の
    // 双方から一貫して除外されるよう、出力対象のテーブルに含まれるものだけへ絞り込む（resolvePhysicalForeignKeys）。
    // サイドカー由来の論理リレーションは、出力対象に含まれるテーブル同士のものだけを同じ集合へ合流させる。
    // 合流させることで、ER図のグループ分割（連結成分）やスキーマ跨ぎ関連の抽出にも自動的に反映される
    final List<ForeignKeyEntity> logicalRelations =
        resolveLogicalRelations(sidecar, tableEntityList);
    final List<ForeignKeyEntity> physicalForeignKeys =
        resolvePhysicalForeignKeys(
            repository.selectForeignKeyList(targetSchemaList, List.of()),
            tableEntityList,
            targetSchemaList,
            targetTableList);
    final ForeignKeys foreignKeys =
        ForeignKeys.of(
            Stream.concat(physicalForeignKeys.stream(), logicalRelations.stream()).toList());
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
   * 一括取得した情報をもとに、指定された形式で出力するメソッド<br>
   * 一括取得した情報から出力できるもの（一覧・ER図等）を先に出力し、その後に関数の定義本体をスキーマ単位で、
   * テーブルの詳細情報をスキーマ・チャンク単位で取得・出力・破棄する。どの形式で出力する場合も取得処理は共通
   *
   * @param targets 一括取得した出力対象の情報
   * @param outputBaseDir 出力先のベースディレクトリパス
   * @param chunkSize 1回の取得でまとめて処理するテーブル数の上限。0以下の場合は分割しない
   * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限（Markdownの出力にのみ用いる）
   * @param outputFormats 出力形式
   */
  private void export(
      ExportTargets targets,
      Path outputBaseDir,
      int chunkSize,
      int erDiagramMaxNodes,
      Set<OutputFormat> outputFormats) {
    if (outputFormats.contains(OutputFormat.MARKDOWN)) {
      writeMarkdownOverview(targets, outputBaseDir, erDiagramMaxNodes);
    }
    if (outputFormats.contains(OutputFormat.SNAPSHOT)) {
      writeSnapshotOverview(targets, outputBaseDir);
    }

    // 関数・プロシージャの個別出力。定義本体が大きくなり得るため、スキーマ単位で本体を取得・出力・破棄する
    targets.functions().stream()
        .map(FunctionEntity::schemaName)
        .distinct()
        .forEach(
            schemaName ->
                exportSchemaFunctionDefinitions(
                    schemaName, targets.baseInfo(), outputBaseDir, outputFormats));

    // テーブル定義出力 -> ./output/ or {設定ファイルのFileParh}/{DB名}/{スキーマ名}/{TBL分類}/{物理テーブル名}.md
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
                schemaName,
                tablesInSchema,
                targets,
                triggers,
                outputBaseDir,
                chunkSize,
                outputFormats));
  }

  /**
   * 一括取得した情報のみで出力できるMarkdown（テーブル一覧・ER図・各種一覧・シーケンス/型の個別定義）を出力するメソッド
   *
   * @param targets 一括取得した出力対象の情報
   * @param outputBaseDir 出力先のベースディレクトリパス
   * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限
   */
  private void writeMarkdownOverview(
      ExportTargets targets, Path outputBaseDir, int erDiagramMaxNodes) {
    final BaseInfoEntity baseInfoEntity = targets.baseInfo();
    // テーブル一覧の関連ドキュメント導線（存在するカテゴリのみ）
    final List<ListDocumentType> relatedDocuments =
        buildRelatedDocuments(
            targets.tables(),
            targets.triggers(),
            targets.functions(),
            targets.sequences(),
            targets.types());

    // テーブル一覧出力 -> ./output/ or {設定ファイルのFileParh}/tableList_{DB名}.md
    writer.writeTableDefinitionList(
        targets.tables(), baseInfoEntity, outputBaseDir, relatedDocuments);

    // スキーマ別ER図と、その索引の出力。テーブル一覧と外部キー一覧のみで生成できるため、
    // テーブル詳細をチャンク単位で取得する前のこの時点で出力できる
    erDiagramWriter.writeErDiagram(
        targets.tables(), targets.foreignKeys(), baseInfoEntity, outputBaseDir, erDiagramMaxNodes);

    // トリガー・関数・シーケンス・型の一覧出力（対象が存在しない場合は出力されない）
    objectListWriter.writeTriggerList(targets.triggers(), baseInfoEntity, outputBaseDir);
    objectListWriter.writeFunctionList(targets.functions(), baseInfoEntity, outputBaseDir);
    objectListWriter.writeSequenceList(targets.sequences(), baseInfoEntity, outputBaseDir);
    objectListWriter.writeTypeList(targets.types(), baseInfoEntity, outputBaseDir);

    // シーケンス・型の個別ファイル出力（情報が小さいため一覧取得結果をそのまま利用する）
    targets
        .sequences()
        .forEach(
            sequence ->
                objectListWriter.writeSequenceDefinition(sequence, baseInfoEntity, outputBaseDir));
    targets
        .types()
        .forEach(type -> objectListWriter.writeTypeDefinition(type, baseInfoEntity, outputBaseDir));
  }

  /**
   * 一括取得した情報のみで出力できるスナップショット（DB全体の情報・シーケンス・型）を出力するメソッド
   *
   * @param targets 一括取得した出力対象の情報
   * @param outputBaseDir 出力先のベースディレクトリパス
   */
  private void writeSnapshotOverview(ExportTargets targets, Path outputBaseDir) {
    snapshotWriter.writeDatabase(targets.baseInfo(), outputBaseDir);
    snapshotWriter.writeSequences(targets.sequences(), targets.baseInfo(), outputBaseDir);
    snapshotWriter.writeTypes(targets.types(), targets.baseInfo(), outputBaseDir);
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
    final Path cwd = Path.of("").toAbsolutePath().normalize();
    final Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    if (absolute.getParent() == null || absolute.equals(cwd) || absolute.equals(home)) {
      throw new IllegalStateException(
          "Refusing to run --rm-dist because outputPath resolves to an unsafe directory. "
              + "[outputBaseDir="
              + absolute
              + "]");
    }
    logger.info("Removing existing output directory before export. [outputBaseDir={}]", absolute);
    fileRepository.deleteDirectory(outputBaseDir);
  }

  /** {@inheritDoc} */
  @Override
  public DiffResult checkDocumentDiff(
      List<String> targetSchemaList,
      List<String> targetTableList,
      String outputPath,
      int chunkSize,
      int erDiagramMaxNodes,
      List<String> outputObjectList,
      String annotationPath) {
    final Path committedDir = outputPathResolver.resolveBaseOutputDir(outputPath);
    final Path generatedDir = fileRepository.createTempDirectory(CHECK_TEMP_DIR_PREFIX);
    try {
      final ExportTargets targets =
          fetchTargets(targetSchemaList, targetTableList, outputObjectList, annotationPath);
      // DBからの取得は通常実行と同じだが、差分の判定に不要なMarkdownの描画・ER図の生成は行わず、
      // スナップショットのみを生成してオブジェクト単位で比較する
      export(
          targets, generatedDir, chunkSize, erDiagramMaxNodes, EnumSet.of(OutputFormat.SNAPSHOT));
      return snapshotDiffDomainService.compare(
          outputPathResolver.resolveSnapshotDirectory(generatedDir),
          outputPathResolver.resolveSnapshotDirectory(committedDir));
    } finally {
      fileRepository.deleteDirectory(generatedDir);
    }
  }

  /**
   * 実在しないテーブルに対する付帯情報（＝孤児注釈）を検出して警告するメソッド<br>
   * リネームや削除により、サイドカーの付帯情報が現在のスキーマと乖離した場合の気付きとする。 スキーマ・テーブルの出力対象が絞り込まれている場合は、対象外テーブルの付帯情報を
   * 誤って孤児と判定しないよう検出をスキップする
   *
   * @param annotations 読み込んだ付帯情報
   * @param tables 出力対象のテーブル情報のリスト
   * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
   * @param targetTableList テーブル定義出力対象のテーブルのリスト
   */
  private void warnOrphanTableAnnotations(
      Annotations annotations,
      List<TableEntity> tables,
      List<String> targetSchemaList,
      List<String> targetTableList) {
    if (annotations.isEmpty()) {
      return;
    }
    if (!targetSchemaList.isEmpty() || !targetTableList.isEmpty()) {
      logger.info("Skipping orphan table annotation check because the output target is filtered.");
      return;
    }
    final Set<TableKey> existingKeys =
        tables.stream().map(TableKey::of).collect(Collectors.toSet());
    annotations.tableKeys().stream()
        .filter(key -> !existingKeys.contains(key))
        .forEach(
            key ->
                logger.warn(
                    "Annotation exists for a table that was not found (renamed or dropped?). [table={}.{}]",
                    key.schema(),
                    key.table()));
  }

  /**
   * DBに実在する外部キー制約のうち、参照元・参照先の双方が出力対象のテーブルであるものだけを抽出するメソッド<br>
   * 外部キーはスキーマ全体から取得しているため、{@code targetTableList}の絞り込みで除外されたテーブルへの
   * 参照が含まれうる。ここで除外しておかないと、テーブル一覧・個別の定義書には現れないテーブルが
   * ER図にだけ箱として残ってしまう。出力対象が絞り込まれていない場合のみ、警告ログで気付けるようにする（ 絞り込み時は意図した除外のため警告しない）
   *
   * @param foreignKeys スキーマ全体から取得した外部キー制約のリスト
   * @param tables 出力対象のテーブル情報のリスト
   * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
   * @param targetTableList テーブル定義出力対象のテーブルのリスト
   * @return 出力対象のテーブル同士の外部キー制約のリスト
   */
  private List<ForeignKeyEntity> resolvePhysicalForeignKeys(
      List<ForeignKeyEntity> foreignKeys,
      List<TableEntity> tables,
      List<String> targetSchemaList,
      List<String> targetTableList) {
    final Set<TableKey> existingKeys =
        tables.stream().map(TableKey::of).collect(Collectors.toSet());
    final boolean isFiltered = !targetSchemaList.isEmpty() || !targetTableList.isEmpty();
    return foreignKeys.stream()
        .filter(
            fk -> {
              if (isResolvable(fk, existingKeys)) {
                return true;
              }
              if (!isFiltered) {
                logger.warn(
                    "Skipping a foreign key because the referenced table was not found "
                        + "(renamed or dropped?). [foreignKey={}, table={}, referenceTable={}]",
                    fk.foreignkeyName(),
                    fk.getSchemaTableName(),
                    fk.getReferenceSchemaTableName());
              }
              return false;
            })
        .toList();
  }

  /**
   * サイドカー由来の論理リレーションのうち、出力対象のテーブル同士のものだけを抽出するメソッド<br>
   * 参照元・参照先の双方が出力対象に含まれていないと、ER図に片側だけのノードが現れたり、 定義書が存在しないテーブルへの関連が掲載されたりするため、いずれかが欠ける定義は除外する。
   * 除外の理由は、出力対象の絞り込みによるものか、リネーム・削除による乖離かを区別できないため、 一律で警告ログを出して気付けるようにする
   *
   * @param sidecar 読み込んだサイドカーの内容
   * @param tables 出力対象のテーブル情報のリスト
   * @return 出力対象のテーブル同士の論理リレーションのリスト
   */
  private List<ForeignKeyEntity> resolveLogicalRelations(
      Sidecar sidecar, List<TableEntity> tables) {
    if (sidecar.logicalRelations().isEmpty()) {
      return List.of();
    }
    final Set<TableKey> existingKeys =
        tables.stream().map(TableKey::of).collect(Collectors.toSet());
    final List<ForeignKeyEntity> resolved =
        sidecar.logicalRelations().stream()
            .filter(relation -> isResolvableRelation(relation, existingKeys))
            .toList();
    logger.info(
        "Merged logical relations declared in the sidecar. [relationCount={}]", resolved.size());
    return resolved;
  }

  /**
   * 論理リレーションの参照元・参照先が、いずれも出力対象のテーブルとして実在するか判定するメソッド<br>
   * 実在しない場合は、どちら側が解決できなかったかを警告ログに出力する
   *
   * @param relation 判定対象の論理リレーション
   * @param existingKeys 出力対象のテーブルキーの集合
   * @return 双方が実在する場合はtrue
   */
  private boolean isResolvableRelation(ForeignKeyEntity relation, Set<TableKey> existingKeys) {
    final boolean childExists = existingKeys.contains(relation.tableKey());
    final boolean parentExists = existingKeys.contains(relation.referenceTableKey());
    if (childExists && parentExists) {
      return true;
    }
    logger.warn(
        "Skipping logical relation because the table was not found in the output target "
            + "(filtered, renamed or dropped?). [relation={}, table={}{}, parentTable={}{}]",
        relation.foreignkeyName(),
        relation.getSchemaTableName(),
        childExists ? "" : " (not found)",
        relation.getReferenceSchemaTableName(),
        parentExists ? "" : " (not found)");
    return false;
  }

  /**
   * 外部キーの参照元・参照先が、いずれも出力対象のテーブルとして実在するか判定するメソッド（ログ出力なし）<br>
   * {@link #isResolvableRelation}と異なり、呼び出し元ごとに警告要否の判断が異なるため副作用を持たない
   *
   * @param relation 判定対象の外部キー
   * @param existingKeys 出力対象のテーブルキーの集合
   * @return 双方が実在する場合はtrue
   */
  private boolean isResolvable(ForeignKeyEntity relation, Set<TableKey> existingKeys) {
    return existingKeys.contains(relation.tableKey())
        && existingKeys.contains(relation.referenceTableKey());
  }

  /**
   * テーブル一覧に掲載する関連ドキュメント（各オブジェクト一覧へのリンク）を組み立てるメソッド<br>
   * 対象が1件以上存在するカテゴリのみをリンク対象とする
   *
   * @param tables テーブル情報のリスト
   * @param triggers トリガー情報のリスト
   * @param functions 関数・プロシージャ情報のリスト
   * @param sequences シーケンス情報のリスト
   * @param types ユーザー定義型情報のリスト
   * @return リンクを掲載する一覧の種別（掲載順）
   */
  private List<ListDocumentType> buildRelatedDocuments(
      List<TableEntity> tables,
      List<TriggerEntity> triggers,
      List<FunctionEntity> functions,
      List<SequenceEntity> sequences,
      List<TypeEntity> types) {
    final List<ListDocumentType> relatedDocuments = new ArrayList<>();
    if (!tables.isEmpty()) {
      relatedDocuments.add(ListDocumentType.ER_DIAGRAM);
    }
    if (!functions.isEmpty()) {
      relatedDocuments.add(ListDocumentType.FUNCTION);
    }
    if (!sequences.isEmpty()) {
      relatedDocuments.add(ListDocumentType.SEQUENCE);
    }
    if (!types.isEmpty()) {
      relatedDocuments.add(ListDocumentType.TYPE);
    }
    if (!triggers.isEmpty()) {
      relatedDocuments.add(ListDocumentType.TRIGGER);
    }
    return relatedDocuments;
  }

  /**
   * 指定スキーマに属する関数・プロシージャの定義本体を取得し、個別ファイルとして出力するメソッド<br>
   * 定義本体はスキーマ単位で取得・出力・破棄することで、同時にメモリ保持する定義本体を抑える
   *
   * @param schemaName 出力対象のスキーマ名
   * @param baseInfo データベースの基本情報
   * @param outputBaseDir 出力先のベースディレクトリパス
   * @param outputFormats 出力形式
   */
  private void exportSchemaFunctionDefinitions(
      String schemaName,
      BaseInfoEntity baseInfo,
      Path outputBaseDir,
      Set<OutputFormat> outputFormats) {
    final List<FunctionEntity> functions = repository.selectFunctionDefList(List.of(schemaName));
    if (outputFormats.contains(OutputFormat.MARKDOWN)) {
      functions.forEach(
          function -> objectListWriter.writeFunctionDefinition(function, baseInfo, outputBaseDir));
    }
    if (outputFormats.contains(OutputFormat.SNAPSHOT)) {
      snapshotWriter.writeFunctions(schemaName, functions, baseInfo, outputBaseDir);
    }
  }

  /**
   * 指定スキーマに属するテーブルの定義書を、chunkSize件ずつに分割して出力するメソッド
   *
   * @param schemaName 出力対象のスキーマ名
   * @param tablesInSchema 当該スキーマに属するテーブルのリスト（呼び出し元で絞り込み済み）
   * @param targets 一括取得した出力対象の情報（基本情報・外部キー・手動付帯情報を参照する）
   * @param triggers 対象範囲全体のトリガー情報
   * @param outputBaseDir 出力先のベースディレクトリパス
   * @param chunkSize 1回の取得でまとめて処理するテーブル数の上限。0以下の場合は分割しない
   * @param outputFormats 出力形式
   */
  private void exportSchemaTableDefinitions(
      String schemaName,
      List<TableEntity> tablesInSchema,
      ExportTargets targets,
      Triggers triggers,
      Path outputBaseDir,
      int chunkSize,
      Set<OutputFormat> outputFormats) {
    // スナップショットはスキーマ単位のファイルへ1テーブルずつ追記するため、先に追記先を空の状態で用意する
    if (outputFormats.contains(OutputFormat.SNAPSHOT)) {
      snapshotWriter.initTableFile(schemaName, targets.baseInfo(), outputBaseDir);
    }
    final int total = tablesInSchema.size();
    // chunkSizeが0以下の場合はスキーマ全体を1チャンクとして扱う
    final int step = chunkSize > 0 ? chunkSize : total;
    for (int from = 0; from < total; from += step) {
      final int to = Math.min(from + step, total);
      exportTableDefinitionChunk(
          schemaName,
          tablesInSchema.subList(from, to),
          targets,
          triggers,
          outputBaseDir,
          outputFormats);
    }
  }

  /**
   * 1チャンク分のテーブルの定義書を出力するメソッド<br>
   * 当該チャンクのテーブルに紐づく詳細情報（カラム・インデックス・制約）のみを取得し、 出力後にローカル変数のスコープを抜けることでメモリ解放対象とする
   *
   * @param schemaName 出力対象のスキーマ名
   * @param chunk 1チャンク分のテーブルのリスト（呼び出し元で絞り込み済み）
   * @param targets 一括取得した出力対象の情報（基本情報・外部キー・手動付帯情報を参照する）
   * @param triggers 対象範囲全体のトリガー情報
   * @param outputBaseDir 出力先のベースディレクトリパス
   * @param outputFormats 出力形式
   */
  private void exportTableDefinitionChunk(
      String schemaName,
      List<TableEntity> chunk,
      ExportTargets targets,
      Triggers triggers,
      Path outputBaseDir,
      Set<OutputFormat> outputFormats) {
    final List<String> schemaList = List.of(schemaName);
    // 当該チャンクのテーブル名のみを条件に詳細情報を取得する
    final List<String> chunkTableList =
        chunk.stream().map(TableEntity::physicalTableName).distinct().toList();
    final Columns columns = Columns.of(repository.selectColumnList(schemaList, chunkTableList));
    final Indexes indexes = Indexes.of(repository.selectIndexList(schemaList, chunkTableList));
    final Constraints constraints =
        Constraints.of(repository.selectConstraintList(schemaList, chunkTableList));

    chunk.stream()
        .peek(
            tableEntity -> warnOrphanColumnAnnotations(tableEntity, columns, targets.annotations()))
        .map(
            tableEntity ->
                TableDefinitionContent.assemble(
                    targets.baseInfo(),
                    tableEntity,
                    columns,
                    indexes,
                    constraints,
                    targets.foreignKeys(),
                    triggers,
                    targets.annotations(),
                    outputBaseDir))
        .forEach(
            content -> {
              if (outputFormats.contains(OutputFormat.MARKDOWN)) {
                writer.writeTableDefinition(content);
              }
              if (outputFormats.contains(OutputFormat.SNAPSHOT)) {
                snapshotWriter.appendTable(content);
              }
            });
  }

  /**
   * 実在しないカラムに対するカラム備考（＝孤児注釈）を検出して警告するメソッド<br>
   * 出力対象のテーブルに対してのみ、実在カラムと付帯情報のカラム名を突き合わせて検出する
   *
   * @param table 出力対象のテーブル情報
   * @param columns 当該チャンクのカラム情報
   * @param annotations 対象範囲全体の手動付帯情報
   */
  private void warnOrphanColumnAnnotations(
      TableEntity table, Columns columns, Annotations annotations) {
    final Set<String> actualColumnNames =
        columns.of(table).stream()
            .map(ColumnEntity::physicalColumnName)
            .collect(Collectors.toSet());
    annotations
        .of(table)
        .orphanColumnNames(actualColumnNames)
        .forEach(
            columnName ->
                logger.warn(
                    "Column annotation exists for a column that was not found (renamed or dropped?). "
                        + "[table={}, column={}]",
                    table.getSchemaTableName(),
                    columnName));
  }

  /** 取得したスキーマ情報の出力形式 */
  private enum OutputFormat {
    /** Markdownのドキュメント（テーブル定義書・ER図・各種一覧） */
    MARKDOWN,
    /** スキーマのスナップショット（JSON Lines） */
    SNAPSHOT
  }

  /**
   * 出力対象のうち、一括取得する軽量な情報の組<br>
   * テーブル数に比例して重くなる詳細情報（カラム・インデックス・制約）と関数の定義本体は含まない
   *
   * @param baseInfo データベースの基本情報
   * @param tables 出力対象のテーブル情報のリスト（テーブルの絞り込み済み）
   * @param foreignKeys 出力対象のテーブル同士の外部キー（論理リレーションを含む）
   * @param triggers 対象範囲全体のトリガー情報のリスト
   * @param functions 関数・プロシージャの一覧情報（定義本体を含まない）のリスト
   * @param sequences シーケンス情報のリスト
   * @param types ユーザー定義型情報のリスト
   * @param annotations 対象範囲全体の手動付帯情報
   */
  private record ExportTargets(
      BaseInfoEntity baseInfo,
      List<TableEntity> tables,
      ForeignKeys foreignKeys,
      List<TriggerEntity> triggers,
      List<FunctionEntity> functions,
      List<SequenceEntity> sequences,
      List<TypeEntity> types,
      Annotations annotations) {}
}
