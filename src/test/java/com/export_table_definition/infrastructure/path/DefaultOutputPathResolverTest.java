package com.export_table_definition.infrastructure.path;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.snapshot.SnapshotKind;
import com.export_table_definition.domain.model.type.ListDocumentType;
import com.export_table_definition.domain.model.type.TableType;
import com.export_table_definition.domain.service.path.OutputRoot;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DefaultOutputPathResolver の各種ファイルパス組み立てに関するテスト */
public class DefaultOutputPathResolverTest {

  private final DefaultOutputPathResolver resolver = new DefaultOutputPathResolver();
  private final Path baseDir = Path.of("output");
  private final BaseInfoEntity baseInfo = new BaseInfoEntity("testdb", "unused", "unused");
  private final OutputRoot root = new OutputRoot(baseDir, baseInfo);

  private TableEntity table(String schema, String physical, String tableType) {
    return new TableEntity("testdb", schema, "", physical, TableType.findByName(tableType), "");
  }

  @Test
  @DisplayName("resolveBaseOutputDir: 指定したパスをそのまま返す")
  void testResolveBaseOutputDirUsesSpecifiedPath() {
    Path result = resolver.resolveBaseOutputDir("custom_out");
    assertEquals(Path.of("custom_out"), result);
  }

  @Test
  @DisplayName("resolveBaseOutputDir: nullの場合は./outputへフォールバックする")
  void testResolveBaseOutputDirFallbackWhenNull() {
    Path result = resolver.resolveBaseOutputDir(null);
    assertEquals(Path.of("./output"), result);
  }

  @Test
  @DisplayName("resolveBaseOutputDir: 空白のみの場合も./outputへフォールバックする")
  void testResolveBaseOutputDirFallbackWhenWhitespace() {
    Path result = resolver.resolveBaseOutputDir("   ");
    assertEquals(Path.of("./output"), result);
  }

  @Test
  @DisplayName("isRemovableOutputDir: カレントディレクトリ配下のサブディレクトリは削除を認める")
  void testIsRemovableOutputDirAllowsSubdirectory() {
    assertTrue(resolver.isRemovableOutputDir(Path.of("output")));
  }

  @Test
  @DisplayName("isRemovableOutputDir: ルート・ホーム・カレントディレクトリ自体は削除を認めない")
  void testIsRemovableOutputDirRejectsUnsafeDirectories() {
    assertFalse(resolver.isRemovableOutputDir(Path.of(".")));
    assertFalse(resolver.isRemovableOutputDir(Path.of("").toAbsolutePath().getRoot()));
    assertFalse(resolver.isRemovableOutputDir(Path.of(System.getProperty("user.home"))));
  }

  @Test
  @DisplayName("resolveTableDefinitionDirectory: {base}/{DB名}/{スキーマ名}/{テーブル種別}")
  void testResolveTableDefinitionDirectory() {
    Path result =
        resolver.resolveTableDefinitionDirectory(root, table("public", "orders", "table"));
    assertEquals(Path.of("output", "testdb", "public", "table"), result);
  }

  @Test
  @DisplayName("resolveTableDefinitionFile: ディレクトリ配下に{物理テーブル名}.md")
  void testResolveTableDefinitionFile() {
    Path result = resolver.resolveTableDefinitionFile(root, table("public", "orders", "view"));
    assertEquals(Path.of("output", "testdb", "public", "view", "orders.md"), result);
  }

  @Test
  @DisplayName("resolveListFile: テーブル一覧は{base}/tableList_{DB名}.md")
  void testResolveListFileForTable() {
    Path result = resolver.resolveListFile(root, ListDocumentType.TABLE);
    assertEquals(Path.of("output", "tableList_testdb.md"), result);
  }

  @Test
  @DisplayName("resolveListFile: オブジェクト一覧・ER図一覧は{base}/{接頭辞}List_{DB名}.md")
  void testResolveListFileForObjects() {
    assertEquals(
        Path.of("output", "triggerList_testdb.md"),
        resolver.resolveListFile(root, ListDocumentType.TRIGGER));
    assertEquals(
        Path.of("output", "erDiagramList_testdb.md"),
        resolver.resolveListFile(root, ListDocumentType.ER_DIAGRAM));
  }

  @Test
  @DisplayName("resolveErDiagramFile: {base}/erDiagram_{DB名}_{スキーマ名}.md")
  void testResolveErDiagramFile() {
    Path result = resolver.resolveErDiagramFile(root, "public");
    assertEquals(Path.of("output", "erDiagram_testdb_public.md"), result);
  }

