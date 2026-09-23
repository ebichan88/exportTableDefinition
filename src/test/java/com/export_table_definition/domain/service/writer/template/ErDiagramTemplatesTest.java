package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * ErDiagramTemplates のセクション生成テスト
 */
public class ErDiagramTemplatesTest {

    private BaseInfoEntity baseInfo() {
        return new BaseInfoEntity("TEST_DB", "| pg | TEST_DB | 2025-01-01 |");
    }

    private TableEntity newTable(String schema, String physical, String logical) {
        return new TableEntity("TEST_DB", schema, logical, physical, "table",
                "| 1 | " + schema + " | " + logical + " | " + physical + " | T | link | note |",
                "| " + schema + " | " + logical + " | " + physical + " | T | note |", "");
    }

    private ForeignKeyEntity newFk(String schema, String table, String fkName, String refSchema, String refTable) {
        return new ForeignKeyEntity(schema, table, "unused", fkName, refSchema, refTable);
    }

    @Test
    @DisplayName("fileHeader: タイトルとDB名を含むヘッダーを生成する")
    void testFileHeader() {
        String header = ErDiagramTemplates.fileHeader("ER図一覧", baseInfo());
        assertTrue(header.startsWith("# ER図一覧（DB名：TEST_DB）"));
    }

    @Test
    @DisplayName("schemaFileHeader: DB名とスキーマ名を含むヘッダーを生成する")
    void testSchemaFileHeader() {
        String header = ErDiagramTemplates.schemaFileHeader("public", baseInfo());
        assertTrue(header.startsWith("# ER図（DB名：TEST_DB / スキーマ名：public）"));
    }

    @Test
    @DisplayName("erDiagram: 外部キーがない場合はMermaidを出力しない")
    void testErDiagramNoForeignKeys() {
        String section = ErDiagramTemplates.erDiagram(List.of(), 80);
        assertTrue(section.contains("外部キーによる関連を持つテーブルはありません。"));
        assertFalse(section.contains("```mermaid"));
    }

    @Test
    @DisplayName("erDiagram: 参照先 ||--o{ 参照元 の向きで関係線を出力する")
    void testErDiagramRelationDirection() {
        var fk = newFk("public", "orders", "fk_orders_customer", "public", "customers");
        String section = ErDiagramTemplates.erDiagram(List.of(fk), 80);
        assertTrue(section.contains("```mermaid"));
        assertTrue(section.contains("erDiagram"));
        assertTrue(section.contains("public_customers ||--o{ public_orders : \"fk_orders_customer\""));
    }

    @Test
    @DisplayName("erDiagram: 属性（カラム）は出力しない")
    void testErDiagramHasNoAttributes() {
        var fk = newFk("public", "orders", "fk_orders_customer", "public", "customers");
        String section = ErDiagramTemplates.erDiagram(List.of(fk), 80);
        // 属性を持つ箱は「<エンティティ名> {」で始まるブロックとして出力される
        assertFalse(section.contains(" {" + System.lineSeparator()));
    }

    @Test
    @DisplayName("erDiagram: スキーマを跨ぐ外部キーも関係線として出力する")
    void testErDiagramCrossSchemaRelation() {
        var fk = newFk("sales", "orders", "fk_orders_customer", "master", "customers");
        String section = ErDiagramTemplates.erDiagram(List.of(fk), 80);
        assertTrue(section.contains("master_customers ||--o{ sales_orders : \"fk_orders_customer\""));
    }

    @Test
    @DisplayName("erDiagram: サニタイズ後に識別子が衝突する場合は連番で一意化する")
    void testErDiagramResolvesIdCollision() {
        // sanitizeIdentifier により "a_b" + "c" と "a" + "b_c" はいずれも "a_b_c" に潰れる
        var fk1 = newFk("a_b", "c", "fk1", "ref", "t");
        var fk2 = newFk("a", "b_c", "fk2", "ref", "t");
        String section = ErDiagramTemplates.erDiagram(List.of(fk1, fk2), 80);
        assertTrue(section.contains("ref_t ||--o{ a_b_c : \"fk1\""));
        assertTrue(section.contains("ref_t ||--o{ a_b_c_2 : \"fk2\""));
    }

