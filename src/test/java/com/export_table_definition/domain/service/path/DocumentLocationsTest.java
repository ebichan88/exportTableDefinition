package com.export_table_definition.domain.service.path;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.type.ListDocumentType;
import com.export_table_definition.domain.model.type.TableType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DocumentLocations のファイル名・相対パス・相対リンクの組み立てに関するテスト */
public class DocumentLocationsTest {

  @Test
  @DisplayName("listFile: {接頭辞}List_{DB名}.md")
  void testListFile() {
    assertEquals(
        "tableList_testdb.md", DocumentLocations.listFile(ListDocumentType.TABLE, "testdb"));
    assertEquals(
        "erDiagramList_testdb.md",
        DocumentLocations.listFile(ListDocumentType.ER_DIAGRAM, "testdb"));
  }

  @Test
  @DisplayName("erDiagramFile/erDiagramGroupFile: スキーマ別・グループ別ER図のファイル名")
  void testErDiagramFiles() {
    assertEquals("erDiagram_testdb_public.md", DocumentLocations.erDiagramFile("testdb", "public"));
    assertEquals(
        "erDiagram_testdb_public_group2.md",
        DocumentLocations.erDiagramGroupFile("testdb", "public", 2));
  }

  @Test
  @DisplayName("pageFile: 拡張子の前に_{ページ番号}を付ける")
  void testPageFile() {
    assertEquals("tableList_testdb_3.md", DocumentLocations.pageFile("tableList_testdb.md", 3));
  }

  @Test
  @DisplayName("pageFile: Markdownの拡張子で終わらないファイル名はIllegalArgumentExceptionをスローする")
  void testPageFileRejectsNonMarkdown() {
    assertThrows(
        IllegalArgumentException.class, () -> DocumentLocations.pageFile("tables.jsonl", 1));
  }

  @Test
  @DisplayName("tableDefinitionFile/schemaObjectFile: {DB名}/{スキーマ名}/{区分}/{名前}.md")
  void testDefinitionFiles() {
    var table = new TableEntity("testdb", "public", "", "orders", TableType.VIEW, "");
    assertEquals(
        "testdb/public/view/orders.md", DocumentLocations.tableDefinitionFile("testdb", table));
    assertEquals(
        "testdb/public/function",
        DocumentLocations.schemaObjectDirectory("testdb", "public", ListDocumentType.FUNCTION));
    assertEquals(
        "testdb/public/type/status.md",
        DocumentLocations.schemaObjectFile("testdb", "public", ListDocumentType.TYPE, "status"));
  }

  @Test
  @DisplayName("linkFromBase/linkFromDefinition: 参照元の配置に応じた相対リンク")
  void testLinks() {
    assertEquals("./tableList_testdb.md", DocumentLocations.linkFromBase("tableList_testdb.md"));
    assertEquals(
        "../../../tableList_testdb.md",
        DocumentLocations.linkFromDefinition("tableList_testdb.md"));
  }
}
