package com.export_table_definition.application.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.application.CheckDiffRequest;
import com.export_table_definition.application.ExportRequest;
import com.export_table_definition.application.TargetSelection;
import com.export_table_definition.domain.model.database.DatabaseEntity;
import com.export_table_definition.domain.model.relation.Cardinality;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.model.sidecar.Annotations;
import com.export_table_definition.domain.model.sidecar.Sidecar;
import com.export_table_definition.domain.model.sidecar.TableAnnotation;
import com.export_table_definition.domain.model.snapshot.ContentDiff;
import com.export_table_definition.domain.model.snapshot.DiffResult;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.ConstraintEntity;
import com.export_table_definition.domain.model.table.IndexEntity;
import com.export_table_definition.domain.model.table.TableDetail;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableKey;
import com.export_table_definition.domain.model.table.TableType;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import com.export_table_definition.domain.model.viewpoint.Viewpoints;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.repository.SidecarRepository;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.domain.service.UnifiedDiffGenerator;
import com.export_table_definition.domain.service.export.MarkdownExportSinkFactory;
import com.export_table_definition.domain.service.export.SnapshotExportSinkFactory;
import com.export_table_definition.domain.service.snapshot.SchemaSnapshotWriterDomainService;
import com.export_table_definition.domain.service.snapshot.SnapshotDiffDomainService;
import com.export_table_definition.domain.service.target.ExportTargetConsistencyDomainService;
import com.export_table_definition.domain.service.writer.ErDiagramWriterDomainService;
import com.export_table_definition.domain.service.writer.ObjectListWriterDomainService;
import com.export_table_definition.domain.service.writer.PagedSectionWriter;
import com.export_table_definition.domain.service.writer.TableDefinitionWriterDomainService;
import com.export_table_definition.domain.service.writer.ViewpointWriterDomainService;
import com.export_table_definition.infrastructure.path.DefaultOutputPathResolver;
import com.export_table_definition.infrastructure.snapshot.JacksonSnapshotSerializer;
import com.export_table_definition.testsupport.EntityFixtures;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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

    /** 書き込み（上書き・追記）を行ったファイルパスの履歴（一時ディレクトリの削除後も残る） */
    private final List<Path> writtenPaths = new ArrayList<>();

    @Override
    public void writeFile(Path filePath, List<String> contents) {
      writtenPaths.add(filePath);
      files.put(filePath, String.join("", contents));
    }

    @Override
    public void appendFile(Path filePath, List<String> contents) {
      writtenPaths.add(filePath);
      files.merge(filePath, String.join("", contents), String::concat);
    }

    @Override
    public void createDirectory(Path filePath) {
      // 何もしない
    }

    @Override
    public boolean exists(Path path) {
      return files.keySet().stream().anyMatch(file -> file.startsWith(path));
    }

    @Override
    public boolean isDirectory(Path path) {
      return files.keySet().stream().anyMatch(file -> file.startsWith(path) && !file.equals(path));
    }

    @Override
    public List<Path> listFiles(Path directory) {
      return files.keySet().stream().filter(path -> path.startsWith(directory)).sorted().toList();
    }

    @Override
    public List<String> readFile(Path filePath) {
      return files.getOrDefault(filePath, "").lines().toList();
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
    private final DatabaseEntity database;
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

    /** 詳細情報（カラム・インデックス・制約）の取得1回ごとの対象テーブル名 */
    final List<List<String>> tableDetailCallArgs = new ArrayList<>();

    final List<List<String>> functionDefCallArgs = new ArrayList<>();
    int foreignKeyListCalls = 0;

    /** テーブル一覧の取得時に投げる例外（DBからの取得失敗の再現用。nullの場合は投げない） */
    RuntimeException tableListFailure;

    RecordingRepository(DatabaseEntity database) {
      this.database = database;
    }

    @Override
    public DatabaseEntity selectDatabase() {
      return database;
    }

    @Override
    public List<TableEntity> selectTableList(List<String> schemaList) {
      if (tableListFailure != null) {
        throw tableListFailure;
      }
      return List.copyOf(tables);
    }

    @Override
    public List<TableDetail> selectTableDetails(List<TableEntity> chunk) {
      final List<String> tableList = chunk.stream().map(TableEntity::physicalTableName).toList();
      tableDetailCallArgs.add(tableList);
      return TableDetail.assembleAll(
          chunk,
          columns.stream().filter(c -> tableList.contains(c.tableName())).toList(),
          indexes.stream().filter(i -> tableList.contains(i.tableName())).toList(),
          constraints.stream().filter(c -> tableList.contains(c.tableName())).toList());
    }

    @Override
    public List<ForeignKeyEntity> selectForeignKeyList(List<String> schemaList) {
      foreignKeyListCalls++;
      return List.copyOf(foreignKeys);
    }

    @Override
    public List<TriggerEntity> selectTriggerList(List<String> schemaList) {
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

  /** 既定の生成日（テストの実行日によらず出力を固定するため、時計を固定する） */
  private static final LocalDate GENERATED_DATE = LocalDate.of(2026, 9, 24);

  /** 生成日を指定してユースケースを組み立てる（同じスタブ・出力先を共有したまま実行日だけを変えるため） */
  private Function<LocalDate, ExportTableDefinitionUsecaseImpl> usecaseAt;

  private CheckDocumentDiffUsecaseImpl checkUsecase;

  /** 生成日を指定して差分検知のユースケースを組み立てる */
  private Function<LocalDate, CheckDocumentDiffUsecaseImpl> checkUsecaseAt;

  /** annotationRepositoryスタブが返す付帯情報（テストごとに差し替え可能） */
  private Annotations annotations = Annotations.empty();

  private List<ForeignKeyEntity> logicalRelations = List.of();
  private Viewpoints viewpoints = Viewpoints.empty();

  /** annotationRepositoryへ渡されたパスを記録する */
  private String receivedSidecarPath;

  /** サイドカーの読み込み時に投げる例外（読み込み失敗の再現用。nullの場合は投げない） */
  private RuntimeException sidecarFailure;

  private void setUp() {
    fileRepository = new InMemoryFileRepository();
    repository = new RecordingRepository(new DatabaseEntity("testdb", "pg"));
    final DefaultOutputPathResolver pathResolver = new DefaultOutputPathResolver();
    final PagedSectionWriter pagedSectionWriter =
        new PagedSectionWriter(fileRepository, pathResolver);
    final TableDefinitionWriterDomainService writer =
        new TableDefinitionWriterDomainService(fileRepository, pathResolver, pagedSectionWriter);
    final ErDiagramWriterDomainService erDiagramWriter =
        new ErDiagramWriterDomainService(fileRepository, pathResolver, pagedSectionWriter);
    final ObjectListWriterDomainService objectListWriter =
        new ObjectListWriterDomainService(fileRepository, pathResolver, pagedSectionWriter);
    final SidecarRepository sidecarRepository =
        path -> {
          receivedSidecarPath = path;
          if (sidecarFailure != null) {
            throw sidecarFailure;
          }
          return new Sidecar(annotations, logicalRelations, viewpoints);
        };
    final JacksonSnapshotSerializer serializer = new JacksonSnapshotSerializer();
    final SchemaSnapshotWriterDomainService snapshotWriter =
        new SchemaSnapshotWriterDomainService(fileRepository, pathResolver, serializer);
    final SnapshotDiffDomainService snapshotDiffDomainService =
        new SnapshotDiffDomainService(
            fileRepository, pathResolver, serializer, new UnifiedDiffGenerator());
    final SnapshotExportSinkFactory snapshotSinkFactory =
        new SnapshotExportSinkFactory(snapshotWriter);
    final Function<LocalDate, SchemaExporter> schemaExporterAt =
        generatedDate ->
            new SchemaExporter(
                repository,
                sidecarRepository,
                new ExportTargetConsistencyDomainService(),
                Clock.fixed(
                    generatedDate.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    usecaseAt =
        generatedDate ->
            new ExportTableDefinitionUsecaseImpl(
                schemaExporterAt.apply(generatedDate),
                new MarkdownExportSinkFactory(
                    writer,
                    erDiagramWriter,
                    objectListWriter,
                    new ViewpointWriterDomainService(
                        fileRepository, pathResolver, pagedSectionWriter)),
                snapshotSinkFactory,
                fileRepository,
                pathResolver);
    checkUsecaseAt =
        generatedDate ->
            new CheckDocumentDiffUsecaseImpl(
                schemaExporterAt.apply(generatedDate),
                snapshotSinkFactory,
                snapshotDiffDomainService,
                fileRepository,
                pathResolver);
    usecase = usecaseAt.apply(GENERATED_DATE);
    checkUsecase = checkUsecaseAt.apply(GENERATED_DATE);
  }

  private TableEntity table(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, TableType.TABLE, "");
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
    repository.columns.add(EntityFixtures.column("public", "t1", "id", "int", true));
    repository.triggers.add(
        new TriggerEntity("public", "t1", "trg_list", "", List.of(), "", "", "trg_info"));
    repository.functions.add(
        new FunctionEntity("testdb", "public", "f1", 1, 1, "", "", "", "", ""));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "public", "f1", 1, 1, "", "", "", "", "BODY"));
    repository.sequences.add(
        new SequenceEntity("testdb", "public", "seq1", "", "", "", "", "", false, ""));
    repository.types.add(new TypeEntity("testdb", "public", "type1", "enum", "def"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

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
    repository.columns.add(EntityFixtures.column("public", "t1", "id", "int", true));
    repository.triggers.add(
        new TriggerEntity("public", "t1", "trg_list", "", List.of(), "", "", "trg_info"));
    repository.functions.add(
        new FunctionEntity("testdb", "public", "f1", 1, 1, "", "", "", "", ""));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "public", "f1", 1, 1, "", "", "", "", "BODY"));
    repository.sequences.add(
        new SequenceEntity("testdb", "public", "seq1", "", "", "", "", "", false, ""));
    repository.types.add(new TypeEntity("testdb", "public", "type1", "enum", "def"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of("function"), null),
            null,
            0,
            80,
            false));

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

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

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
  @DisplayName("テーブルが0件の場合、テーブル一覧は出力するがER図一覧は出力せず、リンクも掲載しない")
  void testNoTablesWritesNoErDiagramAndNoLink() {
    setUp();
    repository.sequences.add(
        new SequenceEntity("testdb", "public", "seq1", "", "", "", "", "", false, ""));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

    final String tableListContent = contentOf(DEFAULT_OUT.resolve("tableList_testdb.md"));
    assertFalse(tableListContent.contains("ER図一覧"));
    assertTrue(tableListContent.contains("シーケンス一覧"));
    assertFalse(fileExists(DEFAULT_OUT.resolve("erDiagramList_testdb.md")));
    assertTrue(fileExists(DEFAULT_OUT.resolve("sequenceList_testdb.md")));
  }

  @Test
  @DisplayName("chunkSize指定時は、指定件数ごとにテーブル詳細を分割取得しつつ全テーブル分を出力する")
  void testChunkSizeSplitsRepositoryCallsPerChunk() {
    setUp();
    IntStream.rangeClosed(1, 5).forEach(i -> repository.tables.add(table("public", "t" + i)));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 2, 80, false));

    // 5件を2件ずつ取得: 3回に分割される
    assertEquals(3, repository.tableDetailCallArgs.size());
    assertEquals(
        List.of(2, 2, 1), repository.tableDetailCallArgs.stream().map(List::size).toList());

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

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

    assertEquals(1, repository.tableDetailCallArgs.size());
    assertEquals(5, repository.tableDetailCallArgs.get(0).size());
  }

  @Test
  @DisplayName("関数定義本体はテーブル数・関数数ではなくスキーマ数の回数だけ取得される")
  void testFunctionDefinitionsFetchedPerSchemaNotPerFunction() {
    setUp();
    repository.tables.add(table("public", "t1"));
    repository.functions.add(new FunctionEntity("testdb", "s1", "f1", 1, 1, "", "", "", "", ""));
    repository.functions.add(new FunctionEntity("testdb", "s1", "f2", 1, 1, "", "", "", "", ""));
    repository.functions.add(new FunctionEntity("testdb", "s2", "f3", 1, 1, "", "", "", "", ""));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "s1", "f1", 1, 1, "", "", "", "", "BODY1"));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "s1", "f2", 1, 1, "", "", "", "", "BODY2"));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "s2", "f3", 1, 1, "", "", "", "", "BODY3"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

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

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of("keep"), List.of(), null), null, 0, 80, false));

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

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of("!*_bk"), List.of(), null), null, 0, 80, false));

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
        new ExportRequest(
            TargetSelection.of(List.of(), List.of("public.employee"), List.of(), null),
            null,
            0,
            80,
            false));

    assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "employee")));
    assertFalse(fileExists(tableDefFile(DEFAULT_OUT, "other", "employee")));
  }

  @Test
  @DisplayName("outputPathが空文字/nullの場合は./outputへフォールバックする")
  void testOutputPathFallbackWhenBlank() {
    setUp();
    repository.tables.add(table("public", "t1"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

    assertTrue(fileExists(DEFAULT_OUT.resolve("tableList_testdb.md")));
  }

  @Test
  @DisplayName("outputPathが空白のみの場合も./outputへフォールバックする")
  void testOutputPathFallbackWhenWhitespace() {
    setUp();
    repository.tables.add(table("public", "t1"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), "   ", 0, 80, false));

    assertTrue(fileExists(DEFAULT_OUT.resolve("tableList_testdb.md")));
  }

  @Test
  @DisplayName("outputPathが指定されている場合はそのパス配下に出力する")
  void testOutputPathUsesSpecifiedPath() {
    setUp();
    repository.tables.add(table("public", "t1"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), "custom_out", 0, 80, false));

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

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 2, 80, false));

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

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of("keep"), List.of(), null), null, 0, 80, false));

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
    repository.columns.add(
        new ColumnEntity("public", "t1", "論理ID", "id", "int", "", true, true, ""));
    annotations =
        Annotations.of(
            Map.of(
                TableKey.of("public", "t1"),
                new TableAnnotation("t1の説明文", "t1の備考", Map.of("id", "主キー"))));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), "conf/annotations.yml"),
            null,
            0,
            80,
            false));

    // annotationRepositoryにはプロパティで指定したパスがそのまま渡される
    assertEquals("conf/annotations.yml", receivedSidecarPath);
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
        checkUsecase.checkDocumentDiff(
            new CheckDiffRequest(
                TargetSelection.of(List.of(), List.of(), List.of(), null), "committed", 0));

    // outputPath（committed）側には何も存在しないため、生成された全オブジェクトがonlyInGeneratedとして検出される
    assertTrue(result.hasDifference());
    assertTrue(result.onlyInCommitted().isEmpty());
    assertTrue(result.contentDiffer().isEmpty());
    assertEquals(
        List.of("table public.t1", Paths.get("testdb", "database.json").toString()),
        result.onlyInGenerated());
  }

  @Test
  @DisplayName("checkDocumentDiff: 比較対象として指定したoutputPath配下には書き込みを行わない")
  void testCheckDocumentDiffDoesNotWriteToOutputPath() {
    setUp();
    repository.tables.add(table("public", "t1"));

    checkUsecase.checkDocumentDiff(
        new CheckDiffRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), "committed", 0));

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
        checkUsecase.checkDocumentDiff(
            new CheckDiffRequest(
                TargetSelection.of(List.of(), List.of("keep"), List.of(), null), "committed", 0));

    assertTrue(result.onlyInGenerated().contains("table public.keep"));
    assertFalse(result.onlyInGenerated().contains("table public.skip"));
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
                List.of("record_id"),
                "public",
                "employee",
                List.of("employee_id"),
                Cardinality.OPTIONAL_ONE_TO_MANY));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), "conf/annotations.yml"),
            null,
            0,
            80,
            false));

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

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

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
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), "conf/annotations.yml"),
            null,
            0,
            80,
            false));

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
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), "conf/annotations.yml"),
            null,
            0,
            80,
            false));

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

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, true));

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

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

    assertTrue(fileExists(staleFile));
    assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "t1")));
  }

  @Test
  @DisplayName("基本情報の作成日は、DBではなく実行時の時計の日付を用いる")
  void testGeneratedDateComesFromClock() {
    setUp();
    repository.tables.add(table("public", "t1"));
    usecase = usecaseAt.apply(LocalDate.of(2031, 12, 31));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

    assertTrue(
        contentOf(DEFAULT_OUT.resolve("tableList_testdb.md")).contains("|pg|testdb|2031/12/31|"));
    assertTrue(
        contentOf(tableDefFile(DEFAULT_OUT, "public", "t1")).contains("|pg|testdb|2031/12/31|"));
  }

  @Test
  @DisplayName("rmDist=trueでも、サイドカーの読み込みに失敗した場合は既存の出力を削除しない")
  void testRmDistKeepsExistingOutputWhenSidecarLoadFails() {
    setUp();
    repository.tables.add(table("public", "t1"));
    final Path existingFile = tableDefFile(DEFAULT_OUT, "public", "t1");
    fileRepository.files.put(existingFile, "previous content");
    sidecarFailure = new IllegalStateException("broken sidecar");

    assertThrows(
        IllegalStateException.class,
        () ->
            usecase.exportTableDefinition(
                new ExportRequest(
                    TargetSelection.of(List.of(), List.of(), List.of(), "broken.yml"),
                    null,
                    0,
                    80,
                    true)));
    assertTrue(fileExists(existingFile));
  }

  @Test
  @DisplayName("rmDist=trueでも、DBからの一括取得に失敗した場合は既存の出力を削除しない")
  void testRmDistKeepsExistingOutputWhenFetchFails() {
    setUp();
    final Path existingFile = tableDefFile(DEFAULT_OUT, "public", "t1");
    fileRepository.files.put(existingFile, "previous content");
    repository.tableListFailure = new IllegalStateException("database is unavailable");

    assertThrows(
        IllegalStateException.class,
        () ->
            usecase.exportTableDefinition(
                new ExportRequest(
                    TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, true)));
    assertTrue(fileExists(existingFile));
  }

  private Path snapshotFile(String schema, String fileName) {
    return DEFAULT_OUT.resolve("snapshot").resolve("testdb").resolve(schema).resolve(fileName);
  }

  @Test
  @DisplayName("Markdownに加えてDB全体・テーブル・関数・シーケンス・型のスナップショットを出力する")
  void testExportWritesSnapshotAlongsideMarkdown() {
    setUp();
    repository.tables.add(table("public", "t1"));
    repository.columns.add(EntityFixtures.column("public", "t1", "id", "int", true));
    repository.functions.add(
        new FunctionEntity("testdb", "public", "f1", 1, 1, "FUNCTION", "", "int", "sql", ""));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "public", "f1", 1, 1, "FUNCTION", "", "int", "sql", "BODY"));
    repository.sequences.add(
        new SequenceEntity("testdb", "public", "seq1", "1", "", "", "", "", false, ""));
    repository.types.add(new TypeEntity("testdb", "public", "type1", "ENUM", "a, b"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

    assertTrue(fileExists(tableDefFile(DEFAULT_OUT, "public", "t1")));
    assertEquals(
        "{\"formatVersion\":1,\"name\":\"testdb\",\"dbms\":\"pg\"}\n",
        contentOf(DEFAULT_OUT.resolve("snapshot").resolve("testdb").resolve("database.json")));
    assertTrue(
        contentOf(snapshotFile("public", "tables.jsonl"))
            .contains("\"columns\":[{\"name\":\"id\",\"type\":\"int\",\"primaryKey\":true"));
    assertTrue(
        contentOf(snapshotFile("public", "functions.jsonl")).contains("\"definition\":\"BODY\""));
    assertTrue(contentOf(snapshotFile("public", "sequences.jsonl")).contains("\"name\":\"seq1\""));
    assertTrue(contentOf(snapshotFile("public", "types.jsonl")).contains("\"name\":\"type1\""));
  }

  @Test
  @DisplayName("chunk分割しても全テーブルがスキーマ単位のスナップショットファイルへ取得順に1行ずつ出力される")
  void testSnapshotAppendsAllChunksInOrder() {
    setUp();
    IntStream.rangeClosed(1, 5).forEach(i -> repository.tables.add(table("public", "t" + i)));
    repository.tables.add(table("sales", "s1"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 2, 80, false));

    final List<String> publicLines =
        contentOf(snapshotFile("public", "tables.jsonl")).lines().toList();
    assertEquals(5, publicLines.size());
    IntStream.rangeClosed(1, 5)
        .forEach(
            i ->
                assertTrue(
                    publicLines
                        .get(i - 1)
                        .startsWith("{\"schema\":\"public\",\"name\":\"t" + i + "\"")));
    assertEquals(1, contentOf(snapshotFile("sales", "tables.jsonl")).lines().count());
  }

  @Test
  @DisplayName("前回実行時のテーブルのスナップショットへ追記せず作り直す")
  void testSnapshotDoesNotAppendToPreviousRun() {
    setUp();
    repository.tables.add(table("public", "t1"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));
    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

    assertEquals(1, contentOf(snapshotFile("public", "tables.jsonl")).lines().count());
  }

  /** "committed"へ出力し、コミット済みの状態を作る */
  private void exportCommitted() {
    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), "committed", 0, 80, false));
  }

  /** "committed"に対してスナップショット同士の比較を行う */
  private DiffResult checkSnapshotDiff() {
    return checkUsecase.checkDocumentDiff(
        new CheckDiffRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), "committed", 0));
  }

  @Test
  @DisplayName("checkDocumentDiff: Markdownの描画・ER図の生成を行わず、スナップショットのみを生成して比較する")
  void testCheckSnapshotDiffDoesNotRenderMarkdown() {
    setUp();
    repository.tables.add(table("public", "t1"));
    repository.foreignKeys.add(ForeignKeyFixtures.physical("public", "t1", "fk", "public", "t1"));
    repository.functions.add(
        new FunctionEntity("testdb", "public", "f1", 1, 1, "FUNCTION", "", "int", "sql", ""));
    repository.functionDefs.add(
        new FunctionEntity("testdb", "public", "f1", 1, 1, "FUNCTION", "", "int", "sql", "BODY"));

    final DiffResult result = checkSnapshotDiff();

    assertTrue(fileRepository.writtenPaths.stream().noneMatch(p -> p.toString().endsWith(".md")));
    // committed側にスナップショットが存在しないため、生成した全オブジェクトがonlyInGeneratedとなる
    assertEquals(
        List.of(
            "function public.f1()",
            "table public.t1",
            Paths.get("testdb", "database.json").toString()),
        result.onlyInGenerated());
    assertTrue(result.onlyInGenerated().stream().noneMatch(target -> target.endsWith(".md")));
  }

  @Test
  @DisplayName("checkDocumentDiff: DBに変更が無ければ差分なし（実行日が変わっても差分にならない）")
  void testCheckSnapshotDiffNoDifference() {
    setUp();
    repository.tables.add(table("public", "t1"));
    repository.columns.add(EntityFixtures.column("public", "t1", "id", "int", true));
    exportCommitted();

    // 実行日（作成日）が変わった状態で比較する
    checkUsecase = checkUsecaseAt.apply(LocalDate.of(2027, 1, 1));
    final DiffResult result = checkSnapshotDiff();

    assertFalse(result.hasDifference());
  }

  @Test
  @DisplayName("checkDocumentDiff: テーブルの追加・削除・変更をテーブル単位で報告する")
  void testCheckSnapshotDiffReportsTableLevelDifferences() {
    setUp();
    repository.tables.add(table("public", "changed"));
    repository.tables.add(table("public", "dropped"));
    repository.columns.add(EntityFixtures.column("public", "changed", "id", "int", true));
    exportCommitted();

    repository.tables.clear();
    repository.tables.add(table("public", "added"));
    repository.tables.add(table("public", "changed"));
    repository.columns.add(EntityFixtures.column("public", "changed", "name", "text", false));
    final DiffResult result = checkSnapshotDiff();

    assertEquals(List.of("table public.added"), result.onlyInGenerated());
    assertEquals(List.of("table public.dropped"), result.onlyInCommitted());
    assertEquals(
        List.of("table public.changed"),
        result.contentDiffer().stream().map(ContentDiff::target).toList());
    // unified diffの本体にも、追加された列の内容が現れる
    assertTrue(
        result.contentDiffer().get(0).unifiedDiff().stream()
            .anyMatch(line -> line.contains("\"name\":\"name\"")));
  }

  @Test
  @DisplayName("checkDocumentDiff: Markdownのみの差分（手修正・削除等）は検知しない")
  void testCheckSnapshotDiffIgnoresMarkdown() {
    setUp();
    repository.tables.add(table("public", "t1"));
    exportCommitted();
    fileRepository.files.remove(tableDefFile(Paths.get("committed"), "public", "t1"));

    assertFalse(checkSnapshotDiff().hasDifference());
  }

  @Test
  @DisplayName("サイドカーの観点が、観点ページ・観点一覧・テーブル一覧の関連ドキュメント・所属テーブルの定義書に反映される")
  void testViewpointsAreExported() {
    setUp();
    repository.tables.add(table("public", "orders"));
    repository.tables.add(table("public", "stock"));
    viewpoints =
        Viewpoints.of(List.of(Viewpoint.of("order", "受注管理", "", List.of("public.orders"))));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), "conf/annotations.yml"),
            null,
            0,
            80,
            false));

    assertTrue(fileExists(DEFAULT_OUT.resolve("viewpoint_testdb_order.md")));
    assertTrue(fileExists(DEFAULT_OUT.resolve("viewpointList_testdb.md")));
    assertTrue(
        contentOf(DEFAULT_OUT.resolve("tableList_testdb.md"))
            .contains("* [観点一覧](./viewpointList_testdb.md)"));
    assertTrue(
        contentOf(tableDefFile(DEFAULT_OUT, "public", "orders"))
            .contains("* [受注管理](../../../viewpoint_testdb_order.md)"));
    // 所属しないテーブルの定義書には、所属する観点のセクションを出力しない
    assertFalse(contentOf(tableDefFile(DEFAULT_OUT, "public", "stock")).contains("## 所属する観点"));
  }

  @Test
  @DisplayName("観点を宣言していない場合は、観点ページ・観点一覧を出力せず、テーブル一覧からもリンクしない")
  void testViewpointsAreNotExportedWhenNotDeclared() {
    setUp();
    repository.tables.add(table("public", "orders"));

    usecase.exportTableDefinition(
        new ExportRequest(
            TargetSelection.of(List.of(), List.of(), List.of(), null), null, 0, 80, false));

    assertTrue(
        fileRepository.files.keySet().stream()
            .noneMatch(path -> path.getFileName().toString().startsWith("viewpoint")));
    assertFalse(contentOf(DEFAULT_OUT.resolve("tableList_testdb.md")).contains("観点一覧"));
    assertFalse(contentOf(tableDefFile(DEFAULT_OUT, "public", "orders")).contains("## 所属する観点"));
  }
}
