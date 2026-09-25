package com.export_table_definition.infrastructure.path;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.snapshot.SnapshotKind;
import com.export_table_definition.domain.model.type.ListDocumentType;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DefaultOutputPathResolver の各種ファイルパス組み立てに関するテスト */
public class DefaultOutputPathResolverTest {

  private final DefaultOutputPathResolver resolver = new DefaultOutputPathResolver();
  private final Path baseDir = Path.of("output");
  private final BaseInfoEntity baseInfo = new BaseInfoEntity("testdb", "unused", "unused");

  private TableEntity table(String schema, String physical, String tableType) {
    return new TableEntity("testdb", schema, "", physical, tableType, "", "");
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
  @DisplayName("resolveTableDefinitionDirectory: {base}/{DB名}/{スキーマ名}/{テーブル種別}")
  void testResolveTableDefinitionDirectory() {
    Path result =
        resolver.resolveTableDefinitionDirectory(
            baseInfo, table("public", "orders", "table"), baseDir);
    assertEquals(Path.of("output", "testdb", "public", "table"), result);
  }

  @Test
  @DisplayName("resolveTableDefinitionFile: ディレクトリ配下に{物理テーブル名}.md")
  void testResolveTableDefinitionFile() {
    Path result =
        resolver.resolveTableDefinitionFile(baseInfo, table("public", "orders", "view"), baseDir);
    assertEquals(Path.of("output", "testdb", "public", "view", "orders.md"), result);
  }

  @Test
  @DisplayName("resolveListFile: テーブル一覧は{base}/tableList_{DB名}.md")
  void testResolveListFileForTable() {
    Path result = resolver.resolveListFile(baseInfo, baseDir, ListDocumentType.TABLE);
    assertEquals(Path.of("output", "tableList_testdb.md"), result);
  }

  @Test
  @DisplayName("resolveListFile: オブジェクト一覧・ER図一覧は{base}/{接頭辞}List_{DB名}.md")
  void testResolveListFileForObjects() {
    assertEquals(
        Path.of("output", "triggerList_testdb.md"),
        resolver.resolveListFile(baseInfo, baseDir, ListDocumentType.TRIGGER));
    assertEquals(
        Path.of("output", "erDiagramList_testdb.md"),
        resolver.resolveListFile(baseInfo, baseDir, ListDocumentType.ER_DIAGRAM));
  }

  @Test
  @DisplayName("resolveErDiagramFile: {base}/erDiagram_{DB名}_{スキーマ名}.md")
  void testResolveErDiagramFile() {
    Path result = resolver.resolveErDiagramFile(baseInfo, baseDir, "public");
    assertEquals(Path.of("output", "erDiagram_testdb_public.md"), result);
  }

  @Test
  @DisplayName("resolveErDiagramGroupFile: {base}/erDiagram_{DB名}_{スキーマ名}_group{グループ番号}.md")
  void testResolveErDiagramGroupFile() {
    Path result = resolver.resolveErDiagramGroupFile(baseInfo, baseDir, "public", 1);
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
        resolver.resolvePageFile(
            resolver.resolveListFile(baseInfo, baseDir, ListDocumentType.FUNCTION), 3));
    assertEquals(
        Path.of("output", "erDiagram_testdb_public_4.md"),
        resolver.resolvePageFile(resolver.resolveErDiagramFile(baseInfo, baseDir, "public"), 4));
    assertEquals(
        Path.of("output", "erDiagram_testdb_public_group1_2.md"),
        resolver.resolvePageFile(
            resolver.resolveErDiagramGroupFile(baseInfo, baseDir, "public", 1), 2));
  }

  @Test
  @DisplayName("resolveSchemaObjectDirectory: {base}/{DB名}/{スキーマ名}/{種別}")
  void testResolveSchemaObjectDirectory() {
    Path result =
        resolver.resolveSchemaObjectDirectory(
            baseInfo, baseDir, "public", ListDocumentType.FUNCTION);
    assertEquals(Path.of("output", "testdb", "public", "function"), result);
  }

  @Test
  @DisplayName("resolveSchemaObjectFile: ディレクトリ配下に{名前}.md")
  void testResolveSchemaObjectFile() {
    Path result =
        resolver.resolveSchemaObjectFile(
            baseInfo, baseDir, "public", ListDocumentType.SEQUENCE, "seq1");
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
        resolver.resolveSnapshotDatabaseFile(baseInfo, baseDir));
  }

  @Test
  @DisplayName("resolveSnapshotFile: {base}/snapshot/{DB名}/{スキーマ名}/{種別のファイル名}.jsonl")
  void testResolveSnapshotFile() {
    assertEquals(
        Path.of("output", "snapshot", "testdb", "public", "tables.jsonl"),
        resolver.resolveSnapshotFile(baseInfo, baseDir, "public", SnapshotKind.TABLE));
    assertEquals(
        Path.of("output", "snapshot", "testdb", "public", "functions.jsonl"),
        resolver.resolveSnapshotFile(baseInfo, baseDir, "public", SnapshotKind.FUNCTION));
  }

  @Test
  @DisplayName("resolveSnapshotKind: resolveSnapshotFileで解決したファイルから種別を判定し、それ以外は空を返す")
  void testResolveSnapshotKind() {
    for (SnapshotKind kind : SnapshotKind.values()) {
      assertEquals(
          Optional.of(kind),
          resolver.resolveSnapshotKind(
              resolver.resolveSnapshotFile(baseInfo, baseDir, "public", kind)));
    }
    assertEquals(
        Optional.empty(),
        resolver.resolveSnapshotKind(resolver.resolveSnapshotDatabaseFile(baseInfo, baseDir)));
    assertEquals(Optional.empty(), resolver.resolveSnapshotKind(Path.of("README.md")));
  }

  @Test
  @DisplayName("スキーマ名・テーブル種別が異なれば別ディレクトリになる（衝突しない）")
  void testDifferentSchemasProduceDifferentDirectories() {
    Path publicDir =
        resolver.resolveTableDefinitionDirectory(
            baseInfo, table("public", "orders", "table"), baseDir);
    Path salesDir =
        resolver.resolveTableDefinitionDirectory(
            baseInfo, table("sales", "orders", "table"), baseDir);
    assertNotEquals(publicDir, salesDir);
  }
}
