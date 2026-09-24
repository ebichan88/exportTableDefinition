package com.export_table_definition.domain.model.collection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * Indexes のテーブルキーによるインデックス化に関するテスト
 */
public class IndexesTest {

    private TableEntity newTable(String schema, String physical) {
        return new TableEntity("TEST_DB", schema, "", physical, "table", "", "", "");
    }

    @Test
    @DisplayName("of: 自テーブルに属するインデックスのみを、登録順を保って返す")
    void testOfReturnsOwnIndexesInOrder() {
        var idx1 = new IndexEntity("public", "orders", "|1|idx_orders_1|id|");
        var idx2 = new IndexEntity("public", "orders", "|2|idx_orders_2|code|");
        var other = new IndexEntity("public", "customers", "|1|idx_customers_1|id|");
        var indexes = Indexes.of(List.of(idx1, idx2, other));

        assertEquals(List.of(idx1, idx2), indexes.of(newTable("public", "orders")));
    }

    @Test
    @DisplayName("of: 該当するインデックスがないテーブルには空リストを返す")
    void testOfReturnsEmptyForUnknownTable() {
        var indexes = Indexes.of(List.of(new IndexEntity("public", "orders", "unused")));

        assertEquals(List.of(), indexes.of(newTable("public", "unknown")));
    }

    @Test
    @DisplayName("of: 同名テーブルでもスキーマが異なれば別のキーとして扱う")
    void testOfDistinguishesSameTableNameAcrossSchemas() {
        var publicIndex = new IndexEntity("public", "orders", "unused");
        var salesIndex = new IndexEntity("sales", "orders", "unused");
        var indexes = Indexes.of(List.of(publicIndex, salesIndex));

        assertEquals(List.of(publicIndex), indexes.of(newTable("public", "orders")));
        assertEquals(List.of(salesIndex), indexes.of(newTable("sales", "orders")));
    }
}
