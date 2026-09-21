package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * TableDefinitionTemplates のセクション生成テスト
 */
public class TableDefinitionTemplatesTest {

    private TableEntity newTable(String schema, String physical, String logical, String type, String def) {
        return new TableEntity("TEST_DB", schema, logical, physical, type,
                "| 1 | " + schema + " | " + logical + " | " + physical + " | T | link | note |",
                "| " + schema + " | " + logical + " | " + physical + " | T | note |", def);
    }

    @Test
    @DisplayName("fileHeader: テーブル名ヘッダー + 改行2つ")
    void testFileHeader() {
        TableEntity table = newTable("public", "orders", "受注", "table", "");
        String header = TableDefinitionTemplates.fileHeader(table);
        assertTrue(header.startsWith("# orders（受注）"));
    }

    @Test
    @DisplayName("baseInfo: baseInfo の内容を含む")
    void testBaseInfo() {
        var base = new BaseInfoEntity("TEST_DB", "| pg | TEST_DB | 2025-01-01 |");
        String txt = TableDefinitionTemplates.baseInfo(base);
        assertTrue(txt.contains("| pg | TEST_DB | 2025-01-01 |"));
    }

    @Test
    @DisplayName("tableInfo: 単体テーブル行が含まれる")
    void testTableInfo() {
        TableEntity table = newTable("public", "orders", "受注", "table", "");
        String info = TableDefinitionTemplates.tableInfo(table);
        assertTrue(info.contains("| public | 受注 | orders |"));
    }

    @Test
    @DisplayName("columns: schema.table が一致する行のみ含まれる")
    void testColumnsFiltered() {
        TableEntity table = newTable("public", "orders", "受注", "table", "");
        var match = new ColumnEntity("public", "orders", "| 1 | 受注ID | order_id | int | Y | N |  |  |", "order_id",
                "int", "○");
        var other = new ColumnEntity("other", "customers", "| 1 | 顧客ID | customer_id | int | Y | N |  |  |",
                "customer_id", "int", "○");
        String section = TableDefinitionTemplates.columns(List.of(match, other), table);
        assertTrue(section.contains("| 1 | 受注ID | order_id | int |"));
        assertFalse(section.contains("顧客ID"));
    }

    @Test
    @DisplayName("view: view でない場合は空文字")
    void testViewSectionNonView() {
        TableEntity table = newTable("public", "orders", "受注", "table", "SELECT * FROM orders");
        assertEquals("", TableDefinitionTemplates.view(table));
    }

    @Test
    @DisplayName("view: view の場合はSQLコードブロック含む")
    void testViewSectionView() {
        TableEntity table = newTable("public", "v_orders", "受注ビュー", "view", "SELECT * FROM orders");
        String section = TableDefinitionTemplates.view(table);
        assertTrue(section.contains("```sql"));
        assertTrue(section.contains("SELECT * FROM orders"));
    }

    @Test
    @DisplayName("indexes: schema.table 一致行のみ")
    void testIndexesFiltered() {
        TableEntity table = newTable("public", "orders", "受注", "table", "");
        var idx1 = new IndexEntity("public", "orders", "| 1 | idx_orders_1 | order_id |");
        var idx2 = new IndexEntity("other", "orders", "| 1 | idx_other | ... |");
        String section = TableDefinitionTemplates.indexes(List.of(idx1, idx2), table);
        assertTrue(section.contains("idx_orders_1"));
        assertFalse(section.contains("idx_other"));
    }

    @Test
    @DisplayName("constraints: schema.table 一致行のみ")
    void testConstraintsFiltered() {
        TableEntity table = newTable("public", "orders", "受注", "table", "");
        var c1 = new ConstraintEntity("public", "orders", "| 1 | pk_orders | PRIMARY KEY | (order_id) |");
        var c2 = new ConstraintEntity("x", "y", "| 1 | pk_other | PRIMARY KEY | (id) |");
        String section = TableDefinitionTemplates.constraints(List.of(c1, c2), table);
        assertTrue(section.contains("pk_orders"));
        assertFalse(section.contains("pk_other"));
    }

    @Test
    @DisplayName("foreignKeys: schema.table 一致行のみ")
    void testForeignKeysFiltered() {
        TableEntity table = newTable("public", "orders", "受注", "table", "");
        var fk1 = new ForeignKeyEntity("public", "orders", "| 1 | fk_orders_customer | customer_id | customers | id |",
                "fk_orders_customer", "public", "customers");
        var fk2 = new ForeignKeyEntity("sales", "orders", "| 1 | fk_sales_orders | x | y | z |", "fk_sales_orders",
                "sales", "y");
        String section = TableDefinitionTemplates.foreignKeys(List.of(fk1, fk2), table);
        assertTrue(section.contains("fk_orders_customer"));
        assertFalse(section.contains("fk_sales_orders"));
    }

    @Test
    @DisplayName("erDiagram: 関連テーブルがない場合はメッセージのみ")
    void testErDiagramNoRelations() {
        TableEntity table = newTable("public", "orders", "受注", "table", "");
        String section = TableDefinitionTemplates.erDiagram(table, List.of(), List.of(), List.of());
        assertTrue(section.contains("関連するテーブルはありません。"));
        assertFalse(section.contains("```mermaid"));
    }

    @Test
    @DisplayName("erDiagram: 自テーブルの属性・PK表記と参照先/参照元の関係線が含まれる")
    void testErDiagramWithRelations() {
        TableEntity table = newTable("public", "orders", "受注", "table", "");
        var column = new ColumnEntity("public", "orders", "unused", "order_id", "character varying(20)", "○");
        var outgoing = new ForeignKeyEntity("public", "orders", "unused", "fk_orders_customer", "public", "customers");
        var incoming = new ForeignKeyEntity("public", "items", "unused", "fk_items_orders", "public", "orders");
        String section = TableDefinitionTemplates.erDiagram(table, List.of(column), List.of(outgoing), List.of(incoming));
        assertTrue(section.contains("```mermaid"));
        assertTrue(section.contains("erDiagram"));
        // 参照先(customers) -> 自テーブル(orders)
        assertTrue(section.contains("public_customers ||--o{ public_orders : \"fk_orders_customer\""));
        // 自テーブル(orders) -> 参照元(items)
        assertTrue(section.contains("public_orders ||--o{ public_items : \"fk_items_orders\""));
        // 自テーブルの属性: 型の括弧部分は除去、空白はアンダースコア、PKマーカー付き
        assertTrue(section.contains("character_varying order_id PK"));
    }

    @Test
    @DisplayName("erDiagram: データ型の桁数指定は除去される")
    void testErDiagramSanitizesType() {
        TableEntity table = newTable("public", "orders", "受注", "table", "");
        var column = new ColumnEntity("public", "orders", "unused", "amount", "numeric(10,2)", "");
        var outgoing = new ForeignKeyEntity("public", "orders", "unused", "fk_orders_customer", "public", "customers");
        String section = TableDefinitionTemplates.erDiagram(table, List.of(column), List.of(outgoing), List.of());
        assertTrue(section.contains("numeric amount"));
        assertFalse(section.contains("(10,2)"));
    }

    @Test
    @DisplayName("footer: 一覧へのリンクが含まれる")
    void testFooter() {
        var base = new BaseInfoEntity("TEST_DB", "| pg | TEST_DB | 2025-01-01 |");
        String footer = TableDefinitionTemplates.footer(base);
        assertTrue(footer.contains("[テーブル一覧へ](../../../tableList_TEST_DB.md)"));
        assertTrue(footer.startsWith("___"));
    }
}