package com.export_table_definition.application.impl;

import com.export_table_definition.application.TargetSelection;
import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.relation.ForeignKeys;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.model.sidecar.Annotations;
import com.export_table_definition.domain.model.sidecar.Sidecar;
import com.export_table_definition.domain.model.table.TableDetail;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.Tables;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.model.table.Triggers;
import com.export_table_definition.domain.model.target.ConsistencyFinding;
import com.export_table_definition.domain.model.target.ExportTargets;
import com.export_table_definition.domain.model.target.OutputObjectType;
import com.export_table_definition.domain.model.target.TableDefinitionContent;
import com.export_table_definition.domain.model.target.TableTargetScope;
import com.export_table_definition.domain.repository.SidecarRepository;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.domain.service.export.ExportSink;
import com.export_table_definition.domain.service.target.ExportTargetConsistencyDomainService;
import com.export_table_definition.domain.service.target.ExportTargetConsistencyDomainService.ResolvedForeignKeys;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * DBからスキーマ情報を取得し、出力形式ごとの{@link ExportSink}へ書き出す段取りを担うクラス<br>
 * テーブル定義出力（通常実行）と差分検知（{@code --check}）の双方で共通の処理で、両者の違いは書き出し先の{@link ExportSink}のみ。
 * 対象範囲全体を一度にメモリへ載せないよう、軽量な情報は一括取得し（{@link #fetchTargets}）、
 * テーブル数に比例して重くなる情報はスキーマ・チャンク単位で取得・書き出し・破棄する（{@link #export}）。 取得した情報同士の突き合わせは{@link
 * ExportTargetConsistencyDomainService}に委ねる
 */
final class SchemaExporter {

  private static final Logger logger = LogManager.getLogger(SchemaExporter.class);
  private final TableDefinitionRepository repository;
  private final SidecarRepository sidecarRepository;
  private final ExportTargetConsistencyDomainService consistencyDomainService;
  private final Clock clock;

  /**
   * @param clock ドキュメントの生成日を決める時計
   */
  @Inject
  SchemaExporter(
      TableDefinitionRepository repository,
      SidecarRepository sidecarRepository,
      ExportTargetConsistencyDomainService consistencyDomainService,
      Clock clock) {
    this.repository = repository;
    this.sidecarRepository = sidecarRepository;
    this.consistencyDomainService = consistencyDomainService;
    this.clock = clock;
  }

  /**
   * 出力対象のうち、一括取得する軽量な情報（基本情報・テーブル一覧・外部キー・トリガー・関数/シーケンス/型の一覧・ 手動付帯情報）を取得するメソッド<br>
   * テーブル数に比例して重くなる詳細情報（カラム・インデックス・制約）と関数の定義本体は、 出力時（{@link #export}）にスキーマ・チャンク単位で取得する
   */
  ExportTargets fetchTargets(TargetSelection targetSelection) {
    // スキーマ・テーブルの絞り込み条件（入口で1回だけ組み立て済み。テーブルごとにワイルドカードパターンを解析し直さない）
    final TableTargetScope targetScope = targetSelection.targetScope();
    final List<String> targetSchemaList = targetScope.schemaNames();
    final boolean isFiltered = targetScope.isFiltered();
    final Set<OutputObjectType> outputObjectTypes = targetSelection.outputObjectTypes();
    // サイドカーYAML（手動付帯情報・論理リレーション・観点）を読み込む。未設定・ファイル不存在の場合は空となりマージは行われない
    final Sidecar sidecar = sidecarRepository.load(targetSelection.sidecarPath());
    final Annotations annotations = sidecar.annotations();

    // 基本情報・テーブル一覧（1テーブル1行の軽量情報）のみ先に取得する。
    // targetTableListにはワイルドカード（*）・除外（!）・スキーマ修飾（schema.table）を指定できるため、
    // SQLの完全一致IN句では絞り込めない。スキーマのみSQLで絞り込み、テーブル単位の絞り込みは
    // TableTargetScopeによりJava側で行う。
    // ここで絞り込んでおくことで、以降のテーブル一覧・ER図・詳細情報取得はすべて対象テーブルのみを扱う
    final BaseInfoEntity baseInfoEntity =
        BaseInfoEntity.of(repository.selectDatabase(), LocalDate.now(clock));
    final Tables tables =
        Tables.of(
            repository.selectTableList(targetSchemaList).stream()
                .filter(targetScope::matches)
                .toList());
    // 実在しないテーブルに対する付帯情報（リネーム・削除の可能性）を検出して警告する
    report(consistencyDomainService.findOrphanTableAnnotations(annotations, tables, isFiltered));
    // どのテーブルにも一致しない観点の所属テーブルのパターン（リネーム・削除の可能性）を検出して警告する
    report(
        consistencyDomainService.findUnmatchedViewpointPatterns(
            sidecar.viewpoints(), tables, isFiltered));
    // 外部キーはテーブル数ではなく制約数に比例する軽量な情報のため、チャンク化せず対象範囲全体を一括取得する。
    // ER図で「他チャンク・他スキーマのテーブルから自テーブルが参照されている」関係も正しく解決するために、
    // 特定のチャンクに限定せず全件を保持しておく必要がある。selectForeignKeyListはスキーマ単位でのみ絞り込み、
    // テーブル単位の絞り込みは行わないため、参照元・参照先の一方でもtargetTableListの絞り込みで除外された関係は、
    // テーブル一覧・ER図の双方から一貫して除外されるよう、出力対象のテーブルに含まれるものだけへ絞り込む。
    // サイドカー由来の論理リレーションも、出力対象に含まれるテーブル同士のものだけを同じ集合へ合流させる
    final ResolvedForeignKeys resolvedForeignKeys =
        consistencyDomainService.resolveForeignKeys(
            repository.selectForeignKeyList(targetSchemaList),
            sidecar.logicalRelations(),
            tables,
            isFiltered);
    report(resolvedForeignKeys.findings());
    final ForeignKeys foreignKeys = resolvedForeignKeys.foreignKeys();
    // トリガーはテーブルに属する軽量な情報のため、外部キーと同様にチャンク化せず対象範囲全体を一括取得し、
    // テーブル定義書内のセクションとトリガー一覧の両方で利用する。
    // outputObjectListでトリガーが対象外とされた場合は、取得自体を行わず一覧・テーブル定義書双方から除外する
    final List<TriggerEntity> triggerEntityList =
        outputObjectTypes.contains(OutputObjectType.TRIGGER)
            ? repository.selectTriggerList(targetSchemaList)
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
        tables,
        foreignKeys,
        triggerEntityList,
        functionList,
        sequenceList,
        typeList,
        annotations,
        sidecar.viewpoints());
  }

  /**
   * 一括取得した情報をもとに、指定された出力形式で書き出すメソッド<br>
   * 一括取得した情報から出力できるもの（一覧・ER図等）を先に書き出し、その後に関数の定義本体をスキーマ単位で、
   * テーブルの詳細情報をスキーマ・チャンク単位で取得・書き出し・破棄する。どの形式で書き出す場合も取得処理は共通
   *
   * @param chunkSize 1回の取得でまとめて処理するテーブル数の上限。0以下の場合は分割しない
   */
  void export(ExportTargets targets, List<ExportSink> sinks, int chunkSize) {
    sinks.forEach(sink -> sink.writeOverview(targets));

    // 関数・プロシージャの個別出力。定義本体が大きくなり得るため、スキーマ単位で本体を取得・出力・破棄する
    targets.functions().stream()
        .map(FunctionEntity::schemaName)
        .distinct()
        .forEach(schemaName -> exportSchemaFunctionDefinitions(schemaName, targets, sinks));

    // カラム・インデックス・制約は、スキーマ内でさらにchunkSize件ずつに分割して取得・出力・破棄する。
    // これにより、テーブルが1スキーマに集中していても、同時にメモリ保持する詳細情報を最大chunkSize件分に抑える
    final Triggers triggers = Triggers.of(targets.triggers());
    targets
        .tables()
        .bySchema()
        .forEach(
            (schemaName, tablesInSchema) ->
                exportSchemaTableDefinitions(
                    schemaName, tablesInSchema, targets, triggers, chunkSize, sinks));
  }

  /** 定義本体はスキーマ単位で取得・出力・破棄することで、同時にメモリ保持する定義本体を抑える */
  private void exportSchemaFunctionDefinitions(
      String schemaName, ExportTargets targets, List<ExportSink> sinks) {
    final List<FunctionEntity> functions = repository.selectFunctionDefList(List.of(schemaName));
    sinks.forEach(sink -> sink.writeFunctionDefinitions(schemaName, functions, targets.baseInfo()));
  }

  /**
   * @param tablesInSchema 当該スキーマに属するテーブルのリスト（呼び出し元で絞り込み済み）
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
      exportTableDefinitionChunk(tablesInSchema.subList(from, to), targets, triggers, sinks);
    }
  }

  /**
   * 当該チャンクのテーブルの詳細情報（カラム・インデックス・制約）のみを取得し、 出力後にローカル変数のスコープを抜けることでメモリ解放対象とする
   *
   * @param chunk 1チャンク分のテーブルのリスト（同一スキーマ。呼び出し元で絞り込み済み）
   */
  private void exportTableDefinitionChunk(
      List<TableEntity> chunk, ExportTargets targets, Triggers triggers, List<ExportSink> sinks) {
    for (final TableDetail detail : repository.selectTableDetails(chunk)) {
      report(consistencyDomainService.findOrphanColumnAnnotations(detail, targets.annotations()));
      final TableDefinitionContent content =
          TableDefinitionContent.assemble(
              targets.baseInfo(),
              detail,
              targets.foreignKeys(),
              triggers,
              targets.annotations(),
              targets.viewpoints());
      sinks.forEach(sink -> sink.writeTableDefinition(content));
    }
  }

  private static void report(List<ConsistencyFinding> findings) {
    findings.forEach(
        finding -> {
          switch (finding.severity()) {
            case INFO -> logger.info(finding.message());
            case WARN -> logger.warn(finding.message());
          }
        });
  }
}
