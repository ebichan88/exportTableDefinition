package com.export_table_definition.application.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.domain.repository.AnnotationRepository;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.domain.service.writer.ErDiagramWriterDomainService;
import com.export_table_definition.domain.service.writer.ObjectListWriterDomainService;
import com.export_table_definition.domain.service.writer.PagedSectionWriter;
import com.export_table_definition.domain.service.writer.TableDefinitionWriterDomainService;
import com.export_table_definition.infrastructure.path.DefaultOutputPathResolver;

/**
 * ExportTableDefinitionUsecaseImpl のオーケストレーションに関するテスト<br>
 * 依存先の各WriterDomainServiceは実オブジェクトを使い、書き込み内容を{@link InMemoryFileRepository}に
 * 収集することで、リポジトリ呼び出し・チャンク分割・関連ドキュメント構築を含む一連の流れを検証する
 */
public class ExportTableDefinitionUsecaseImplTest {

    private static final Path DEFAULT_OUT = Paths.get("./output");

    /** 書き込み内容をメモリ上に収集するFileRepositoryのスタブ */
    private static class InMemoryFileRepository implements FileRepository {
        private final Map<Path, String> files = new LinkedHashMap<>();

        @Override
        public void writeFile(Path filePath, List<String> contents) {
            files.put(filePath, String.join("", contents));
        }

        @Override
        public void createDirectory(Path filePath) {
            // 何もしない
        }
    }

    /** リポジトリ呼び出し回数・引数を記録するスタブ */
    private static class RecordingRepository implements TableDefinitionRepository {
        private final BaseInfoEntity baseInfo;
        List<TableEntity> tables = new ArrayList<>();
        List<ColumnEntity> columns = new ArrayList<>();
        List<IndexEntity> indexes = new ArrayList<>();
        List<ConstraintEntity> constraints = new ArrayList<>();
        List<ForeignKeyEntity> foreignKeys = new ArrayList<>();
        List<TriggerEntity> triggers = new ArrayList<>();
        List<FunctionEntity> functions = new ArrayList<>();
        List<FunctionEntity> functionDefs = new ArrayList<>();
        List<SequenceEntity> sequences = new ArrayList<>();
        List<TypeEntity> types = new ArrayList<>();

        final List<List<String>> columnCallArgs = new ArrayList<>();
        final List<List<String>> indexCallArgs = new ArrayList<>();
        final List<List<String>> constraintCallArgs = new ArrayList<>();
        final List<List<String>> functionDefCallArgs = new ArrayList<>();
        int foreignKeyListCalls = 0;

        RecordingRepository(BaseInfoEntity baseInfo) {
            this.baseInfo = baseInfo;
        }

        @Override
        public BaseInfoEntity selectBaseInfo() {
            return baseInfo;
        }

        @Override
        public List<TableEntity> selectTableList(List<String> schemaList, List<String> tableList) {
            return List.copyOf(tables);
        }

        @Override
        public List<ColumnEntity> selectColumnList(List<String> schemaList, List<String> tableList) {
            columnCallArgs.add(tableList);
            return columns.stream().filter(c -> tableList.contains(c.tableName())).toList();
        }

        @Override
        public List<IndexEntity> selectIndexList(List<String> schemaList, List<String> tableList) {
            indexCallArgs.add(tableList);
            return indexes.stream().filter(i -> tableList.contains(i.tableName())).toList();
        }

        @Override
        public List<ConstraintEntity> selectConstraintList(List<String> schemaList, List<String> tableList) {
            constraintCallArgs.add(tableList);
            return constraints.stream().filter(c -> tableList.contains(c.tableName())).toList();
        }

        @Override
        public List<ForeignKeyEntity> selectForeignKeyList(List<String> schemaList, List<String> tableList) {
            foreignKeyListCalls++;
            return List.copyOf(foreignKeys);
        }

        @Override
        public List<TriggerEntity> selectTriggerList(List<String> schemaList, List<String> tableList) {
            return List.copyOf(triggers);
        }

        @Override
        public List<FunctionEntity> selectFunctionList(List<String> schemaList) {
            return List.copyOf(functions);
        }

