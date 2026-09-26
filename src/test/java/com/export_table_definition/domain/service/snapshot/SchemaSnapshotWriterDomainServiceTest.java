package com.export_table_definition.domain.service.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.model.sidecar.TableAnnotation;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableType;
import com.export_table_definition.domain.model.target.TableDefinitionContent;
import com.export_table_definition.domain.repository.FileRepository;
import com.export_table_definition.domain.service.path.OutputRoot;
import com.export_table_definition.infrastructure.path.DefaultOutputPathResolver;
import com.export_table_definition.infrastructure.snapshot.JacksonSnapshotSerializer;
import com.export_table_definition.testsupport.EntityFixtures;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SchemaSnapshotWriterDomainService のスナップショット（JSON Lines）書き込みに関するテスト */
public class SchemaSnapshotWriterDomainServiceTest {

  private static final Path OUT = Path.of("output");
  private static final Path SNAPSHOT_DIR = OUT.resolve("snapshot").resolve("testdb");
  private static final BaseInfoEntity BASE_INFO =
      new BaseInfoEntity("testdb", "PostgreSQL", LocalDate.of(2026, 9, 25));
  private static final OutputRoot ROOT = new OutputRoot(OUT, BASE_INFO);

  /** 書き込み内容・ディレクトリ作成呼び出しをメモリ上に収集するFileRepositoryのスタブ */
  private static class InMemoryFileRepository implements FileRepository {
    private final Map<Path, String> files = new LinkedHashMap<>();
    private final List<Path> createdDirectories = new ArrayList<>();

    @Override
    public void writeFile(Path filePath, List<String> contents) {
      files.put(filePath, String.join("", contents));
    }

    @Override
    public void appendFile(Path filePath, List<String> contents) {
      files.merge(filePath, String.join("", contents), String::concat);
    }

    @Override
    public void createDirectory(Path filePath) {
      createdDirectories.add(filePath);
    }

    @Override
    public List<Path> listFiles(Path directory) {
      return List.of();
    }

    @Override
    public List<String> readFile(Path filePath) {
      return List.of();
    }

    @Override
    public Path createTempDirectory(String prefix) {
      return Path.of(prefix);
    }

    @Override
    public void deleteDirectory(Path directory) {
      // 何もしない
    }
  }

  private InMemoryFileRepository fileRepository;
  private SchemaSnapshotWriterDomainService writer;

  @BeforeEach
  void setUp() {
    fileRepository = new InMemoryFileRepository();
    writer =
        new SchemaSnapshotWriterDomainService(
            fileRepository, new DefaultOutputPathResolver(), new JacksonSnapshotSerializer());
  }

