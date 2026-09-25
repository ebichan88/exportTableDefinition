package com.export_table_definition.domain.service.writer;

import com.export_table_definition.domain.model.collection.ForeignKeyGroup;
import com.export_table_definition.domain.model.collection.ForeignKeyGroups;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PageLayout;
import com.export_table_definition.domain.service.writer.PagedSectionWriter.PagedSection;
import com.export_table_definition.domain.service.writer.template.ErDiagramTemplates;
import com.export_table_definition.domain.service.writer.template.PagedSectionTemplates;
import com.google.inject.Inject;
import java.nio.file.Path;
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
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   * @param maxNodes 1つの図に描画するノード数の上限。0以下の場合は上限なし
   */
  public void writeErDiagram(
      List<TableEntity> tables,
      ForeignKeys foreignKeys,
      BaseInfoEntity baseInfo,
      Path outputDirectoryPath,
      int maxNodes) {
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
                baseInfo,
                outputDirectoryPath,
                maxNodes));
    writeErDiagramIndex(tablesBySchema, foreignKeys.crossSchema(), baseInfo, outputDirectoryPath);
  }

  /**
   * スキーマ1つ分のER図を書き込むメソッド<br>
   * ノード数が上限を超える場合は、連結成分を1枚に収まる範囲でまとめ直したグループごとに分割して出力する
   *
   * @param schemaName 出力対象のスキーマ名
   * @param relatedForeignKeys 当該スキーマのテーブルが関与する外部キー（他スキーマとの関連を含む）のリスト
   * @param tableByKey テーブルキーをキー、テーブル情報を値とするマップ
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   * @param maxNodes 1つの図に描画するノード数の上限。0以下の場合は上限なし
   */
  private void writeSchemaErDiagram(
      String schemaName,
      List<ForeignKeyEntity> relatedForeignKeys,
      Map<TableKey, TableEntity> tableByKey,
      BaseInfoEntity baseInfo,
      Path outputDirectoryPath,
      int maxNodes) {
    final ForeignKeyGroup schemaGroup = ForeignKeyGroup.of(relatedForeignKeys);
    final List<ForeignKeyGroup> groups =
        schemaGroup.exceeds(maxNodes)
            ? ForeignKeyGroups.pack(
                ForeignKeyGroups.connectedComponents(relatedForeignKeys), maxNodes)
            : List.of();
    // グループが1つ以下の場合は分割しても1枚に収まらない単一の巨大なまとまりであり、
    // スキーマページと同じ内容のグループページができるだけなので分割しない
    if (groups.size() <= 1) {
      final PageLayout layout =
          new PageLayout(
              ErDiagramTemplates.schemaFileHeader(schemaName, baseInfo),
              page ->
                  outputPathResolver.resolveErDiagramFile(
                      baseInfo, outputDirectoryPath, schemaName, page),
              page -> String.format("./erDiagram_%s_%s_%d.md", baseInfo.dbName(), schemaName, page),
              String.format("./erDiagram_%s_%s.md", baseInfo.dbName(), schemaName),
              "ER図へ");
      writeErDiagramPage(
          outputPathResolver.resolveErDiagramFile(baseInfo, outputDirectoryPath, schemaName),
          layout,
          schemaGroup,
          ErDiagramTemplates.schemaFooter(baseInfo),
          tableByKey,
          maxNodes,
          baseInfo);
      return;
    }
    IntStream.rangeClosed(1, groups.size())
        .forEach(
            groupNo ->
                writeGroupErDiagram(
                    schemaName,
                    groupNo,
                    groups.get(groupNo - 1),
                    tableByKey,
                    baseInfo,
                    outputDirectoryPath,
                    maxNodes));
    writeSchemaGroupIndex(
        schemaName, groups, schemaGroup.nodeCount(), baseInfo, outputDirectoryPath, maxNodes);
  }

  /**
   * グループ1つ分のER図を書き込むメソッド
   *
   * @param schemaName 出力対象のスキーマ名
   * @param groupNo グループ番号（1始まり）
   * @param group 当該グループに属する外部キーのまとまり
   * @param tableByKey テーブルキーをキー、テーブル情報を値とするマップ
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   * @param maxNodes 1つの図に描画するノード数の上限
   */
  private void writeGroupErDiagram(
      String schemaName,
      int groupNo,
      ForeignKeyGroup group,
      Map<TableKey, TableEntity> tableByKey,
      BaseInfoEntity baseInfo,
      Path outputDirectoryPath,
      int maxNodes) {
    final PageLayout layout =
        new PageLayout(
            ErDiagramTemplates.groupFileHeader(schemaName, groupNo, baseInfo),
            page ->
                outputPathResolver.resolveErDiagramGroupFile(
                    baseInfo, outputDirectoryPath, schemaName, groupNo, page),
            page ->
                String.format(
                    "./erDiagram_%s_%s_group%d_%d.md",
                    baseInfo.dbName(), schemaName, groupNo, page),
            String.format("./erDiagram_%s_%s_group%d.md", baseInfo.dbName(), schemaName, groupNo),
            "ER図へ");
    writeErDiagramPage(
        outputPathResolver.resolveErDiagramGroupFile(
            baseInfo, outputDirectoryPath, schemaName, groupNo),
        layout,
        group,
        ErDiagramTemplates.groupFooter(schemaName, baseInfo),
        tableByKey,
        maxNodes,
        baseInfo);
  }

  /**
   * ER図のページを書き込む共通メソッド<br>
   * スキーマ全体のページとグループ別のページで本文の構成（図または代替の外部キー一覧）が同じため共通化する。
   * ER図を描画した場合は図中の箱の一覧を、描画を省略した場合は代替として外部キーの一覧を掲載する
   *
   * @param filePath 出力先のファイルパス
   * @param layout ファイルヘッダーと、一覧が長い場合の分割ページの配置
   * @param group 当該ページに描画する外部キーのまとまり
   * @param footer フッター
   * @param tableByKey テーブルキーをキー、テーブル情報を値とするマップ
   * @param maxNodes 1つの図に描画するノード数の上限
   * @param baseInfo データベースの基本情報
   */
  private void writeErDiagramPage(
      Path filePath,
      PageLayout layout,
      ForeignKeyGroup group,
      String footer,
      Map<TableKey, TableEntity> tableByKey,
      int maxNodes,
      BaseInfoEntity baseInfo) {
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
    final List<String> contents =
        List.of(
            layout.fileHeader(), // ヘッダー
            ErDiagramTemplates.baseInfo(baseInfo), // 基本情報
            ErDiagramTemplates.erDiagram(group, maxNodes), // ER図（描画結果または省略メッセージ）
            pagedSectionWriter.writePagedSection(detail, layout), // 掲載テーブル または 外部キー一覧
            footer // フッター
            );
    fileRepository.writeFile(filePath, contents);
    logger.debug("exportErDiagram complete. [filePath={}]", filePath.toString());
  }

  /**
   * ER図をグループに分割した場合の、スキーマページ（グループ索引）を書き込むメソッド
   *
   * @param schemaName 出力対象のスキーマ名
   * @param groups グループごとにまとめ直した外部キーのまとまりのリスト
   * @param nodeCount 当該スキーマの関連テーブル数
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   * @param maxNodes 1つの図に描画するノード数の上限
   */
  private void writeSchemaGroupIndex(
      String schemaName,
      List<ForeignKeyGroup> groups,
      int nodeCount,
      BaseInfoEntity baseInfo,
      Path outputDirectoryPath,
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
                      String.format(
                          "./erDiagram_%s_%s_group%d.md", baseInfo.dbName(), schemaName, groupNo)));
            });
    final List<String> contents =
        List.of(
            ErDiagramTemplates.schemaFileHeader(schemaName, baseInfo), // ヘッダー
            ErDiagramTemplates.baseInfo(baseInfo), // 基本情報
            ErDiagramTemplates.groupedMessage(nodeCount, maxNodes, groups.size()), // 分割の説明
            groupIndex.append(System.lineSeparator()).toString(), // グループ一覧
            ErDiagramTemplates.schemaFooter(baseInfo) // フッター
            );
    fileRepository.writeFile(
        outputPathResolver.resolveErDiagramFile(baseInfo, outputDirectoryPath, schemaName),
        contents);
  }

  /**
   * ER図の索引ファイルを書き込むメソッド
   *
   * @param tablesBySchema スキーマ名をキー、当該スキーマのテーブルのリストを値とするマップ
   * @param crossSchemaForeignKeys スキーマを跨ぐ外部キーのリスト
   * @param baseInfo データベースの基本情報
   * @param outputDirectoryPath 出力ディレクトリのパス
   */
  private void writeErDiagramIndex(
      Map<String, List<TableEntity>> tablesBySchema,
      List<ForeignKeyEntity> crossSchemaForeignKeys,
      BaseInfoEntity baseInfo,
      Path outputDirectoryPath) {
    final PagedSection<ForeignKeyEntity> crossSchemaSection =
        new PagedSection<>(
            ErDiagramTemplates.crossSchemaForeignKeyHeading(),
            ErDiagramTemplates.foreignKeyTableHeader(),
            crossSchemaForeignKeys,
            ErDiagramTemplates::foreignKeyTableLine);
    final PageLayout layout =
        new PageLayout(
            ErDiagramTemplates.fileHeader("ER図一覧", baseInfo),
            page ->
                outputPathResolver.resolveObjectListFile(
                    baseInfo, outputDirectoryPath, "erDiagram", page),
            page -> String.format("./erDiagramList_%s_%d.md", baseInfo.dbName(), page),
            String.format("./erDiagramList_%s.md", baseInfo.dbName()),
            "ER図一覧へ");
    final List<String> contents =
        List.of(
            ErDiagramTemplates.fileHeader("ER図一覧", baseInfo), // ヘッダー
            ErDiagramTemplates.baseInfo(baseInfo), // 基本情報
            ErDiagramTemplates.schemaIndex(baseInfo, tablesBySchema), // スキーマ別ER図へのリンク
            pagedSectionWriter.writePagedSection(crossSchemaSection, layout), // スキーマ跨ぎの外部キー
            ErDiagramTemplates.indexFooter(baseInfo) // フッター
            );
    fileRepository.writeFile(
        outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, "erDiagram"),
        contents);
  }
}
