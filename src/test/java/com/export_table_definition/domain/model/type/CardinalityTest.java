package com.export_table_definition.domain.model.type;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cardinality の多重度判定テスト
 */
public class CardinalityTest {

    @Test
    @DisplayName("of: 一意でなくNOT NULLの場合は1対多")
    void testOneToMany() {
        assertEquals(Cardinality.ONE_TO_MANY, Cardinality.of(false, true));
        assertEquals("||--o{", Cardinality.ONE_TO_MANY.getNotation());
    }

    @Test
    @DisplayName("of: 一意でなくNULLを許容する場合は0..1対多")
    void testOptionalOneToMany() {
        assertEquals(Cardinality.OPTIONAL_ONE_TO_MANY, Cardinality.of(false, false));
        assertEquals("|o--o{", Cardinality.OPTIONAL_ONE_TO_MANY.getNotation());
    }

    @Test
    @DisplayName("of: 一意かつNOT NULLの場合は1対1")
    void testOneToOne() {
        assertEquals(Cardinality.ONE_TO_ONE, Cardinality.of(true, true));
        assertEquals("||--o|", Cardinality.ONE_TO_ONE.getNotation());
    }

    @Test
    @DisplayName("of: 一意だがNULLを許容する場合は0..1対1")
    void testOptionalOneToOne() {
        assertEquals(Cardinality.OPTIONAL_ONE_TO_ONE, Cardinality.of(true, false));
        assertEquals("|o--o|", Cardinality.OPTIONAL_ONE_TO_ONE.getNotation());
    }

    @Test
    @DisplayName("getNotation: 子側が「1以上」となる表記は持たない")
    void testNoMandatoryChildNotation() {
        // 「親1件につき子が1件以上存在すること」はテーブル定義では表現できない
        for (Cardinality cardinality : Cardinality.values()) {
            assertFalse(cardinality.getNotation().endsWith("|{"), cardinality.name());
            assertFalse(cardinality.getNotation().endsWith("--||"), cardinality.name());
        }
    }

    @Test
    @DisplayName("getLabel: すべての多重度にラベルが定義されている")
    void testLabelDefined() {
        for (Cardinality cardinality : Cardinality.values()) {
            assertNotNull(cardinality.getLabel());
            assertFalse(cardinality.getLabel().isEmpty(), cardinality.name());
        }
    }
}