  private TableDefinitionContent tableContent(String schema, String table, String column) {
    return new TableDefinitionContent(
        BASE_INFO,
        new TableEntity("testdb", schema, "", table, TableType.TABLE, ""),
        List.of(EntityFixtures.column(schema, table, column, "integer", true)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        TableAnnotation.EMPTY);
  }

  @Test
  @DisplayName("writeDatabase: DB名・DBMS種別・形式バージョンを出力し、生成日は含めない")
  void testWriteDatabaseExcludesGeneratedDate() {
    writer.writeDatabase(ROOT);

    Path file = SNAPSHOT_DIR.resolve("database.json");
    assertEquals(
        "{\"formatVersion\":1,\"name\":\"testdb\",\"dbms\":\"PostgreSQL\"}\n",
        fileRepository.files.get(file));
    assertTrue(fileRepository.createdDirectories.contains(SNAPSHOT_DIR));
  }

  @Test
  @DisplayName("initTableFile + appendTable: スキーマ単位のファイルへ1テーブル1行で追記する")
  void testAppendTableWritesOneLinePerTable() {
    writer.initTableFile("public", ROOT);
    writer.appendTable(tableContent("public", "t1", "id"), OUT);
    writer.appendTable(tableContent("public", "t2", "code"), OUT);

    Path file = SNAPSHOT_DIR.resolve("public").resolve("tables.jsonl");
    List<String> lines = fileRepository.files.get(file).lines().toList();
    assertEquals(2, lines.size());
    assertTrue(
        lines.get(0).startsWith("{\"schema\":\"public\",\"name\":\"t1\",\"type\":\"table\""));
    assertTrue(lines.get(0).contains("{\"name\":\"id\",\"type\":\"integer\",\"primaryKey\":true"));
    assertTrue(lines.get(1).startsWith("{\"schema\":\"public\",\"name\":\"t2\""));
    assertTrue(fileRepository.files.get(file).endsWith("\n"));
  }

  @Test
  @DisplayName("initTableFile: 前回実行時の内容が残っていても空にしてから追記する")
  void testInitTableFileTruncatesStaleContent() {
    Path file = SNAPSHOT_DIR.resolve("public").resolve("tables.jsonl");
    fileRepository.files.put(file, "{\"stale\":true}\n");

    writer.initTableFile("public", ROOT);
    writer.appendTable(tableContent("public", "t1", "id"), OUT);

    assertFalse(fileRepository.files.get(file).contains("stale"));
    assertEquals(1, fileRepository.files.get(file).lines().count());
  }

  @Test
  @DisplayName("writeSequences/writeTypes: スキーマごとのファイルへ分けて出力し、対象の無いスキーマは出力しない")
  void testWriteSequencesAndTypesBySchema() {
    writer.writeSequences(
        List.of(
            new SequenceEntity("testdb", "public", "seq_a", "1", "1", "100", "1", "1", true, ""),
            new SequenceEntity("testdb", "sales", "seq_b", "1", "1", "100", "1", "1", false, "")),
        ROOT);
    writer.writeTypes(List.of(new TypeEntity("testdb", "public", "mood", "ENUM", "sad, ok")), ROOT);

    assertEquals(
        "{\"schema\":\"public\",\"name\":\"seq_a\",\"incrementBy\":\"1\",\"minValue\":\"1\","
            + "\"maxValue\":\"100\",\"cacheSize\":\"1\",\"startValue\":\"1\",\"cycle\":true}\n",
        fileRepository.files.get(SNAPSHOT_DIR.resolve("public").resolve("sequences.jsonl")));
    assertTrue(
        fileRepository
            .files
            .get(SNAPSHOT_DIR.resolve("sales").resolve("sequences.jsonl"))
            .contains("\"name\":\"seq_b\""));
    assertEquals(
        "{\"schema\":\"public\",\"name\":\"mood\",\"category\":\"ENUM\",\"definition\":\"sad, ok\"}\n",
        fileRepository.files.get(SNAPSHOT_DIR.resolve("public").resolve("types.jsonl")));
    assertFalse(
        fileRepository.files.containsKey(SNAPSHOT_DIR.resolve("sales").resolve("types.jsonl")));
  }

  @Test
  @DisplayName("writeSequences: 対象が0件の場合は何も出力しない")
  void testWriteSequencesEmptyWritesNothing() {
    writer.writeSequences(List.of(), ROOT);
    assertTrue(fileRepository.files.isEmpty());
  }

  @Test
  @DisplayName("writeFunctions: 定義本体を含めて1関数1行で出力し、Markdown都合の個別定義ファイル名は含めない")
  void testWriteFunctions() {
    writer.writeFunctions(
        "public",
        List.of(
            new FunctionEntity(
                "testdb",
                "public",
                "calc",
                1,
                1,
                "FUNCTION",
                "x integer",
                "integer",
                "sql",
                "CREATE FUNCTION public.calc(x integer)\n RETURNS integer ..."),
            new FunctionEntity(
                "testdb", "public", "calc", 2, 2, "PROCEDURE", "", "", "plpgsql", "BODY")),
        ROOT);

    List<String> lines =
        fileRepository
            .files
            .get(SNAPSHOT_DIR.resolve("public").resolve("functions.jsonl"))
            .lines()
            .toList();
    assertEquals(
        List.of(
            "{\"schema\":\"public\",\"name\":\"calc\",\"kind\":\"FUNCTION\",\"arguments\":\"x integer\","
                + "\"result\":\"integer\",\"language\":\"sql\","
                + "\"definition\":\"CREATE FUNCTION public.calc(x integer)\\n RETURNS integer ...\"}",
            "{\"schema\":\"public\",\"name\":\"calc\",\"kind\":\"PROCEDURE\",\"language\":\"plpgsql\","
                + "\"definition\":\"BODY\"}"),
        lines);
  }
}