  @Test
  @DisplayName("resolveErDiagramGroupFile: {base}/erDiagram_{DB名}_{スキーマ名}_group{グループ番号}.md")
  void testResolveErDiagramGroupFile() {
    Path result = resolver.resolveErDiagramGroupFile(root, "public", 1);
    assertEquals(Path.of("output", "erDiagram_testdb_public_group1.md"), result);
  }

  @Test
  @DisplayName("resolvePageFile: 本体ページと同じディレクトリに、拡張子の前へ_{ページ番号}を付けたファイル")
  void testResolvePageFile() {
    assertEquals(
        Path.of("output", "tableList_testdb_2.md"),
        resolver.resolvePageFile(Path.of("output", "tableList_testdb.md"), 2));
    assertEquals(
        Path.of("output", "functionList_testdb_3.md"),
        resolver.resolvePageFile(resolver.resolveListFile(root, ListDocumentType.FUNCTION), 3));
    assertEquals(
        Path.of("output", "erDiagram_testdb_public_4.md"),
        resolver.resolvePageFile(resolver.resolveErDiagramFile(root, "public"), 4));
    assertEquals(
        Path.of("output", "erDiagram_testdb_public_group1_2.md"),
        resolver.resolvePageFile(resolver.resolveErDiagramGroupFile(root, "public", 1), 2));
  }

  @Test
  @DisplayName("resolveSchemaObjectDirectory: {base}/{DB名}/{スキーマ名}/{種別}")
  void testResolveSchemaObjectDirectory() {
    Path result = resolver.resolveSchemaObjectDirectory(root, "public", ListDocumentType.FUNCTION);
    assertEquals(Path.of("output", "testdb", "public", "function"), result);
  }

  @Test
  @DisplayName("resolveSchemaObjectFile: ディレクトリ配下に{名前}.md")
  void testResolveSchemaObjectFile() {
    Path result =
        resolver.resolveSchemaObjectFile(root, "public", ListDocumentType.SEQUENCE, "seq1");
    assertEquals(Path.of("output", "testdb", "public", "sequence", "seq1.md"), result);
  }

  @Test
  @DisplayName("resolveSnapshotDirectory: {base}/snapshot")
  void testResolveSnapshotDirectory() {
    assertEquals(Path.of("output", "snapshot"), resolver.resolveSnapshotDirectory(baseDir));
  }

  @Test
  @DisplayName("resolveSnapshotDatabaseFile: {base}/snapshot/{DB名}/database.json")
  void testResolveSnapshotDatabaseFile() {
    assertEquals(
        Path.of("output", "snapshot", "testdb", "database.json"),
        resolver.resolveSnapshotDatabaseFile(root));
  }

  @Test
  @DisplayName("resolveSnapshotFile: {base}/snapshot/{DB名}/{スキーマ名}/{種別のファイル名}.jsonl")
  void testResolveSnapshotFile() {
    assertEquals(
        Path.of("output", "snapshot", "testdb", "public", "tables.jsonl"),
        resolver.resolveSnapshotFile(root, "public", SnapshotKind.TABLE));
    assertEquals(
        Path.of("output", "snapshot", "testdb", "public", "functions.jsonl"),
        resolver.resolveSnapshotFile(root, "public", SnapshotKind.FUNCTION));
  }

  @Test
  @DisplayName("resolveSnapshotKind: resolveSnapshotFileで解決したファイルから種別を判定し、それ以外は空を返す")
  void testResolveSnapshotKind() {
    for (SnapshotKind kind : SnapshotKind.values()) {
      assertEquals(
          Optional.of(kind),
          resolver.resolveSnapshotKind(resolver.resolveSnapshotFile(root, "public", kind)));
    }
    assertEquals(
        Optional.empty(), resolver.resolveSnapshotKind(resolver.resolveSnapshotDatabaseFile(root)));
    assertEquals(Optional.empty(), resolver.resolveSnapshotKind(Path.of("README.md")));
  }

  @Test
  @DisplayName("スキーマ名・テーブル種別が異なれば別ディレクトリになる（衝突しない）")
  void testDifferentSchemasProduceDifferentDirectories() {
    Path publicDir =
        resolver.resolveTableDefinitionDirectory(root, table("public", "orders", "table"));
    Path salesDir =
        resolver.resolveTableDefinitionDirectory(root, table("sales", "orders", "table"));
    assertNotEquals(publicDir, salesDir);
  }
}
