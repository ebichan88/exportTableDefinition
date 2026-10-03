package com.export_table_definition.mcp.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.UserCorrectableException;
import com.export_table_definition.mcp.catalog.ObjectKey;
import com.export_table_definition.mcp.catalog.RelationEntry;
import com.export_table_definition.mcp.catalog.SchemaCatalog;
import com.export_table_definition.mcp.catalog.TableEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link SnapshotDirectoryReader}のテスト */
class SnapshotDirectoryReaderTest {

  private static final String DATABASE_JSON =
      "{\"formatVersion\":1,\"name\":\"testdb\",\"dbms\":\"PostgreSQL\"}";

  @TempDir Path snapshot;

  private final SnapshotDirectoryReader reader = new SnapshotDirectoryReader();

  @Test
  @DisplayName("DB・スキーマごとのtables.jsonlを読み込み、1行をそのまま保持する")
  void readsTablesOfAllDatabasesAndSchemas() throws IOException {
    final String order =
        "{\"schema\":\"sales\",\"name\":\"orders\",\"type\":\"table\",\"logicalName\":\"受注\","
            + "\"columns\":[{\"name\":\"customer_id\",\"type\":\"integer\",\"remarks\":\"顧客\"}],"
            + "\"foreignKeys\":[{\"name\":\"fk\",\"columns\":[\"customer_id\"],\"referenceSchema\":\"sales\","
            + "\"referenceTable\":\"customer\",\"referenceColumns\":[\"id\"],\"cardinality\":\"ONE_TO_MANY\"}]}";
    write("testdb/database.json", DATABASE_JSON);
    write("testdb/sales/tables.jsonl", order + "\n\n");
    write(
        "testdb/hr/tables.jsonl", "{\"schema\":\"hr\",\"name\":\"employee\",\"type\":\"table\"}\n");
    write("otherdb/database.json", "{\"formatVersion\":1,\"name\":\"otherdb\"}");
    write(
        "otherdb/public/tables.jsonl",
        "{\"schema\":\"public\",\"name\":\"item\",\"type\":\"view\"}\n");

    final SchemaCatalog catalog = reader.read(snapshot);

    assertEquals(
        List.of(
            new ObjectKey("otherdb", "public", "item"),
            new ObjectKey("testdb", "hr", "employee"),
            new ObjectKey("testdb", "sales", "orders")),
        catalog.tables().stream().map(TableEntry::key).toList());
    final TableEntry orders = catalog.tables().get(2);
    assertEquals("受注", orders.logicalName());
    assertEquals("顧客", orders.columns().get(0).remarks());
    assertEquals(
        new RelationEntry(
            "fk", List.of("customer_id"), "sales", "customer", List.of("id"), "ONE_TO_MANY"),
        orders.foreignKeys().get(0));
    assertEquals(order, orders.json());
  }

  @Test
  @DisplayName("未知の項目は無視する（cliが項目を追加しても読み込める）")
  void ignoresUnknownFields() throws IOException {
    write(
        "testdb/database.json", "{\"formatVersion\":1,\"name\":\"testdb\",\"generatedBy\":\"x\"}");
    write(
        "testdb/sample/tables.jsonl",
        "{\"schema\":\"sample\",\"name\":\"t\",\"newField\":{\"a\":1},"
            + "\"columns\":[{\"name\":\"c\",\"newColumnField\":true}]}\n");

    final SchemaCatalog catalog = reader.read(snapshot);

    assertEquals("c", catalog.tables().get(0).columns().get(0).name());
  }

  @Test
  @DisplayName("tables.jsonlの無いスキーマ・database.jsonの無いディレクトリは読み飛ばす")
  void skipsDirectoriesWithoutSnapshot() throws IOException {
    write("testdb/database.json", DATABASE_JSON);
    write("testdb/sample/functions.jsonl", "{\"schema\":\"sample\",\"name\":\"f\"}\n");
    write("notadb/sample/tables.jsonl", "{\"schema\":\"sample\",\"name\":\"t\"}\n");

    assertTrue(reader.read(snapshot).tables().isEmpty());
  }

  @Test
  @DisplayName("ディレクトリが無い場合は、snapshotディレクトリを指定するよう伝える")
  void failsWhenDirectoryIsMissing() {
    final UserCorrectableException e =
        assertThrows(UserCorrectableException.class, () -> reader.read(snapshot.resolve("none")));

    assertTrue(e.getMessage().contains("snapshotディレクトリを指定してください"), e.getMessage());
  }

  @Test
  @DisplayName("database.jsonが1つも無い場合（outputPathそのものを指定した等）は、snapshotディレクトリを指定するよう伝える")
  void failsWhenNoDatabaseFound() throws IOException {
    write("testdb/sample/table/employee.md", "# employee");

    final UserCorrectableException e =
        assertThrows(UserCorrectableException.class, () -> reader.read(snapshot));

    assertTrue(e.getMessage().contains("database.jsonがありません"), e.getMessage());
  }

  @Test
  @DisplayName("対応していない新しい形式のバージョンの場合は、MCPサーバーの更新を促す")
  void failsOnNewerFormatVersion() throws IOException {
    write("testdb/database.json", "{\"formatVersion\":2,\"name\":\"testdb\"}");

    final UserCorrectableException e =
        assertThrows(UserCorrectableException.class, () -> reader.read(snapshot));

    assertTrue(e.getMessage().contains("formatVersion=2"), e.getMessage());
  }

  @Test
  @DisplayName("database.jsonにformatVersion・nameが無い場合は失敗にする")
  void failsWhenDatabaseInfoIsIncomplete() throws IOException {
    write("testdb/database.json", "{\"name\":\"testdb\"}");

    assertThrows(UserCorrectableException.class, () -> reader.read(snapshot));
  }

  @Test
  @DisplayName("JSONとして読めない行がある場合は、ファイルと行番号を示して失敗にする")
  void failsOnBrokenLineWithLineNumber() throws IOException {
    write("testdb/database.json", DATABASE_JSON);
    final Path tables =
        write(
            "testdb/sample/tables.jsonl",
            "{\"schema\":\"sample\",\"name\":\"a\"}\n<<<<<<< HEAD\n{\"schema\":\"sample\",\"name\":\"b\"}\n");

    final UserCorrectableException e =
        assertThrows(UserCorrectableException.class, () -> reader.read(snapshot));

    assertTrue(e.getMessage().contains("file=" + tables + ", line=2"), e.getMessage());
  }

  @Test
  @DisplayName("schema・nameの無い行がある場合は、ファイルと行番号を示して失敗にする")
  void failsOnLineWithoutName() throws IOException {
    write("testdb/database.json", DATABASE_JSON);
    final Path tables = write("testdb/sample/tables.jsonl", "{\"schema\":\"sample\"}\n");

    final UserCorrectableException e =
        assertThrows(UserCorrectableException.class, () -> reader.read(snapshot));

    assertTrue(e.getMessage().contains("file=" + tables + ", line=1"), e.getMessage());
  }

  private Path write(String relativePath, String content) throws IOException {
    final Path file = snapshot.resolve(relativePath);
    Files.createDirectories(file.getParent());
    return Files.writeString(file, content);
  }
}
