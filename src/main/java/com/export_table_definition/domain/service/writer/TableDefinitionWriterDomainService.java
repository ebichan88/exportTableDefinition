package com.export_table_definition.domain.service.writer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputPathResolver;
import com.export_table_definition.domain.service.writer.template.ErDiagramTemplates;
import com.export_table_definition.domain.service.writer.template.ObjectDefinitionTemplates;
import com.export_table_definition.domain.service.writer.template.PagedSectionTemplates;
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
     * スキーマ別ER図（全体ER図）の書き込み処理を行うメソッド<br>
     * 1つの図にすべてのテーブルを載せるとMermaidが描画できる規模を超えるため、スキーマ単位に分割して出力し、
     * それらへのリンクをまとめた索引ファイルを併せて出力する。
     * 利用する情報はテーブル一覧と外部キー一覧のみで、テーブル詳細を必要としない
     *
     * @param tables              テーブル情報リスト
     * @param foreignKeys         対象範囲全体の外部キー情報
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     * @param maxNodes            1つの図に描画するノード数の上限。0以下の場合は上限なし
     */
    public void writeErDiagram(List<TableEntity> tables, ForeignKeys foreignKeys, BaseInfoEntity baseInfo,
            Path outputDirectoryPath, int maxNodes) {
        if (tables.isEmpty()) {
            return;
        }
        final Map<String, List<TableEntity>> tablesBySchema = tables.stream()
                .collect(Collectors.groupingBy(TableEntity::schemaName, LinkedHashMap::new, Collectors.toList()));
        // 外部キーのスキーマ単位のグループ化は1度だけ行う。スキーマごとに全件を走査すると
        // 外部キー数×スキーマ数の走査となり、対象範囲が広い場合に処理時間が膨らむ
        final Map<String, List<ForeignKeyEntity>> foreignKeysBySchema = foreignKeys.groupBySchema();
        // ER図には他スキーマのテーブルも箱として登場するため、全テーブルを引けるマップを用意する
        final Map<TableKey, TableEntity> tableByKey = tables.stream()
                .collect(Collectors.toMap(TableKey::of, table -> table, (first, duplicate) -> first,
                        LinkedHashMap::new));
        tablesBySchema.forEach((schemaName, tablesInSchema) -> writeSchemaErDiagram(schemaName,
                foreignKeysBySchema.getOrDefault(schemaName, List.of()), tableByKey, baseInfo, outputDirectoryPath,
                maxNodes));
        writeErDiagramIndex(tablesBySchema, foreignKeys.crossSchema(), baseInfo, outputDirectoryPath);
    }

    /**
     * スキーマ1つ分のER図を書き込むメソッド
     *
     * @param schemaName          出力対象のスキーマ名
     * @param relatedForeignKeys  当該スキーマのテーブルが関与する外部キー（他スキーマとの関連を含む）のリスト
     * @param tableByKey          テーブルキーをキー、テーブル情報を値とするマップ
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     * @param maxNodes            1つの図に描画するノード数の上限。0以下の場合は上限なし
     */
    private void writeSchemaErDiagram(String schemaName, List<ForeignKeyEntity> relatedForeignKeys,
            Map<TableKey, TableEntity> tableByKey, BaseInfoEntity baseInfo, Path outputDirectoryPath, int maxNodes) {
        final List<TableKey> nodes = ErDiagramTemplates.diagramNodes(relatedForeignKeys);
        // ER図を描画した場合は図中の箱の一覧を、描画を省略した場合は代替として外部キーの一覧を掲載する
        final PagedSection<?> detail = ErDiagramTemplates.isOverflow(nodes.size(), maxNodes)
                ? new PagedSection<>(ErDiagramTemplates.foreignKeyHeading(),
                        ErDiagramTemplates.foreignKeyTableHeader(), relatedForeignKeys,
                        ErDiagramTemplates::foreignKeyTableLine)
                : new PagedSection<>(ErDiagramTemplates.diagramTableHeading(),
                        ErDiagramTemplates.diagramTableHeader(), nodes,
                        (no, key) -> ErDiagramTemplates.diagramTableLine(no, key, tableByKey.get(key)));
        final PageLayout layout = new PageLayout(ErDiagramTemplates.schemaFileHeader(schemaName, baseInfo),
                page -> outputPathResolver.resolveErDiagramFile(baseInfo, outputDirectoryPath, schemaName, page),
                page -> String.format("./erDiagram_%s_%s_%d.md", baseInfo.dbName(), schemaName, page),
                String.format("./erDiagram_%s_%s.md", baseInfo.dbName(), schemaName), "ER図へ");
        final List<String> contents = List.of(ErDiagramTemplates.schemaFileHeader(schemaName, baseInfo), // ヘッダー
                ErDiagramTemplates.baseInfo(baseInfo), // 基本情報
                ErDiagramTemplates.erDiagram(relatedForeignKeys, nodes, maxNodes), // ER図
                writePagedSection(detail, layout), // 掲載テーブル または 外部キー一覧
                ErDiagramTemplates.schemaFooter(baseInfo) // フッター
        );
        final Path filePath = outputPathResolver.resolveErDiagramFile(baseInfo, outputDirectoryPath, schemaName);
        fileRepository.writeFile(filePath, contents);
        logger.debug("exportErDiagram complete. [filePath={}]", filePath.toString());
    }

    /**
     * ページ分割対象となる表のセクション
     *
     * @param <T>         行の元になる要素の型
     * @param heading     セクションの見出し
     * @param tableHeader 表のヘッダー行
     * @param rows        行の元になる要素のリスト
     * @param lineMapper  行番号と要素から1行分の文字列を生成する関数
     */
    private record PagedSection<T>(String heading, String tableHeader, List<T> rows,
            BiFunction<Integer, T, String> lineMapper) {
    }

    /**
     * 分割ページの配置（ファイルパスとリンクの解決方法）
     *
     * @param fileHeader 分割ページのファイルヘッダー
     * @param pageFile   ページ番号から出力先パスを解決する関数
     * @param pageHref   ページ番号から相対パスを解決する関数
     * @param backHref   本体ページへの相対パス
     * @param backLabel  本体ページへのリンク表示名
     */
    private record PageLayout(String fileHeader, IntFunction<Path> pageFile, IntFunction<String> pageHref,
            String backHref, String backLabel) {
    }

    /**
     * 表のセクションを書き込むメソッド<br>
     * 行数がMarkdownの表に表示できる最大件数以下の場合は本体ページに直接埋め込み、
     * 超える場合はテーブル一覧と同様に別ファイルへ分割して、本体ページにはリンクのみを掲載する。
     * 行の文字列生成はページ単位で行い、全行分を同時にメモリ保持しない
     *
     * @param <T>     行の元になる要素の型
     * @param section 書き込む表のセクション
     * @param layout  分割ページの配置
     * @return 本体ページに掲載するセクション文字列
     */
    private <T> String writePagedSection(PagedSection<T> section, PageLayout layout) {
        final int total = section.rows().size();
        if (total == 0) {
            return "";
        }
        if (total <= MAX_TABLE_LIST_SIZE) {
            return PagedSectionTemplates.heading(section.heading()) + section.tableHeader()
                    + buildRows(section, 0, total) + System.lineSeparator();
        }
        final int totalPages = (total + MAX_TABLE_LIST_SIZE - 1) / MAX_TABLE_LIST_SIZE;
        for (int page = 1; page <= totalPages; page++) {
            final int from = (page - 1) * MAX_TABLE_LIST_SIZE;
            final int to = Math.min(from + MAX_TABLE_LIST_SIZE, total);
            final List<String> contents = List.of(layout.fileHeader(),
                    PagedSectionTemplates.heading(section.heading()), section.tableHeader(),
                    buildRows(section, from, to) + System.lineSeparator(),
                    PagedSectionTemplates.pageFooter(page > 1 ? layout.pageHref().apply(page - 1) : null,
                            page < totalPages ? layout.pageHref().apply(page + 1) : null, layout.backHref(),
                            layout.backLabel()));
            fileRepository.writeFile(layout.pageFile().apply(page), contents);
        }
        return PagedSectionTemplates.pagedSectionLinks(section.heading(), section.heading(),
                IntStream.rangeClosed(1, totalPages).mapToObj(layout.pageHref()::apply).toList());
    }

    /**
     * 表の行を指定範囲分だけ組み立てるメソッド
     *
     * @param <T>     行の元になる要素の型
     * @param section 対象の表のセクション
     * @param from    開始インデックス（含む）
     * @param to      終了インデックス（含まない）
     * @return 行を連結した文字列
     */
    private <T> String buildRows(PagedSection<T> section, int from, int to) {
        final StringBuilder sb = new StringBuilder();
        IntStream.range(from, to)
                .forEach(i -> sb.append(section.lineMapper().apply(i + 1, section.rows().get(i))));
        return sb.toString();
    }

    /**
     * ER図の索引ファイルを書き込むメソッド
     *
     * @param tablesBySchema      スキーマ名をキー、当該スキーマのテーブルのリストを値とするマップ
     * @param crossSchemaForeignKeys スキーマを跨ぐ外部キーのリスト
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    private void writeErDiagramIndex(Map<String, List<TableEntity>> tablesBySchema,
            List<ForeignKeyEntity> crossSchemaForeignKeys, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        final PagedSection<ForeignKeyEntity> crossSchemaSection = new PagedSection<>(
                ErDiagramTemplates.crossSchemaForeignKeyHeading(), ErDiagramTemplates.foreignKeyTableHeader(),
                crossSchemaForeignKeys, ErDiagramTemplates::foreignKeyTableLine);
        final PageLayout layout = new PageLayout(ErDiagramTemplates.fileHeader("ER図一覧", baseInfo),
                page -> outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, "erDiagram", page),
                page -> String.format("./erDiagramList_%s_%d.md", baseInfo.dbName(), page),
                String.format("./erDiagramList_%s.md", baseInfo.dbName()), "ER図一覧へ");
        final List<String> contents = List.of(ErDiagramTemplates.fileHeader("ER図一覧", baseInfo), // ヘッダー
                ErDiagramTemplates.baseInfo(baseInfo), // 基本情報
                ErDiagramTemplates.schemaIndex(baseInfo, tablesBySchema), // スキーマ別ER図へのリンク
                writePagedSection(crossSchemaSection, layout), // スキーマ跨ぎの外部キー
                ErDiagramTemplates.indexFooter(baseInfo) // フッター
        );
        fileRepository.writeFile(outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, "erDiagram"),
                contents);
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
        writeObjectList("トリガー一覧", "trigger", ObjectListTemplates.triggerTableHeader(), triggers,
                TriggerEntity::triggerListInfo, baseInfo, outputDirectoryPath);
    }

    /**
     * オブジェクト一覧（トリガー/関数/シーケンス/型）の書き込み処理を行う共通メソッド<br>
     * 対象が存在しない場合は何も出力しない。
     * 行数がMarkdownの表に表示できる最大件数を超える場合は、テーブル一覧と同様に別ファイルへ分割する
     *
     * @param <T>                 エンティティの型
     * @param title               一覧のタイトル
     * @param prefix              一覧ファイル名の接頭辞（例: trigger, function, sequence, type）
     * @param tableHeader         表のヘッダー行
     * @param objects             エンティティのリスト
     * @param listInfoGetter      エンティティから一覧行の文字列を取得する関数
     * @param baseInfo            データベースの基本情報
     * @param outputDirectoryPath 出力ディレクトリのパス
     */
    private <T> void writeObjectList(String title, String prefix, String tableHeader, List<T> objects,
            Function<T, String> listInfoGetter, BaseInfoEntity baseInfo, Path outputDirectoryPath) {
        if (objects.isEmpty()) {
            return;
        }
        final PagedSection<T> section = new PagedSection<>(title, tableHeader, objects,
                (no, object) -> ObjectListTemplates.listLine(listInfoGetter.apply(object)));
        final PageLayout layout = new PageLayout(ObjectListTemplates.fileHeader(title, baseInfo),
                page -> outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, prefix, page),
                page -> String.format("./%sList_%s_%d.md", prefix, baseInfo.dbName(), page),
                String.format("./%sList_%s.md", prefix, baseInfo.dbName()), title + "へ");
        final List<String> contents = List.of(ObjectListTemplates.fileHeader(title, baseInfo), // ヘッダー
                ObjectListTemplates.baseInfo(baseInfo), // 基本情報
                writePagedSection(section, layout), // 一覧
                ObjectListTemplates.footer(baseInfo) // フッター
        );
        fileRepository.writeFile(outputPathResolver.resolveObjectListFile(baseInfo, outputDirectoryPath, prefix),
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
        writeObjectList("関数・プロシージャ一覧", "function", ObjectListTemplates.functionTableHeader(), functions,
                FunctionEntity::functionListInfo, baseInfo, outputDirectoryPath);
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
        writeObjectList("シーケンス一覧", "sequence", ObjectListTemplates.sequenceTableHeader(), sequences,
                SequenceEntity::sequenceListInfo, baseInfo, outputDirectoryPath);
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
        writeObjectList("ユーザー定義型一覧", "type", ObjectListTemplates.typeTableHeader(), types,
                TypeEntity::typeListInfo, baseInfo, outputDirectoryPath);
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
