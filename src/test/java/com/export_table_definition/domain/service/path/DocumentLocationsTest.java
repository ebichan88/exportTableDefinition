package com.export_table_definition.domain.service.path;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableType;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import java.util.List;
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
  @DisplayName("functionDefinitionName: オーバーロードが無い関数は関数名をそのまま用いる")
  void testFunctionDefinitionNameWithoutOverload() {
    var function =
        new FunctionEntity("testdb", "public", "calc", 1, 1, "FUNCTION", "", "int", "sql", "");
    assertEquals("calc", DocumentLocations.functionDefinitionName(function));
  }

  @Test
  @DisplayName("functionDefinitionName: オーバーロードされた関数は作成順の番号を付ける")
  void testFunctionDefinitionNameWithOverload() {
    var first =
        new FunctionEntity("testdb", "public", "calc", 1, 2, "FUNCTION", "a int", "int", "sql", "");
    var second =
        new FunctionEntity(
            "testdb", "public", "calc", 2, 2, "FUNCTION", "a text", "int", "sql", "");
    assertEquals("calc_1", DocumentLocations.functionDefinitionName(first));
    assertEquals("calc_2", DocumentLocations.functionDefinitionName(second));
  }

  @Test
  @DisplayName("linkFromBase/linkFromDefinition: 参照元の配置に応じた相対リンク")
  void testLinks() {
    assertEquals("./tableList_testdb.md", DocumentLocations.linkFromBase("tableList_testdb.md"));
    assertEquals(
        "../../../tableList_testdb.md",
        DocumentLocations.linkFromDefinition("tableList_testdb.md"));
  }

  @Test
  @DisplayName("viewpointFile: 観点ページのファイル名には、表示名ではなく識別子を用いる")
  void testViewpointFile() {
    assertEquals(
        "viewpoint_testdb_order.md",
        DocumentLocations.viewpointFile(
            "testdb", Viewpoint.of("order", "受注 管理", "", List.of("orders"))));
  }
}