        @Override
        public List<FunctionEntity> selectFunctionDefList(List<String> schemaList) {
            functionDefCallArgs.add(schemaList);
            return functionDefs.stream().filter(f -> schemaList.contains(f.schemaName())).toList();
        }

        @Override
        public List<SequenceEntity> selectSequenceList(List<String> schemaList) {
            return List.copyOf(sequences);
        }

        @Override
        public List<TypeEntity> selectTypeList(List<String> schemaList) {
            return List.copyOf(types);
        }
    }

    private InMemoryFileRepository fileRepository;
    private RecordingRepository repository;
    private ExportTableDefinitionUsecaseImpl usecase;
    /** annotationRepositoryスタブが返す付帯情報（テストごとに差し替え可能） */
    private Annotations annotations = Annotations.empty();
    /** annotationRepositoryへ渡されたパスを記録する */
    private String receivedAnnotationPath;

    private void setUp() {
        fileRepository = new InMemoryFileRepository();
        repository = new RecordingRepository(new BaseInfoEntity("testdb", "| pg | testdb | 2026-09-24 |"));
        final DefaultOutputPathResolver pathResolver = new DefaultOutputPathResolver();
        final PagedSectionWriter pagedSectionWriter = new PagedSectionWriter(fileRepository);
        final TableDefinitionWriterDomainService writer = new TableDefinitionWriterDomainService(fileRepository,
                pathResolver, pagedSectionWriter);
        final ErDiagramWriterDomainService erDiagramWriter = new ErDiagramWriterDomainService(fileRepository,
                pathResolver, pagedSectionWriter);
        final ObjectListWriterDomainService objectListWriter = new ObjectListWriterDomainService(fileRepository,
                pathResolver, pagedSectionWriter);
        final AnnotationRepository annotationRepository = path -> {
            receivedAnnotationPath = path;
            return annotations;
        };
        usecase = new ExportTableDefinitionUsecaseImpl(repository, writer, erDiagramWriter, objectListWriter,
                annotationRepository);
    }

    private TableEntity table(String schema, String physical) {
        return new TableEntity("testdb", schema, "", physical, "table",
                "|1|" + schema + "|" + physical + "|" + physical + "|table|[link](x)||",
                "|" + schema + "||" + physical + "|table|", "");
    }

    private String contentOf(Path path) {
        return fileRepository.files.entrySet().stream().filter(e -> e.getKey().equals(path)).map(Map.Entry::getValue)
                .findFirst().orElseThrow(() -> new AssertionError("生成されていないファイル: " + path));
    }

    private boolean fileExists(Path path) {
        return fileRepository.files.containsKey(path);
    }

    private Path tableDefFile(Path baseDir, String schema, String physical) {
        return baseDir.resolve("testdb").resolve(schema).resolve("table").resolve(physical + ".md");
    }

    @Test
    @DisplayName("全種別のオブジェクトが存在する場合、一覧・個別定義・ER図がすべて出力される")
    void testFullExportGeneratesAllExpectedFiles() {
        setUp();
        repository.tables.add(table("public", "t1"));
        repository.tables.add(table("public", "t2"));
        repository.columns.add(new ColumnEntity("public", "t1", "col1", "id", "int", "○"));
        repository.triggers.add(new TriggerEntity("public", "t1", "trg_list", "trg_info"));
        repository.functions.add(new FunctionEntity("testdb", "public", "f1", "f1", "f_list", ""));
        repository.functionDefs.add(new FunctionEntity("testdb", "public", "f1", "f1", "f_list", "BODY"));
        repository.sequences.add(new SequenceEntity("testdb", "public", "seq1", "seq_list", "seq_info"));
        repository.types.add(new TypeEntity("testdb", "public", "type1", "enum", "type_list", "def"));

        usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null);

        // テーブル一覧: 全カテゴリへの関連ドキュメントリンクを含む
        final Path tableListFile = DEFAULT_OUT.resolve("tableList_testdb.md");
        assertTrue(fileExists(tableListFile));
        final String tableListContent = contentOf(tableListFile);
        assertTrue(tableListContent.contains("ER図一覧"));
        assertTrue(tableListContent.contains("関数・プロシージャ一覧"));
        assertTrue(tableListContent.contains("シーケンス一覧"));
        assertTrue(tableListContent.contains("ユーザー定義型一覧"));
        assertTrue(tableListContent.contains("トリガー一覧"));

