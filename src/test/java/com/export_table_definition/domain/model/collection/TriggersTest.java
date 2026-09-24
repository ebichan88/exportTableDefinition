package com.export_table_definition.domain.model.collection;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;

/**
 * Triggers のテーブルキーによるインデックス化に関するテスト
 */
public class TriggersTest {

    private TableEntity newTable(String schema, String physical) {
        return new TableEntity("TEST_DB", schema, "", physical, "table", "", "", "");
    }

    @Test
    @DisplayName("of: 自テーブルに属するトリガーのみを、登録順を保って返す")
    void testOfReturnsOwnTriggersInOrder() {
        var t1 = new TriggerEntity("public", "orders", "unused", "|1|trg_orders_1|BEFORE|INSERT|ROW|...|");
        var t2 = new TriggerEntity("public", "orders", "unused", "|2|trg_orders_2|AFTER|UPDATE|ROW|...|");
        var other = new TriggerEntity("public", "customers", "unused", "|1|trg_customers_1|BEFORE|INSERT|ROW|...|");
        var triggers = Triggers.of(List.of(t1, t2, other));

        assertEquals(List.of(t1, t2), triggers.of(newTable("public", "orders")));
    }

    @Test
    @DisplayName("of: 該当するトリガーがないテーブルには空リストを返す")
    void testOfReturnsEmptyForUnknownTable() {
        var triggers = Triggers.of(List.of(new TriggerEntity("public", "orders", "unused", "unused")));

        assertEquals(List.of(), triggers.of(newTable("public", "unknown")));
    }

    @Test
    @DisplayName("of: 同名テーブルでもスキーマが異なれば別のキーとして扱う")
    void testOfDistinguishesSameTableNameAcrossSchemas() {
        var publicTrigger = new TriggerEntity("public", "orders", "unused", "unused");
        var salesTrigger = new TriggerEntity("sales", "orders", "unused", "unused");
        var triggers = Triggers.of(List.of(publicTrigger, salesTrigger));

        assertEquals(List.of(publicTrigger), triggers.of(newTable("public", "orders")));
        assertEquals(List.of(salesTrigger), triggers.of(newTable("sales", "orders")));
    }
}
