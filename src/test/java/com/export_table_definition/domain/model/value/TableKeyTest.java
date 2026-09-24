package com.export_table_definition.domain.model.value;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * TableKey のファクトリメソッド・等価性に関するテスト
 */
public class TableKeyTest {

    @Test
    @DisplayName("of(schema, table): 指定した値を保持するTableKeyを生成する")
    void testOfWithSchemaAndTable() {
        TableKey key = TableKey.of("public", "orders");
        assertEquals("public", key.schema());
        assertEquals("orders", key.table());
    }

    @Test
    @DisplayName("of(TableEntity): スキーマ名・物理テーブル名からTableKeyを生成する")
    void testOfWithTableEntity() {
        TableEntity table = new TableEntity("testdb", "public", "受注", "orders", "table", "", "", "");
        TableKey key = TableKey.of(table);
        assertEquals(new TableKey("public", "orders"), key);
    }

    @Test
    @DisplayName("of(TableEntity): 論理テーブル名やDB名の違いはキーに影響しない")
    void testOfWithTableEntityIgnoresLogicalNameAndDbName() {
        TableEntity a = new TableEntity("db1", "public", "論理名A", "orders", "table", "", "", "");
        TableEntity b = new TableEntity("db2", "public", "論理名B", "orders", "view", "", "", "");
        assertEquals(TableKey.of(a), TableKey.of(b));
    }

    @Test
    @DisplayName("equals/hashCode: スキーマ・テーブル名が同じキー同士は等価")
    void testEqualsAndHashCode() {
        TableKey a = TableKey.of("public", "orders");
        TableKey b = TableKey.of("public", "orders");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("equals: スキーマまたはテーブル名が異なれば非等価")
    void testEqualsDiffersWhenSchemaOrTableDiffers() {
        TableKey base = TableKey.of("public", "orders");
        assertNotEquals(base, TableKey.of("sales", "orders"));
        assertNotEquals(base, TableKey.of("public", "customers"));
    }
}
