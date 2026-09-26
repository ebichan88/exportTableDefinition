package com.export_table_definition.domain.service.writer;

import com.export_table_definition.domain.model.collection.ForeignKeyGroup;
import com.export_table_definition.domain.model.collection.ForeignKeyGroups;
import com.export_table_definition.domain.model.collection.ForeignKeyGroups.PageComposition;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.type.ListDocumentType;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.DocumentLocations;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.path.OutputRoot;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PageLayout;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PagedSection;
import com.export_table_definition.domain.service.writer.template.ErDiagramTemplates;
import com.export_table_definition.domain.service.writer.template.PagedSectionTemplates;
import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * スキーマ別ER図（全体ER図）とその索引を書き込むクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ErDiagramWriterDomainService {

  /** ER図の分割ページから本体ページへ戻るリンクの表示名 */
  private static final String ER_DIAGRAM_BACK_LABEL = "ER図へ";

  private static final Logger logger = LogManager.getLogger(ErDiagramWriterDomainService.class);
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
  public ErDiagramWriterDomainService(
      FileRepository fileRepository,
      OutputPathResolver outputPathResolver,
      PagedSectionWriter pagedSectionWriter) {
    this.fileRepository = fileRepository;
    this.outputPathResolver = outputPathResolver;
    this.pagedSectionWriter = pagedSectionWriter;
  }

  /**
   * スキーマ別ER図（全体ER図）の書き込み処理を行うメソッド<br>
   * 1つの図にすべてのテーブルを載せるとMermaidが描画できる規模を超えるため、スキーマ単位に分割して出力し、 それらへのリンクをまとめた索引ファイルを併せて出力する。
   * 利用する情報はテーブル一覧と外部キー一覧のみで、テーブル詳細を必要としない
   *
   * @param tables テーブル情報リスト
   * @param foreignKeys 対象範囲全体の外部キー情報
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   * @param maxNodes 1つの図に描画するノード数の上限。0以下の場合は上限なし
   */
  public void writeErDiagram(
      List<TableEntity> tables, ForeignKeys foreignKeys, OutputRoot outputRoot, int maxNodes) {
    if (tables.isEmpty()) {
      return;
    }
    final Map<String, List<TableEntity>> tablesBySchema =
        tables.stream()
            .collect(
                Collectors.groupingBy(
                    TableEntity::schemaName, LinkedHashMap::new, Collectors.toList()));
    // 外部キーのスキーマ単位のグループ化は1度だけ行う。スキーマごとに全件を走査すると
    // 外部キー数×スキーマ数の走査となり、対象範囲が広い場合に処理時間が膨らむ
    final Map<String, List<ForeignKeyEntity>> foreignKeysBySchema = foreignKeys.groupBySchema();
    // ER図には他スキーマのテーブルも箱として登場するため、全テーブルを引けるマップを用意する
    final Map<TableKey, TableEntity> tableByKey =
        tables.stream()
            .collect(
                Collectors.toMap(
                    TableKey::of, table -> table, (first, duplicate) -> first, LinkedHashMap::new));
    tablesBySchema.forEach(
        (schemaName, tablesInSchema) ->
            writeSchemaErDiagram(
                schemaName,
                foreignKeysBySchema.getOrDefault(schemaName, List.of()),
                tableByKey,
                outputRoot,
                maxNodes));
    writeErDiagramIndex(tablesBySchema, foreignKeys.crossSchema(), outputRoot);
  }

  /**
   * スキーマ1つ分のER図を書き込むメソッド<br>
   * ノード数が上限を超える場合は、連結成分を1枚に収まる範囲でまとめ直したグループごとに分割して出力する
   *
   * @param schemaName 出力対象のスキーマ名
   * @param relatedForeignKeys 当該スキーマのテーブルが関与する外部キー（他スキーマとの関連を含む）のリスト
   * @param tableByKey テーブルキーをキー、テーブル情報を値とするマップ
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   * @param maxNodes 1つの図に描画するノード数の上限。0以下の場合は上限なし
   */
  private void writeSchemaErDiagram(
      String schemaName,
      List<ForeignKeyEntity> relatedForeignKeys,
      Map<TableKey, TableEntity> tableByKey,
      OutputRoot outputRoot,
      int maxNodes) {
    final PageComposition composition = ForeignKeyGroups.compose(relatedForeignKeys, maxNodes);
    switch (composition) {
      case PageComposition.Single(ForeignKeyGroup group) -> {
        final PageLayout layout =
            new PageLayout(
                ErDiagramTemplates.schemaFileHeader(schemaName, outputRoot.baseInfo()),
                outputPathResolver.resolveErDiagramFile(outputRoot, schemaName),
                ER_DIAGRAM_BACK_LABEL);
        writeErDiagramPage(
            layout,
            group,
            ErDiagramTemplates.schemaFooter(outputRoot.baseInfo()),
            tableByKey,
            maxNodes,
            outputRoot);
      }
      case PageComposition.Grouped(List<ForeignKeyGroup> groups, int nodeCount) -> {
        IntStream.rangeClosed(1, groups.size())
            .forEach(
                groupNo ->
                    writeGroupErDiagram(
                        schemaName,
                        groupNo,
                        groups.get(groupNo - 1),
                        tableByKey,
                        outputRoot,
                        maxNodes));
        writeSchemaGroupIndex(schemaName, groups, nodeCount, outputRoot, maxNodes);
      }
    }
  }

  /**
   * グループ1つ分のER図を書き込むメソッド
   *
   * @param schemaName 出力対象のスキーマ名
   * @param groupNo グループ番号（1始まり）
   * @param group 当該グループに属する外部キーのまとまり
   * @param tableByKey テーブルキーをキー、テーブル情報を値とするマップ
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   * @param maxNodes 1つの図に描画するノード数の上限
   */
  private void writeGroupErDiagram(
      String schemaName,
      int groupNo,
      ForeignKeyGroup group,
      Map<TableKey, TableEntity> tableByKey,
      OutputRoot outputRoot,
      int maxNodes) {
    final PageLayout layout =
        new PageLayout(
            ErDiagramTemplates.groupFileHeader(schemaName, groupNo, outputRoot.baseInfo()),
            outputPathResolver.resolveErDiagramGroupFile(outputRoot, schemaName, groupNo),
            ER_DIAGRAM_BACK_LABEL);
    writeErDiagramPage(
        layout,
        group,
        ErDiagramTemplates.groupFooter(schemaName, outputRoot.baseInfo()),
        tableByKey,
        maxNodes,
        outputRoot);
  }

  /**
   * ER図のページを書き込む共通メソッド<br>
   * スキーマ全体のページとグループ別のページで本文の構成（図または代替の外部キー一覧）が同じため共通化する。
   * ER図を描画した場合は図中の箱の一覧を、描画を省略した場合は代替として外部キーの一覧を掲載する
   *
   * @param layout 出力先のファイルパスとファイルヘッダー（一覧が長い場合の分割ページと共通）
   * @param group 当該ページに描画する外部キーのまとまり
   * @param footer フッター
   * @param tableByKey テーブルキーをキー、テーブル情報を値とするマップ
   * @param maxNodes 1つの図に描画するノード数の上限
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  private void writeErDiagramPage(
      PageLayout layout,
      ForeignKeyGroup group,
      String footer,
      Map<TableKey, TableEntity> tableByKey,
      int maxNodes,
      OutputRoot outputRoot) {
    final PagedSection<?> detail =
        group.exceeds(maxNodes)
            ? new PagedSection<>(
                ErDiagramTemplates.foreignKeyHeading(),
                ErDiagramTemplates.foreignKeyTableHeader(),
                group.foreignKeys(),
                ErDiagramTemplates::foreignKeyTableLine)
            : new PagedSection<>(
                ErDiagramTemplates.diagramTableHeading(),
                ErDiagramTemplates.diagramTableHeader(),
                group.nodes(),
                (no, key) -> ErDiagramTemplates.diagramTableLine(no, key, tableByKey.get(key)));
    final String detailSection = pagedSectionWriter.writePagedSection(detail, layout);
    final List<String> contents =
        List.of(
            layout.fileHeader(), // ヘッダー
            ErDiagramTemplates.baseInfo(outputRoot.baseInfo()), // 基本情報
            ErDiagramTemplates.erDiagram(group, maxNodes), // ER図（描画結果または省略メッセージ）
            detailSection, // 掲載テーブル または 外部キー一覧
            footer // フッター
            );
    fileRepository.writeFile(layout.file(), contents);
    logger.debug("exportErDiagram complete. [filePath={}]", layout.file());
  }

  /**
   * ER図をグループに分割した場合の、スキーマページ（グループ索引）を書き込むメソッド
   *
   * @param schemaName 出力対象のスキーマ名
   * @param groups グループごとにまとめ直した外部キーのまとまりのリスト
   * @param nodeCount 当該スキーマの関連テーブル数
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   * @param maxNodes 1つの図に描画するノード数の上限
   */
  private void writeSchemaGroupIndex(
      String schemaName,
      List<ForeignKeyGroup> groups,
      int nodeCount,
      OutputRoot outputRoot,
      int maxNodes) {
    final StringBuilder groupIndex =
        new StringBuilder(PagedSectionTemplates.heading(ErDiagramTemplates.groupIndexHeading()))
            .append(ErDiagramTemplates.groupIndexHeader());
    IntStream.rangeClosed(1, groups.size())
        .forEach(
            groupNo -> {
              final ForeignKeyGroup group = groups.get(groupNo - 1);
              groupIndex.append(
                  ErDiagramTemplates.groupIndexLine(
                      groupNo,
                      group.nodeCount(),
                      group.foreignKeys().size(),
                      group.mainTable(),
                      DocumentLocations.linkFromBase(
                          DocumentLocations.erDiagramGroupFile(
                              outputRoot.baseInfo().dbName(), schemaName, groupNo))));
            });
    final List<String> contents =
        List.of(
            ErDiagramTemplates.schemaFileHeader(schemaName, outputRoot.baseInfo()), // ヘッダー
            ErDiagramTemplates.baseInfo(outputRoot.baseInfo()), // 基本情報
            ErDiagramTemplates.groupedMessage(nodeCount, maxNodes, groups.size()), // 分割の説明
            groupIndex.append(System.lineSeparator()).toString(), // グループ一覧
            ErDiagramTemplates.schemaFooter(outputRoot.baseInfo()) // フッター
            );
    fileRepository.writeFile(
        outputPathResolver.resolveErDiagramFile(outputRoot, schemaName), contents);
  }

  /**
   * ER図の索引ファイルを書き込むメソッド
   *
   * @param tablesBySchema スキーマ名をキー、当該スキーマのテーブルのリストを値とするマップ
   * @param crossSchemaForeignKeys スキーマを跨ぐ外部キーのリスト
   * @param outputRoot 出力先ベースディレクトリとデータベース基本情報
   */
  private void writeErDiagramIndex(
      Map<String, List<TableEntity>> tablesBySchema,
      List<ForeignKeyEntity> crossSchemaForeignKeys,
      OutputRoot outputRoot) {
    final PagedSection<ForeignKeyEntity> crossSchemaSection =
        new PagedSection<>(
            ErDiagramTemplates.crossSchemaForeignKeyHeading(),
            ErDiagramTemplates.foreignKeyTableHeader(),
            crossSchemaForeignKeys,
            ErDiagramTemplates::foreignKeyTableLine);
    final PageLayout layout =
        new PageLayout(
            ErDiagramTemplates.fileHeader(
                ListDocumentType.ER_DIAGRAM.getTitle(), outputRoot.baseInfo()),
            outputPathResolver.resolveListFile(outputRoot, ListDocumentType.ER_DIAGRAM),
            ListDocumentType.ER_DIAGRAM.getBackLinkLabel());
    final String crossSchemaDetail =
        pagedSectionWriter.writePagedSection(crossSchemaSection, layout);
    final List<String> contents =
        List.of(
            layout.fileHeader(), // ヘッダー
            ErDiagramTemplates.baseInfo(outputRoot.baseInfo()), // 基本情報
            ErDiagramTemplates.schemaIndex(outputRoot.baseInfo(), tablesBySchema), // スキーマ別ER図へのリンク
            crossSchemaDetail, // スキーマ跨ぎの外部キー
            ErDiagramTemplates.indexFooter(outputRoot.baseInfo()) // フッター
            );
    fileRepository.writeFile(layout.file(), contents);
  }
}