    @Test
    @DisplayName("erDiagram: ノード数が上限を超える場合はMermaidを出力せず外部キー一覧にフォールバックする")
    void testErDiagramFallbackOnOverflow() {
        var fk1 = newFk("public", "orders", "fk_orders_customer", "public", "customers");
        var fk2 = newFk("public", "items", "fk_items_orders", "public", "orders");
        // ノードは orders / customers / items の3件のため、上限2件で超過する
        String section = ErDiagramTemplates.erDiagram(List.of(fk1, fk2), 2);
        assertFalse(section.contains("```mermaid"));
        assertTrue(section.contains("関連テーブル数が3件と上限（2件）を超えるため、ER図の描画を省略しました。"));
        assertTrue(section.contains("| 1 | public.orders | fk_orders_customer | public.customers |"));
        assertTrue(section.contains("| 2 | public.items | fk_items_orders | public.orders |"));
    }

    @Test
    @DisplayName("erDiagram: 上限が0以下の場合は件数に関わらずMermaidを出力する")
    void testErDiagramNoLimit() {
        var fk1 = newFk("public", "orders", "fk_orders_customer", "public", "customers");
        var fk2 = newFk("public", "items", "fk_items_orders", "public", "orders");
        String section = ErDiagramTemplates.erDiagram(List.of(fk1, fk2), 0);
        assertTrue(section.contains("```mermaid"));
    }

    @Test
    @DisplayName("tableList: ER図掲載の有無と定義書へのリンクを出力する")
    void testTableList() {
        var connected = newTable("public", "orders", "受注");
        var isolated = newTable("public", "logs", "ログ");
        var fk = newFk("public", "orders", "fk_orders_customer", "public", "customers");
        String section = ErDiagramTemplates.tableList(List.of(connected, isolated), List.of(fk));
        assertTrue(section.contains("| 1 | orders | 受注 | table | ○ | [定義書](./TEST_DB/public/table/orders.md) |"));
        assertTrue(section.contains("| 2 | logs | ログ | table | - | [定義書](./TEST_DB/public/table/logs.md) |"));
    }

    @Test
    @DisplayName("schemaIndex: スキーマ別ER図へのリンクとテーブル数を出力する")
    void testSchemaIndex() {
        final Map<String, List<TableEntity>> tablesBySchema = new LinkedHashMap<>();
        tablesBySchema.put("public", List.of(newTable("public", "orders", "受注"), newTable("public", "items", "明細")));
        tablesBySchema.put("master", List.of(newTable("master", "customers", "顧客")));
        String section = ErDiagramTemplates.schemaIndex(baseInfo(), tablesBySchema);
        assertTrue(section.contains("| 1 | public | 2 | [ER図](./erDiagram_TEST_DB_public.md) |"));
        assertTrue(section.contains("| 2 | master | 1 | [ER図](./erDiagram_TEST_DB_master.md) |"));
    }

    @Test
    @DisplayName("crossSchemaForeignKeys: 渡された外部キーを一覧表として出力する")
    void testCrossSchemaForeignKeys() {
        var crossSchema = newFk("sales", "orders", "fk_cross", "master", "customers");
        String section = ErDiagramTemplates.crossSchemaForeignKeys(List.of(crossSchema));
        assertTrue(section.contains("## スキーマ跨ぎの外部キー"));
        assertTrue(section.contains("| 1 | sales.orders | fk_cross | master.customers |"));
    }

    @Test
    @DisplayName("crossSchemaForeignKeys: 該当がない場合は空文字列を返す")
    void testCrossSchemaForeignKeysEmpty() {
        assertEquals("", ErDiagramTemplates.crossSchemaForeignKeys(List.of()));
    }

    @Test
    @DisplayName("schemaFooter: ER図一覧とテーブル一覧へのリンクを含む")
    void testSchemaFooter() {
        String footer = ErDiagramTemplates.schemaFooter(baseInfo());
        assertTrue(footer.startsWith("___"));
        assertTrue(footer.contains("[ER図一覧へ](./erDiagramList_TEST_DB.md)"));
        assertTrue(footer.contains("[テーブル一覧へ](./tableList_TEST_DB.md)"));
    }
}
