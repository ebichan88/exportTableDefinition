package com.export_table_definition.domain.model.table;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TableType の逆引き・ビュー判定に関するテスト */
public class TableTypeTest {

  @Test
  @DisplayName("findByName: 'table' はTABLEに解決される")
  void testFindByNameTable() {
    assertEquals(TableType.TABLE, TableType.findByName("table"));
  }

  @Test
  @DisplayName("findByName: 'view' はVIEWに解決される")
  void testFindByNameView() {
    assertEquals(TableType.VIEW, TableType.findByName("view"));
  }

  @Test
  @DisplayName("findByName: 'materialized_view' はMATERIALIZED_VIEWに解決される")
  void testFindByNameMaterializedView() {
    assertEquals(TableType.MATERIALIZED_VIEW, TableType.findByName("materialized_view"));
  }

  @Test
  @DisplayName("findByName: 未知の値の場合はIllegalArgumentExceptionをスローする")
  void testFindByNameUnknownThrows() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> TableType.findByName("unknown"));
    assertTrue(e.getMessage().contains("unknown"));
  }

  @Test
  @DisplayName("findByName: nullの場合はIllegalArgumentExceptionをスローする")
  void testFindByNameNullThrows() {
    assertThrows(IllegalArgumentException.class, () -> TableType.findByName(null));
  }

  @Test
  @DisplayName("isView: TABLEはfalse")
  void testIsViewTable() {
    assertFalse(TableType.TABLE.isView());
  }

  @Test
  @DisplayName("isView: VIEWはtrue")
  void testIsViewView() {
    assertTrue(TableType.VIEW.isView());
  }

  @Test
  @DisplayName("isView: MATERIALIZED_VIEWはtrue")
  void testIsViewMaterializedView() {
    assertTrue(TableType.MATERIALIZED_VIEW.isView());
  }

  @Test
  @DisplayName("getName: 各Enum値に対応するテーブル種類名を返す")
  void testGetName() {
    assertEquals("table", TableType.TABLE.getName());
    assertEquals("view", TableType.VIEW.getName());
    assertEquals("materialized_view", TableType.MATERIALIZED_VIEW.getName());
  }
}
