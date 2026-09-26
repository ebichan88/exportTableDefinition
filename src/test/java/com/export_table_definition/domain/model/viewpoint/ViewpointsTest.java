package com.export_table_definition.domain.model.viewpoint;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Viewpoints のテーブルから観点への逆引きに関するテスト */
public class ViewpointsTest {

  private static TableEntity table(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, TableType.TABLE, "");
  }

  @Test
  @DisplayName("of: テーブルが所属する観点を宣言順に返し、所属する観点が無い場合は空")
  void testOfTable() {
    Viewpoint order = Viewpoint.of("order", "受注管理", "", List.of("sales.order*", "customer"));
    Viewpoint master = Viewpoint.of("master", "マスタ", "", List.of("customer", "product"));
    Viewpoints viewpoints = Viewpoints.of(List.of(order, master));

    assertEquals(List.of(order, master), viewpoints.of(table("sales", "customer")));
    assertEquals(List.of(order), viewpoints.of(table("sales", "orders")));
    assertEquals(List.of(), viewpoints.of(table("sales", "stock")));
  }

  @Test
  @DisplayName("empty: 観点を持たない")
  void testEmpty() {
    assertTrue(Viewpoints.empty().isEmpty());
    assertEquals(List.of(), Viewpoints.empty().asList());
    assertEquals(List.of(), Viewpoints.empty().of(table("sales", "orders")));
  }
}
