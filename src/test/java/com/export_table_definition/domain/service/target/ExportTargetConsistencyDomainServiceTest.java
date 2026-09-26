package com.export_table_definition.domain.service.target;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ExportTargetConsistencyDomainService の外部キー・論理リレーションの突き合わせに関するテスト */
public class ExportTargetConsistencyDomainServiceTest {

  private final ExportTargetConsistencyDomainService service =
      new ExportTargetConsistencyDomainService();

  private TableEntity table(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, "table", "");
  }

  private List<String> names(List<ForeignKeyEntity> foreignKeys) {
    return foreignKeys.stream().map(ForeignKeyEntity::foreignkeyName).toList();
  }

  @Test
  @DisplayName("resolveForeignKeys: 参照元・参照先の双方が出力対象の物理外部キーのみを残す")
  void testResolveForeignKeysKeepsOnlyResolvablePhysicalForeignKeys() {
    var tables = List.of(table("public", "orders"), table("public", "customers"));
    var resolvable =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var missingParent =
        ForeignKeyFixtures.physical("public", "orders", "fk_orders_staff", "public", "staff");

    ForeignKeys result =
        service.resolveForeignKeys(List.of(resolvable, missingParent), List.of(), tables, false);

    assertEquals(List.of("fk_orders_customer"), names(result.of(table("public", "orders"))));
  }

  @Test
  @DisplayName("resolveForeignKeys: 論理リレーションも同じ集合へ合流させ、片側が出力対象外のものは除外する")
  void testResolveForeignKeysMergesResolvableLogicalRelations() {
    var tables = List.of(table("public", "orders"), table("public", "staff"));
    var physical =
        ForeignKeyFixtures.physical("public", "orders", "fk_orders_staff", "public", "staff");
    var logical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_staff", "public", "staff");
    var unresolvedLogical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_coupon", "public", "coupons");

    ForeignKeys result =
        service.resolveForeignKeys(
            List.of(physical), List.of(logical, unresolvedLogical), tables, true);

    // 物理外部キー、論理リレーションの順に並ぶ
    assertEquals(
        List.of("fk_orders_staff", "rel_orders_staff"),
        names(result.of(table("public", "orders"))));
    assertEquals(List.of("fk_orders_staff"), names(result.physicalOf(table("public", "orders"))));
    assertEquals(List.of("rel_orders_staff"), names(result.logicalOf(table("public", "orders"))));
  }

  @Test
  @DisplayName("resolveForeignKeys: 対象が無い場合は空の集合を返す")
  void testResolveForeignKeysReturnsEmptyWhenNothingToResolve() {
    ForeignKeys result =
        service.resolveForeignKeys(List.of(), List.of(), List.of(table("public", "orders")), false);

    assertEquals(List.of(), result.of(table("public", "orders")));
    assertEquals(List.of(), result.crossSchema());
  }
}