        // テーブル定義書が両テーブル分出力される
        assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "t1")));
        assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "t2")));

        // ER図（スキーマページ・索引）が出力される
        assertTrue(fileExists(DEFAULT_OUT.resolve("erDiagram_testdb_public.md")));
        assertTrue(fileExists(DEFAULT_OUT.resolve("erDiagramList_testdb.md")));

        // 一覧ファイル
        assertTrue(fileExists(DEFAULT_OUT.resolve("triggerList_testdb.md")));
        assertTrue(fileExists(DEFAULT_OUT.resolve("functionList_testdb.md")));
        assertTrue(fileExists(DEFAULT_OUT.resolve("sequenceList_testdb.md")));
        assertTrue(fileExists(DEFAULT_OUT.resolve("typeList_testdb.md")));

        // 個別定義ファイル
        assertTrue(fileExists(DEFAULT_OUT.resolve("testdb").resolve("public").resolve("function").resolve("f1.md")));
        assertTrue(fileExists(DEFAULT_OUT.resolve("testdb").resolve("public").resolve("sequence").resolve("seq1.md")));
        assertTrue(fileExists(DEFAULT_OUT.resolve("testdb").resolve("public").resolve("type").resolve("type1.md")));
    }

    @Test
    @DisplayName("outputObjectListで一部種別のみ指定した場合、指定外の種別は一覧・個別定義・テーブル定義書内セクションとも出力されない")
    void testOutputObjectListRestrictsToSpecifiedTypes() {
        setUp();
        repository.tables.add(table("public", "t1"));
        repository.columns.add(new ColumnEntity("public", "t1", "col1", "id", "int", "○"));
        repository.triggers.add(new TriggerEntity("public", "t1", "trg_list", "trg_info"));
        repository.functions.add(new FunctionEntity("testdb", "public", "f1", "f1", "f_list", ""));
        repository.functionDefs.add(new FunctionEntity("testdb", "public", "f1", "f1", "f_list", "BODY"));
        repository.sequences.add(new SequenceEntity("testdb", "public", "seq1", "seq_list", "seq_info"));
        repository.types.add(new TypeEntity("testdb", "public", "type1", "enum", "type_list", "def"));

        usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of("function"), null);

        // 指定したfunctionのみ出力される
        assertTrue(fileExists(DEFAULT_OUT.resolve("functionList_testdb.md")));
        assertTrue(fileExists(DEFAULT_OUT.resolve("testdb").resolve("public").resolve("function").resolve("f1.md")));

        // 指定外の種別は一覧・個別定義とも出力されない
        assertFalse(fileExists(DEFAULT_OUT.resolve("triggerList_testdb.md")));
        assertFalse(fileExists(DEFAULT_OUT.resolve("sequenceList_testdb.md")));
        assertFalse(fileExists(DEFAULT_OUT.resolve("typeList_testdb.md")));
        assertFalse(fileExists(DEFAULT_OUT.resolve("testdb").resolve("public").resolve("sequence").resolve("seq1.md")));
        assertFalse(fileExists(DEFAULT_OUT.resolve("testdb").resolve("public").resolve("type").resolve("type1.md")));

        // テーブル一覧の関連ドキュメントも指定外の種別は含まれない
        final String tableListContent = contentOf(DEFAULT_OUT.resolve("tableList_testdb.md"));
        assertTrue(tableListContent.contains("関数・プロシージャ一覧"));
        assertFalse(tableListContent.contains("シーケンス一覧"));
        assertFalse(tableListContent.contains("ユーザー定義型一覧"));
        assertFalse(tableListContent.contains("トリガー一覧"));

        // トリガーが対象外の場合、テーブル定義書内の「トリガー情報」セクションにも出力されない
        final String t1Content = contentOf(tableDefFile(DEFAULT_OUT, "public", "t1"));
        assertFalse(t1Content.contains("trg_info"));
    }

    @Test
    @DisplayName("関連ドキュメントは存在するカテゴリのみリンクされ、対象が空の一覧は出力されない")
    void testBuildRelatedDocumentsOnlyIncludesExistingCategories() {
        setUp();
        repository.tables.add(table("public", "t1"));
        // トリガー・関数・シーケンス・型はすべて0件

        usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null);

        final String tableListContent = contentOf(DEFAULT_OUT.resolve("tableList_testdb.md"));
        assertTrue(tableListContent.contains("ER図一覧"));
        assertFalse(tableListContent.contains("関数・プロシージャ一覧"));
        assertFalse(tableListContent.contains("シーケンス一覧"));
        assertFalse(tableListContent.contains("ユーザー定義型一覧"));
        assertFalse(tableListContent.contains("トリガー一覧"));

        // 対象が空のオブジェクト一覧はファイル自体が出力されない
        assertFalse(fileExists(DEFAULT_OUT.resolve("triggerList_testdb.md")));
        assertFalse(fileExists(DEFAULT_OUT.resolve("functionList_testdb.md")));
        assertFalse(fileExists(DEFAULT_OUT.resolve("sequenceList_testdb.md")));
        assertFalse(fileExists(DEFAULT_OUT.resolve("typeList_testdb.md")));
    }

    @Test
    @DisplayName("chunkSize指定時は、指定件数ごとにテーブル詳細を分割取得しつつ全テーブル分を出力する")
    void testChunkSizeSplitsRepositoryCallsPerChunk() {
        setUp();
        IntStream.rangeClosed(1, 5).forEach(i -> repository.tables.add(table("public", "t" + i)));

        usecase.exportTableDefinition(List.of(), List.of(), null, 2, 80, List.of(), null);

        // 5件を2件ずつ取得: 3回に分割される
        assertEquals(3, repository.columnCallArgs.size());
        assertEquals(3, repository.indexCallArgs.size());
        assertEquals(3, repository.constraintCallArgs.size());
        assertEquals(List.of(2, 2, 1), repository.columnCallArgs.stream().map(List::size).toList());

        // チャンク境界を跨いでも全テーブル分の定義書が出力される
        IntStream.rangeClosed(1, 5)
                .forEach(i -> assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "t" + i)), "t" + i + "が欠落"));

        // 外部キー・トリガーはチャンク化せず、対象範囲全体を1回だけ取得する
        assertEquals(1, repository.foreignKeyListCalls);
    }

    @Test
    @DisplayName("chunkSizeが0以下の場合はスキーマ全体を1回で取得する")
    void testChunkSizeZeroMeansSingleChunk() {
        setUp();
        IntStream.rangeClosed(1, 5).forEach(i -> repository.tables.add(table("public", "t" + i)));

        usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null);

        assertEquals(1, repository.columnCallArgs.size());
        assertEquals(5, repository.columnCallArgs.get(0).size());
    }

    @Test
    @DisplayName("関数定義本体はテーブル数・関数数ではなくスキーマ数の回数だけ取得される")
    void testFunctionDefinitionsFetchedPerSchemaNotPerFunction() {
        setUp();
        repository.tables.add(table("public", "t1"));
        repository.functions.add(new FunctionEntity("testdb", "s1", "f1", "f1", "list", ""));
        repository.functions.add(new FunctionEntity("testdb", "s1", "f2", "f2", "list", ""));
        repository.functions.add(new FunctionEntity("testdb", "s2", "f3", "f3", "list", ""));
        repository.functionDefs.add(new FunctionEntity("testdb", "s1", "f1", "f1", "list", "BODY1"));
        repository.functionDefs.add(new FunctionEntity("testdb", "s1", "f2", "f2", "list", "BODY2"));
        repository.functionDefs.add(new FunctionEntity("testdb", "s2", "f3", "f3", "list", "BODY3"));

        usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null);

        // 関数は3件だが、スキーマは2件のためselectFunctionDefListは2回のみ呼ばれる
        assertEquals(2, repository.functionDefCallArgs.size());
        assertTrue(repository.functionDefCallArgs.stream().allMatch(args -> args.size() == 1));

        // 3件分の個別ファイルはすべて出力される
        assertTrue(fileExists(DEFAULT_OUT.resolve("testdb").resolve("s1").resolve("function").resolve("f1.md")));
        assertTrue(fileExists(DEFAULT_OUT.resolve("testdb").resolve("s1").resolve("function").resolve("f2.md")));
        assertTrue(fileExists(DEFAULT_OUT.resolve("testdb").resolve("s2").resolve("function").resolve("f3.md")));
    }

    @Test
    @DisplayName("targetTableListで対象外となったテーブルは個別の定義書を出力しないが、テーブル一覧には掲載され続ける")
    void testTargetFilterSkipsTableDefinitionButKeepsListing() {
        setUp();
        repository.tables.add(table("public", "keep"));
        repository.tables.add(table("public", "skip"));

        usecase.exportTableDefinition(List.of(), List.of("keep"), null, 0, 80, List.of(), null);

        assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "keep")));
        assertFalse(fileExists(tableDefFile(DEFAULT_OUT, "public", "skip")));

        final String tableListContent = contentOf(DEFAULT_OUT.resolve("tableList_testdb.md"));
        assertTrue(tableListContent.contains("|keep|"));
        assertTrue(tableListContent.contains("|skip|"));
    }

    @Test
    @DisplayName("outputPathが空文字/nullの場合は./outputへフォールバックする")
    void testOutputPathFallbackWhenBlank() {
        setUp();
        repository.tables.add(table("public", "t1"));

        usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null);

        assertTrue(fileExists(DEFAULT_OUT.resolve("tableList_testdb.md")));
    }

    @Test
    @DisplayName("outputPathが空白のみの場合も./outputへフォールバックする")
    void testOutputPathFallbackWhenWhitespace() {
        setUp();
        repository.tables.add(table("public", "t1"));

        usecase.exportTableDefinition(List.of(), List.of(), "   ", 0, 80, List.of(), null);

        assertTrue(fileExists(DEFAULT_OUT.resolve("tableList_testdb.md")));
    }

    @Test
    @DisplayName("outputPathが指定されている場合はそのパス配下に出力する")
    void testOutputPathUsesSpecifiedPath() {
        setUp();
        repository.tables.add(table("public", "t1"));

        usecase.exportTableDefinition(List.of(), List.of(), "custom_out", 0, 80, List.of(), null);

        final Path customOut = Paths.get("custom_out");
        assertTrue(fileExists(customOut.resolve("tableList_testdb.md")));
        assertFalse(fileExists(DEFAULT_OUT.resolve("tableList_testdb.md")));
    }

    @Test
    @DisplayName("チャンクを跨いだ外部キーの参照関係も、ER図の被参照側で正しく解決される")
    void testForeignKeyResolutionAcrossChunkBoundary() {
        setUp();
        IntStream.rangeClosed(1, 4).forEach(i -> repository.tables.add(table("public", "t" + i)));
        // t4（2チャンク目）がt1（1チャンク目）を参照する
        repository.foreignKeys.add(new ForeignKeyEntity("public", "t4", "unused", "fk_t4_t1", "public", "t1"));

        usecase.exportTableDefinition(List.of(), List.of(), null, 2, 80, List.of(), null);

        final String t1Content = contentOf(tableDefFile(DEFAULT_OUT, "public", "t1"));
        // t1はt4から参照されている（被参照側）関係がER図セクションに反映される
        assertTrue(t1Content.contains("public_t1 ||--o{ public_t4 : \"fk_t4_t1\""));
    }

    @Test
    @DisplayName("サイドカーの付帯情報が、テーブル説明・テーブル備考・カラム備考としてテーブル定義書にマージされる")
    void testAnnotationsAreMergedIntoTableDefinition() {
        setUp();
        repository.tables.add(table("public", "t1"));
        repository.columns.add(new ColumnEntity("public", "t1", "|1|論理ID|id|int|Y|N||", "id", "int", "○"));
        annotations = Annotations.of(Map.of(TableKey.of("public", "t1"),
                new TableAnnotation("t1の説明文", "t1の備考", Map.of("id", "主キー"))));

        usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), "conf/annotations.yml");

        // annotationRepositoryにはプロパティで指定したパスがそのまま渡される
        assertEquals("conf/annotations.yml", receivedAnnotationPath);
        final String t1Content = contentOf(tableDefFile(DEFAULT_OUT, "public", "t1"));
        assertTrue(t1Content.contains("t1の説明文"));
        assertTrue(t1Content.contains("t1の備考"));
        assertTrue(t1Content.contains("|1|論理ID|id|int|Y|N||主キー|"));
    }
}
