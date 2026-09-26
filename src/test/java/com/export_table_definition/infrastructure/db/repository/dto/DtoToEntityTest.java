package com.export_table_definition.infrastructure.db.repository.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.type.TableType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SQLの取得結果（DTO）からドメインのエンティティへの変換に関するテスト */
public class DtoToEntityTest {

  @Test
  @DisplayName("TableDto: 区分の文字列をTableTypeへ変換する")
  void testTableDtoConvertsTableType() {
    var dto = new TableDto("testdb", "public", "受注ビュー", "v_orders", "view", "SELECT 1");

    assertEquals(TableType.VIEW, dto.toEntity().tableType());
  }

  @Test
  @DisplayName("TableDto: 未知の区分は変換時にIllegalArgumentExceptionをスローする")
  void testTableDtoRejectsUnknownTableType() {
    var dto = new TableDto("testdb", "public", "", "orders", "foreign_table", "");

    assertThrows(IllegalArgumentException.class, dto::toEntity);
  }
}
