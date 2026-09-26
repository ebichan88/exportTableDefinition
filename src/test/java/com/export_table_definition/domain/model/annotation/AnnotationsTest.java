package com.export_table_definition.domain.model.annotation;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Annotations のテーブルキー突合・空フォールバックに関するテスト */
public class AnnotationsTest {

  private TableEntity table(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, "table", "");
  }

  @Test
  @DisplayName("of: スキーマ.物理テーブル名が一致する付帯情報を返す")
  void testOfReturnsMatchingAnnotation() {
    var annotation = new TableAnnotation("説明", "備考", Map.of());
    var annotations = Annotations.of(Map.of(TableKey.of("public", "orders"), annotation));

    assertSame(annotation, annotations.of(table("public", "orders")));
  }

  @Test
  @DisplayName("of: 一致する付帯情報が無い場合はEMPTYを返す")
  void testOfReturnsEmptyWhenNoMatch() {
    var annotations =
        Annotations.of(
            Map.of(TableKey.of("public", "orders"), new TableAnnotation("説明", "", Map.of())));

    assertSame(TableAnnotation.EMPTY, annotations.of(table("public", "customers")));
  }

  @Test
  @DisplayName("empty: 付帯情報を持たず、どのテーブルに対してもEMPTYを返す")
  void testEmpty() {
    var annotations = Annotations.empty();

    assertTrue(annotations.isEmpty());
    assertTrue(annotations.tableKeys().isEmpty());
    assertSame(TableAnnotation.EMPTY, annotations.of(table("public", "orders")));
  }
}
