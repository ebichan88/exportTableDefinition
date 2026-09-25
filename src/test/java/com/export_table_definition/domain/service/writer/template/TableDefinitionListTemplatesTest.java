package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.testsupport.MarkdownAssert;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TableDefinitionListTemplates の詳細テスト */
public class TableDefinitionListTemplatesTest {

  private static final String NL = System.lineSeparator();
  private static final String NL2 = NL + NL;

  private BaseInfoEntity baseInfo() {
    return new BaseInfoEntity("TEST_DB", "pg", "2025-01-01");
  }

  private TableEntity newEntity(String schema, String physical, String logical, String remarks) {
    return new TableEntity("TEST_DB", schema, logical, physical, "table", remarks, "");
  }

  @Test
  @DisplayName("fileHeader: 期待値と完全一致 (末尾はダブル改行)")
  void fileHeader_fullMatch() {
    String expected = "# テーブル一覧（DB名：TEST_DB）" + NL2;
    MarkdownAssert.assertMarkdownEquals(
        expected, TableDefinitionListTemplates.fileHeader(baseInfo()));
  }

  @Test
  @DisplayName("baseInfo: テーブル（基本情報）Markdown 全体一致")
  void baseInfo_fullMatch() {
    String expected =
        "## 基本情報"
            + NL
            + NL
            + "| RDBMS | データベース名 | 作成日 |"
            + NL
            + "|:---|:---|:---|"
            + NL
            + "|pg|TEST_DB|2025-01-01|"
            + NL
            + NL;
    String actual = TableDefinitionListTemplates.baseInfo(baseInfo());
    MarkdownAssert.assertMarkdownEquals(expected, actual);
  }

  @Test
  @DisplayName("tableListTableHeader: 完全一致（見出しは含まず表ヘッダーのみ）")
  void tableListHeader_fullMatch() {
    String expected =
        """
                | No. | スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | Link | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
    MarkdownAssert.assertMarkdownEquals(
        expected, TableDefinitionListTemplates.tableListTableHeader());
  }

  @Test
  @DisplayName("tableListLine: 行番号・リンク・備考を含む1行＋末尾改行")
  void tableListLine_single() {
    TableEntity e = newEntity("public", "orders", "受注", "note");
    String expected = "|1|public|受注|orders|table|[■](./TEST_DB/public/table/orders.md)|note|" + NL;
    MarkdownAssert.assertMarkdownEquals(expected, TableDefinitionListTemplates.tableListLine(1, e));
  }

  @Test
  @DisplayName("tableListLine: 論理テーブル名（DBコメント由来）に含まれる|・改行は表を崩さないようエスケープする")
  void tableListLine_escapesLogicalTableName() {
    TableEntity e = newEntity("public", "orders", "受注|管理\n(旧:注文)", "");
    String expected =
        "|1|public|受注\\|管理<br>(旧:注文)|orders|table|[■](./TEST_DB/public/table/orders.md)||" + NL;
    MarkdownAssert.assertMarkdownEquals(expected, TableDefinitionListTemplates.tableListLine(1, e));
  }

  @Test
  @DisplayName("relatedDocuments: 空マップ → 空文字")
  void relatedDocuments_empty() {
    MarkdownAssert.assertMarkdownEquals(
        "", TableDefinitionListTemplates.relatedDocuments(baseInfo(), Map.of()));
  }

  @Test
  @DisplayName("relatedDocuments: 挿入順にリンクを列挙する")
  void relatedDocuments_entries() {
    Map<String, String> entries = new LinkedHashMap<>();
    entries.put("関数・プロシージャ一覧", "function");
    entries.put("トリガー一覧", "trigger");
    String section = TableDefinitionListTemplates.relatedDocuments(baseInfo(), entries);
    assertTrue(section.startsWith("## 関連ドキュメント"));
    assertTrue(section.contains("* [関数・プロシージャ一覧](./functionList_TEST_DB.md)"));
    assertTrue(section.contains("* [トリガー一覧](./triggerList_TEST_DB.md)"));
    assertTrue(section.indexOf("functionList") < section.indexOf("triggerList"), "挿入順が保持される");
  }
}
