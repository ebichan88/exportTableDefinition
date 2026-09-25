package com.export_table_definition.application.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.DiffResult;
import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.Sidecar;
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
import com.export_table_definition.domain.model.type.Cardinality;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.domain.repository.AnnotationRepository;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.domain.service.DocumentDiffDomainService;
import com.export_table_definition.domain.service.writer.ErDiagramWriterDomainService;
import com.export_table_definition.domain.service.writer.ObjectListWriterDomainService;
import com.export_table_definition.domain.service.writer.PagedSectionWriter;
import com.export_table_definition.domain.service.writer.TableDefinitionWriterDomainService;
import com.export_table_definition.infrastructure.path.DefaultOutputPathResolver;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    private final AtomicInteger tempDirCounter = new AtomicInteger();

    @Override
    public void writeFile(Path filePath, List<String> contents) {
      files.put(filePath, String.join("", contents));
    }

    @Override
    public void createDirectory(Path filePath) {
      // 何もしない
    }

    @Override
    public List<Path> listFiles(Path directory) {
      return files.keySet().stream().filter(path -> path.startsWith(directory)).sorted().toList();
    }

    @Override
    public List<String> readFile(Path filePath) {
      return List.of(files.getOrDefault(filePath, ""));
    }

    @Override
    public Path createTempDirectory(String prefix) {
      return Paths.get(prefix + tempDirCounter.incrementAndGet());
    }

    @Override
    public void deleteDirectory(Path directory) {
      files.keySet().removeIf(path -> path.startsWith(directory));
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
    public List<ConstraintEntity> selectConstraintList(
        List<String> schemaList, List<String> tableList) {
      constraintCallArgs.add(tableList);
      return constraints.stream().filter(c -> tableList.contains(c.tableName())).toList();
    }

    @Override
    public List<ForeignKeyEntity> selectForeignKeyList(
        List<String> schemaList, List<String> tableList) {
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

  private List<ForeignKeyEntity> logicalRelations = List.of();

  /** annotationRepositoryへ渡されたパスを記録する */
  private String receivedAnnotationPath;

  private void setUp() {
    fileRepository = new InMemoryFileRepository();
    repository = new RecordingRepository(new BaseInfoEntity("testdb", "pg", "2026-09-24"));
    final DefaultOutputPathResolver pathResolver = new DefaultOutputPathResolver();
    final PagedSectionWriter pagedSectionWriter = new PagedSectionWriter(fileRepository);
    final TableDefinitionWriterDomainService writer =
        new TableDefinitionWriterDomainService(fileRepository, pathResolver, pagedSectionWriter);
    final ErDiagramWriterDomainService erDiagramWriter =
        new ErDiagramWriterDomainService(fileRepository, pathResolver, pagedSectionWriter);
    final ObjectListWriterDomainService objectListWriter =
        new ObjectListWriterDomainService(fileRepository, pathResolver, pagedSectionWriter);
    final AnnotationRepository annotationRepository =
        path -> {
          receivedAnnotationPath = path;
          return new Sidecar(annotations, logicalRelations);
        };
    final DocumentDiffDomainService documentDiffDomainService =
        new DocumentDiffDomainService(fileRepository);
    usecase =
        new ExportTableDefinitionUsecaseImpl(
            repository,
            writer,
            erDiagramWriter,
            objectListWriter,
            annotationRepository,
            documentDiffDomainService,
            fileRepository,
            pathResolver);
  }

  private TableEntity table(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, "table", "", "");
  }

  private String contentOf(Path path) {
    return fileRepository.files.entrySet().stream()
        .filter(e -> e.getKey().equals(path))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElseThrow(() -> new AssertionError("生成されていないファイル: " + path));
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
    repository.columns.add(new ColumnEntity("public", "t1", "id", "int", "○"));
    repository.triggers.add(
        new TriggerEntity("public", "t1", "trg_list", "", "", "", "", "trg_info"));
    repository.functions.add(
        new FunctionEntity("testdb", "public", "f1", "f1", "", "", "", "", ""));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "public", "f1", "f1", "", "", "", "", "BODY"));
    repository.sequences.add(
        new SequenceEntity("testdb", "public", "seq1", "", "", "", "", "", "", ""));
    repository.types.add(new TypeEntity("testdb", "public", "type1", "enum", "def"));

    usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null, false);

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
    assertTrue(
        fileExists(
            DEFAULT_OUT.resolve("testdb").resolve("public").resolve("function").resolve("f1.md")));
    assertTrue(
        fileExists(
            DEFAULT_OUT
                .resolve("testdb")
                .resolve("public")
                .resolve("sequence")
                .resolve("seq1.md")));
    assertTrue(
        fileExists(
            DEFAULT_OUT.resolve("testdb").resolve("public").resolve("type").resolve("type1.md")));
  }

  @Test
  @DisplayName("outputObjectListで一部種別のみ指定した場合、指定外の種別は一覧・個別定義・テーブル定義書内セクションとも出力されない")
  void testOutputObjectListRestrictsToSpecifiedTypes() {
    setUp();
    repository.tables.add(table("public", "t1"));
    repository.columns.add(new ColumnEntity("public", "t1", "id", "int", "○"));
    repository.triggers.add(
        new TriggerEntity("public", "t1", "trg_list", "", "", "", "", "trg_info"));
    repository.functions.add(
        new FunctionEntity("testdb", "public", "f1", "f1", "", "", "", "", ""));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "public", "f1", "f1", "", "", "", "", "BODY"));
    repository.sequences.add(
        new SequenceEntity("testdb", "public", "seq1", "", "", "", "", "", "", ""));
    repository.types.add(new TypeEntity("testdb", "public", "type1", "enum", "def"));

    usecase.exportTableDefinition(
        List.of(), List.of(), null, 0, 80, List.of("function"), null, false);

    // 指定したfunctionのみ出力される
    assertTrue(fileExists(DEFAULT_OUT.resolve("functionList_testdb.md")));
    assertTrue(
        fileExists(
            DEFAULT_OUT.resolve("testdb").resolve("public").resolve("function").resolve("f1.md")));

    // 指定外の種別は一覧・個別定義とも出力されない
    assertFalse(fileExists(DEFAULT_OUT.resolve("triggerList_testdb.md")));
    assertFalse(fileExists(DEFAULT_OUT.resolve("sequenceList_testdb.md")));
    assertFalse(fileExists(DEFAULT_OUT.resolve("typeList_testdb.md")));
    assertFalse(
        fileExists(
            DEFAULT_OUT
                .resolve("testdb")
                .resolve("public")
                .resolve("sequence")
                .resolve("seq1.md")));
    assertFalse(
        fileExists(
            DEFAULT_OUT.resolve("testdb").resolve("public").resolve("type").resolve("type1.md")));

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

    usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null, false);

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

    usecase.exportTableDefinition(List.of(), List.of(), null, 2, 80, List.of(), null, false);

    // 5件を2件ずつ取得: 3回に分割される
    assertEquals(3, repository.columnCallArgs.size());
    assertEquals(3, repository.indexCallArgs.size());
    assertEquals(3, repository.constraintCallArgs.size());
    assertEquals(List.of(2, 2, 1), repository.columnCallArgs.stream().map(List::size).toList());

    // チャンク境界を跨いでも全テーブル分の定義書が出力される
    IntStream.rangeClosed(1, 5)
        .forEach(
            i ->
                assertTrue(
                    fileExists(tableDefFile(DEFAULT_OUT, "public", "t" + i)), "t" + i + "が欠落"));

    // 外部キー・トリガーはチャンク化せず、対象範囲全体を1回だけ取得する
    assertEquals(1, repository.foreignKeyListCalls);
  }

  @Test
  @DisplayName("chunkSizeが0以下の場合はスキーマ全体を1回で取得する")
  void testChunkSizeZeroMeansSingleChunk() {
    setUp();
    IntStream.rangeClosed(1, 5).forEach(i -> repository.tables.add(table("public", "t" + i)));

    usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null, false);

    assertEquals(1, repository.columnCallArgs.size());
    assertEquals(5, repository.columnCallArgs.get(0).size());
  }

  @Test
  @DisplayName("関数定義本体はテーブル数・関数数ではなくスキーマ数の回数だけ取得される")
  void testFunctionDefinitionsFetchedPerSchemaNotPerFunction() {
    setUp();
    repository.tables.add(table("public", "t1"));
    repository.functions.add(new FunctionEntity("testdb", "s1", "f1", "f1", "", "", "", "", ""));
    repository.functions.add(new FunctionEntity("testdb", "s1", "f2", "f2", "", "", "", "", ""));
    repository.functions.add(new FunctionEntity("testdb", "s2", "f3", "f3", "", "", "", "", ""));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "s1", "f1", "f1", "", "", "", "", "BODY1"));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "s1", "f2", "f2", "", "", "", "", "BODY2"));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "s2", "f3", "f3", "", "", "", "", "BODY3"));

    usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null, false);

    // 関数は3件だが、スキーマは2件のためselectFunctionDefListは2回のみ呼ばれる
    assertEquals(2, repository.functionDefCallArgs.size());
    assertTrue(repository.functionDefCallArgs.stream().allMatch(args -> args.size() == 1));

    // 3件分の個別ファイルはすべて出力される
    assertTrue(
        fileExists(
            DEFAULT_OUT.resolve("testdb").resolve("s1").resolve("function").resolve("f1.md")));
    assertTrue(
        fileExists(
            DEFAULT_OUT.resolve("testdb").resolve("s1").resolve("function").resolve("f2.md")));
    assertTrue(
        fileExists(
            DEFAULT_OUT.resolve("testdb").resolve("s2").resolve("function").resolve("f3.md")));
  }

  @Test
  @DisplayName("targetTableListで対象外となったテーブルは個別の定義書にもテーブル一覧にも掲載されない")
  void testTargetFilterExcludesTableFromDefinitionAndListing() {
    setUp();
    repository.tables.add(table("public", "keep"));
    repository.tables.add(table("public", "skip"));

    usecase.exportTableDefinition(List.of(), List.of("keep"), null, 0, 80, List.of(), null, false);

    assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "keep")));
    assertFalse(fileExists(tableDefFile(DEFAULT_OUT, "public", "skip")));

    final String tableListContent = contentOf(DEFAULT_OUT.resolve("tableList_testdb.md"));
    assertTrue(tableListContent.contains("|keep|"));
    assertFalse(tableListContent.contains("|skip|"));
  }

  @Test
  @DisplayName("targetTableListにワイルドカードを指定すると、パターンに一致するテーブルのみ出力される")
  void testTargetFilterSupportsWildcard() {
    setUp();
    repository.tables.add(table("public", "employee"));
    repository.tables.add(table("public", "employee_bk"));

    usecase.exportTableDefinition(List.of(), List.of("!*_bk"), null, 0, 80, List.of(), null, false);

    assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "employee")));
    assertFalse(fileExists(tableDefFile(DEFAULT_OUT, "public", "employee_bk")));
  }

  @Test
  @DisplayName("targetTableListにスキーマ修飾パターンを指定すると、対象スキーマのテーブルのみ出力される")
  void testTargetFilterSupportsSchemaQualifiedPattern() {
    setUp();
    repository.tables.add(table("public", "employee"));
    repository.tables.add(table("other", "employee"));

    usecase.exportTableDefinition(
        List.of(), List.of("public.employee"), null, 0, 80, List.of(), null, false);

    assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "employee")));
    assertFalse(fileExists(tableDefFile(DEFAULT_OUT, "other", "employee")));
  }

  @Test
  @DisplayName("outputPathが空文字/nullの場合は./outputへフォールバックする")
  void testOutputPathFallbackWhenBlank() {
    setUp();
    repository.tables.add(table("public", "t1"));

    usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null, false);

    assertTrue(fileExists(DEFAULT_OUT.resolve("tableList_testdb.md")));
  }

  @Test
  @DisplayName("outputPathが空白のみの場合も./outputへフォールバックする")
  void testOutputPathFallbackWhenWhitespace() {
    setUp();
    repository.tables.add(table("public", "t1"));

    usecase.exportTableDefinition(List.of(), List.of(), "   ", 0, 80, List.of(), null, false);

    assertTrue(fileExists(DEFAULT_OUT.resolve("tableList_testdb.md")));
  }

  @Test
  @DisplayName("outputPathが指定されている場合はそのパス配下に出力する")
  void testOutputPathUsesSpecifiedPath() {
    setUp();
    repository.tables.add(table("public", "t1"));

    usecase.exportTableDefinition(
        List.of(), List.of(), "custom_out", 0, 80, List.of(), null, false);

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
    repository.foreignKeys.add(
        ForeignKeyFixtures.physical("public", "t4", "fk_t4_t1", "public", "t1"));

    usecase.exportTableDefinition(List.of(), List.of(), null, 2, 80, List.of(), null, false);

    final String t1Content = contentOf(tableDefFile(DEFAULT_OUT, "public", "t1"));
    // t1はt4から参照されている（被参照側）関係がER図セクションに反映される
    assertTrue(t1Content.contains("public_t1 ||--o{ public_t4 : \"fk_t4_t1\""));
  }

  @Test
  @DisplayName("targetTableListの絞り込みで除外されたテーブルへの物理外部キーは、スキーマ別ER図から除外される")
  void testForeignKeyToExcludedTableIsRemovedFromErDiagram() {
    setUp();
    repository.tables.add(table("public", "keep"));
    repository.tables.add(table("public", "skip"));
    // keepがskip（絞り込みで除外される）を参照する
    repository.foreignKeys.add(
        ForeignKeyFixtures.physical("public", "keep", "fk_keep_skip", "public", "skip"));

    usecase.exportTableDefinition(List.of(), List.of("keep"), null, 0, 80, List.of(), null, false);

    // skipは出力対象外のため、そのテーブルへの外部キーはスキーマ別ER図に箱としても線としても現れない
    final String erContent = contentOf(DEFAULT_OUT.resolve("erDiagram_testdb_public.md"));
    assertFalse(erContent.contains("skip"));
    assertFalse(erContent.contains("fk_keep_skip"));
  }

  @Test
  @DisplayName("サイドカーの付帯情報が、テーブル説明・テーブル備考・カラム備考としてテーブル定義書にマージされる")
  void testAnnotationsAreMergedIntoTableDefinition() {
    setUp();
    repository.tables.add(table("public", "t1"));
    repository.columns.add(new ColumnEntity("public", "t1", "論理ID", "id", "int", "", "○", "○", ""));
    annotations =
        Annotations.of(
            Map.of(
                TableKey.of("public", "t1"),
                new TableAnnotation("t1の説明文", "t1の備考", Map.of("id", "主キー"))));

    usecase.exportTableDefinition(
        List.of(), List.of(), null, 0, 80, List.of(), "conf/annotations.yml", false);

    // annotationRepositoryにはプロパティで指定したパスがそのまま渡される
    assertEquals("conf/annotations.yml", receivedAnnotationPath);
    final String t1Content = contentOf(tableDefFile(DEFAULT_OUT, "public", "t1"));
    assertTrue(t1Content.contains("t1の説明文"));
    assertTrue(t1Content.contains("t1の備考"));
    assertTrue(t1Content.contains("|1|論理ID|id|int||○|○||主キー|"));
  }

  @Test
  @DisplayName("checkDocumentDiff: 一時ディレクトリへ生成した結果と、指定したoutputPath配下を比較する")
  void testCheckDocumentDiffComparesGeneratedResultAgainstOutputPath() {
    setUp();
    repository.tables.add(table("public", "t1"));

    final DiffResult result =
        usecase.checkDocumentDiff(List.of(), List.of(), "committed", 0, 80, List.of(), null);

    // outputPath（committed）側には何も存在しないため、生成された全ファイルがonlyInGeneratedとして検出される
    assertTrue(result.hasDifference());
    assertTrue(result.onlyInCommitted().isEmpty());
    assertTrue(result.contentDiffer().isEmpty());
    assertTrue(result.onlyInGenerated().contains(Paths.get("tableList_testdb.md")));
    assertTrue(result.onlyInGenerated().contains(tableDefFile(Paths.get(""), "public", "t1")));
  }

  @Test
  @DisplayName("checkDocumentDiff: 比較対象として指定したoutputPath配下には書き込みを行わない")
  void testCheckDocumentDiffDoesNotWriteToOutputPath() {
    setUp();
    repository.tables.add(table("public", "t1"));

    usecase.checkDocumentDiff(List.of(), List.of(), "committed", 0, 80, List.of(), null);

    final Path committedDir = Paths.get("committed");
    assertTrue(
        fileRepository.files.keySet().stream().noneMatch(path -> path.startsWith(committedDir)));
  }

  @Test
  @DisplayName("checkDocumentDiff: exportTableDefinitionと同じ引数（スキーマ・テーブル絞り込み等）が一時生成に反映される")
  void testCheckDocumentDiffAppliesSameFiltersAsExport() {
    setUp();
    repository.tables.add(table("public", "keep"));
    repository.tables.add(table("public", "skip"));

    final DiffResult result =
        usecase.checkDocumentDiff(List.of(), List.of("keep"), "committed", 0, 80, List.of(), null);

    assertTrue(result.onlyInGenerated().contains(tableDefFile(Paths.get(""), "public", "keep")));
    assertFalse(result.onlyInGenerated().contains(tableDefFile(Paths.get(""), "public", "skip")));
  }

  @Test
  @DisplayName("サイドカーの論理リレーションが、専用セクションとER図（破線）の双方に反映される")
  void testLogicalRelationsAreMergedIntoTableDefinition() {
    setUp();
    repository.tables.add(table("public", "audit_log"));
    repository.tables.add(table("public", "employee"));
    logicalRelations =
        List.of(
            ForeignKeyFixtures.logical(
                "public",
                "audit_log",
                "rel_audit_employee",
                "record_id",
                "public",
                "employee",
                "employee_id",
                Cardinality.OPTIONAL_ONE_TO_MANY));

    usecase.exportTableDefinition(
        List.of(), List.of(), null, 0, 80, List.of(), "conf/annotations.yml", false);

    final String auditContent = contentOf(tableDefFile(DEFAULT_OUT, "public", "audit_log"));
    // 参照元は専用セクションに掲載され、外部キー情報セクションには現れない
    assertTrue(auditContent.contains("## 論理リレーション情報"));
    assertTrue(
        auditContent.contains(
            "|1|rel_audit_employee|record_id|public.employee|employee_id|0..1対多|"));
    assertTrue(
        auditContent.contains("public_employee |o..o{ public_audit_log : \"rel_audit_employee\""));

    // 参照先には被参照側としてER図にのみ現れ、専用セクションは出力されない
    final String employeeContent = contentOf(tableDefFile(DEFAULT_OUT, "public", "employee"));
    assertFalse(employeeContent.contains("## 論理リレーション情報"));
    assertTrue(
        employeeContent.contains(
            "public_employee |o..o{ public_audit_log : \"rel_audit_employee\""));
  }

  @Test
  @DisplayName("論理リレーションを持たないテーブルには、論理リレーション情報セクションを出力しない")
  void testLogicalRelationSectionOmittedWhenNotDeclared() {
    setUp();
    repository.tables.add(table("public", "t1"));

    usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null, false);

    assertFalse(contentOf(tableDefFile(DEFAULT_OUT, "public", "t1")).contains("## 論理リレーション情報"));
  }

  @Test
  @DisplayName("参照元・参照先のいずれかが出力対象に存在しない論理リレーションは除外される")
  void testLogicalRelationSkippedWhenTableMissing() {
    setUp();
    repository.tables.add(table("public", "audit_log"));
    // 参照先のemployeeは出力対象に存在しない（絞り込み・リネーム・削除を想定）
    logicalRelations =
        List.of(
            ForeignKeyFixtures.logical(
                "public", "audit_log", "rel_audit_employee", "public", "employee"));

    usecase.exportTableDefinition(
        List.of(), List.of(), null, 0, 80, List.of(), "conf/annotations.yml", false);

    final String auditContent = contentOf(tableDefFile(DEFAULT_OUT, "public", "audit_log"));
    assertFalse(auditContent.contains("## 論理リレーション情報"));
    assertFalse(auditContent.contains("rel_audit_employee"));
  }

  @Test
  @DisplayName("論理リレーションもスキーマ別ER図に破線で描画される")
  void testLogicalRelationAppearsInSchemaErDiagram() {
    setUp();
    repository.tables.add(table("public", "audit_log"));
    repository.tables.add(table("public", "employee"));
    logicalRelations =
        List.of(
            ForeignKeyFixtures.logical(
                "public", "audit_log", "rel_audit_employee", "public", "employee"));

    usecase.exportTableDefinition(
        List.of(), List.of(), null, 0, 80, List.of(), "conf/annotations.yml", false);

    final String erContent = contentOf(DEFAULT_OUT.resolve("erDiagram_testdb_public.md"));
    assertTrue(erContent.contains("||..o{"));
    assertTrue(erContent.contains("rel_audit_employee"));
  }

  @Test
  @DisplayName("rmDist=trueの場合、書き込み前に出力先ディレクトリ配下の既存ファイルを削除する")
  void testRmDistRemovesStaleFilesBeforeWriting() {
    setUp();
    repository.tables.add(table("public", "t1"));
    // 前回実行の残骸ファイル（削除されたテーブルの定義書を想定）を出力先へ事前に配置しておく
    final Path staleFile =
        DEFAULT_OUT
            .resolve("testdb")
            .resolve("public")
            .resolve("table")
            .resolve("removed_table.md");
    fileRepository.files.put(staleFile, "stale content");

    usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null, true);

    assertFalse(fileExists(staleFile));
    assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "t1")));
  }

  @Test
  @DisplayName("rmDist=falseの場合、出力先ディレクトリ配下の既存ファイルは削除されない")
  void testRmDistFalseKeepsStaleFiles() {
    setUp();
    repository.tables.add(table("public", "t1"));
    final Path staleFile =
        DEFAULT_OUT
            .resolve("testdb")
            .resolve("public")
            .resolve("table")
            .resolve("removed_table.md");
    fileRepository.files.put(staleFile, "stale content");

    usecase.exportTableDefinition(List.of(), List.of(), null, 0, 80, List.of(), null, false);

    assertTrue(fileExists(staleFile));
    assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "t1")));
  }

  @Test
  @DisplayName("rmDist=trueかつoutputPathがカレントディレクトリ自体に解決される場合は例外を投げて削除を拒否する")
  void testRmDistRefusesToRemoveCurrentDirectory() {
    setUp();
    repository.tables.add(table("public", "t1"));

    assertThrows(
        IllegalStateException.class,
        () ->
            usecase.exportTableDefinition(List.of(), List.of(), ".", 0, 80, List.of(), null, true));
  }
}
