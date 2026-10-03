package com.export_table_definition.infrastructure.db.repository.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.relation.Cardinality;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.relation.RelationType;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.TableType;
import java.util.List;
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

  @Test
  @DisplayName("ForeignKeyDto: カンマ区切りで連結された列名をリストへ分解する")
  void testForeignKeyDtoSplitsColumnNames() {
    var dto =
        new ForeignKeyDto(
            "public",
            "order_items",
            "fk_order_items",
            "order_id,line_no",
            "public",
            "orders",
            "id,line_no",
            false,
            true);

    ForeignKeyEntity entity = dto.toEntity();

    assertEquals(List.of("order_id", "line_no"), entity.columnNames());
    assertEquals(List.of("id", "line_no"), entity.referenceColumnNames());
    assertEquals(Cardinality.ONE_TO_MANY, entity.cardinality());
    assertEquals(RelationType.PHYSICAL, entity.relationType());
  }

  @Test
  @DisplayName("TriggerDto: スラッシュ区切りで連結された対象イベントをリストへ分解する")
  void testTriggerDtoSplitsEvents() {
    var dto =
        new TriggerDto(
            "public", "orders", "trg", "AFTER", "INSERT/UPDATE", "ROW", "public.f", "CREATE ...");

    assertEquals(List.of("INSERT", "UPDATE"), dto.toEntity().events());
  }

  @Test
  @DisplayName("値が無い任意項目は、DBによらず空文字に揃える（PostgreSQLの空白1文字・OracleのNULL）")
  void testAbsentValuesAreNormalizedToEmpty() {
    // PostgreSQL: 値が無い場合に空白1文字を返していた項目
    var fromPostgres = new ColumnDto("public", "orders", "", "id", "integer", "", true, true, " ");
    // Oracle: 空文字がNULLとして返る
    var fromOracle =
        new ColumnDto("PUBLIC", "ORDERS", null, "ID", "NUMBER", null, true, true, null);

    ColumnEntity postgres = fromPostgres.toEntity();
    ColumnEntity oracle = fromOracle.toEntity();

    assertEquals("", postgres.defaultValue());
    assertEquals("", oracle.logicalColumnName());
    assertEquals("", oracle.precisionScale());
    assertEquals("", oracle.defaultValue());
  }

  @Test
  @DisplayName("値を持つ項目は前後の空白も含めてそのまま保持する")
  void testPresentValuesAreKeptAsIs() {
    var dto = new TableDto("testdb", "public", "", "v_orders", "view", " SELECT 1;");

    assertEquals(" SELECT 1;", dto.toEntity().definition());
  }
}
