package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.testsupport.MarkdownAssert;

/**
 * TableDefinitionListTemplates の詳細テスト
 */
public class TableDefinitionListTemplatesTest {

    private static final String NL = System.lineSeparator();
    private static final String NL2 = NL + NL;

    private BaseInfoEntity baseInfo() {
        return new BaseInfoEntity("TEST_DB", "| pg | TEST_DB | 2025-01-01 |");
    }

    private TableEntity newEntity(int no, String schema, String physical, String logical) {
        String listRow = "| " + no + " | " + schema + " | " + logical + " | " + physical + " | T | link | note |";
        return new TableEntity("TEST_DB", schema, logical, physical, "table", listRow,
                "| " + schema + " | " + logical + " | " + physical + " | T | note |", "");
    }

    @Test
    @DisplayName("fileHeader: 期待値と完全一致 (末尾はダブル改行)")
    void fileHeader_fullMatch() {
        String expected = "# テーブル一覧（DB名：TEST_DB）" + NL2;
        MarkdownAssert.assertMarkdownEquals(expected, TableDefinitionListTemplates.fileHeader(baseInfo()));
    }

    @Test
    @DisplayName("baseInfo: テーブル（基本情報）Markdown 全体一致")
    void baseInfo_fullMatch() {
        String expected = "## 基本情報" + NL + NL + "| RDBMS | データベース名 | 作成日 |" + NL + "|:---|:---|:---|" + NL
                + "| pg | TEST_DB | 2025-01-01 |" + NL + NL;
        String actual = TableDefinitionListTemplates.baseInfo(baseInfo());
        MarkdownAssert.assertMarkdownEquals(expected, actual);
    }

    @Test
    @DisplayName("tableListTableHeader: 完全一致（改行レイアウト確認）")
    void tableListHeader_fullMatch() {
        String expected = """
                ## テーブル情報

                | No. | スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | Link | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
        MarkdownAssert.assertMarkdownEquals(expected, TableDefinitionListTemplates.tableListTableHeader());
    }

    @Test
    @DisplayName("tableListLine: 1行＋末尾改行のみ")
    void tableListLine_single() {
        TableEntity e = newEntity(1, "public", "orders", "受注");
        String expected = e.tableInfoList() + NL;
        MarkdownAssert.assertMarkdownEquals(expected, TableDefinitionListTemplates.tableListLine(e));
    }

    @Test
    @DisplayName("tableList: 複数行 (3件) 完全一致 + 最後1つだけ改行")
    void tableList_threeRows() {
        List<TableEntity> list = IntStream.rangeClosed(1, 3).mapToObj(i -> newEntity(i, "public", "t" + i, "論理" + i))
                .toList();

        String header = TableDefinitionListTemplates.tableListTableHeader();
        String body = String.join(NL, list.stream().map(TableEntity::tableInfoList).toList()) + NL;

        String expected = header + body;
        MarkdownAssert.assertMarkdownEquals(expected, TableDefinitionListTemplates.tableList(list));
    }

    @Test
    @DisplayName("tableList: 空リスト → ヘッダーのみ + 改行1つ")
    void tableList_empty() {
        String expected = TableDefinitionListTemplates.tableListTableHeader() + NL;
        MarkdownAssert.assertMarkdownEquals(expected, TableDefinitionListTemplates.tableList(List.of()));
    }

    @Test
    @DisplayName("subTableListLink: 完全一致（末尾改行あり）")
    void subTableListLink_fullMatch() {
        String expected = "* [テーブル一覧_2](./tableList_TEST_DB_2.md)  " + NL;
        MarkdownAssert.assertMarkdownEquals(expected, TableDefinitionListTemplates.subTableListLink(baseInfo(), 2));
    }

    @Test
    @DisplayName("relatedDocuments: 空マップ → 空文字")
    void relatedDocuments_empty() {
        MarkdownAssert.assertMarkdownEquals("", TableDefinitionListTemplates.relatedDocuments(baseInfo(), Map.of()));
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

    @Nested
    public class FooterTests {

        @Test
        @DisplayName("footer: 総ページ=1 → 単一改行のみ (水平線なし)")
        void footer_singlePage() {
            String expected = NL;
            MarkdownAssert.assertMarkdownEquals(expected,
                    TableDefinitionListTemplates.writeTableListFooter(baseInfo(), 1, 1, 1));
        }

        @ParameterizedTest(name = "[{index}] totalPages={0}, currentPage={1} -> 前後リンク確認")
        @CsvSource({
                // totalPages, currentPage, expectedPrev, expectedNext
                "5,1,false,true", "5,2,true,true", "5,4,true,true", "5,5,true,false" })
        @DisplayName("footer: 複数ページの前後リンク組合せ")
        void footer_param(int totalPages, int currentPage, boolean expectPrev, boolean expectNext) {

            String footer = TableDefinitionListTemplates.writeTableListFooter(baseInfo(), totalPages, currentPage,
                    currentPage);

            assertTrue(footer.startsWith("___" + NL + NL), "水平線 + ダブル改行で始まる");
            if (expectPrev) {
                assertTrue(footer.contains("[<<前へ](./tableList_TEST_DB_" + (currentPage - 1) + ".md) "), "前リンクが存在するべき");
            } else {
                assertFalse(footer.contains("[<<前へ]"), "前リンクが存在しないはず");
            }
            if (expectNext) {
                assertTrue(footer.contains("[次へ>>](./tableList_TEST_DB_" + (currentPage + 1) + ".md) "), "次リンクが存在するべき");
            } else {
                assertFalse(footer.contains("[次へ>>]"), "次リンクが存在しないはず");
            }
            assertTrue(footer.endsWith(NL), "末尾は改行");
        }
    }
}