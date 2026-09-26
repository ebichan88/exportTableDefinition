package com.export_table_definition.domain.service.target;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.TableDetail;
import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.collection.Tables;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.type.TableType;
import com.export_table_definition.domain.model.value.ConsistencyFinding;
import com.export_table_definition.domain.model.value.ConsistencyFinding.Kind;
import com.export_table_definition.domain.model.value.ConsistencyFinding.Severity;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.domain.service.target.ExportTargetConsistencyDomainService.ResolvedForeignKeys;
import com.export_table_definition.testsupport.EntityFixtures;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ExportTargetConsistencyDomainService の外部キー・論理リレーション・付帯情報の突き合わせに関するテスト */
public class ExportTargetConsistencyDomainServiceTest {

  private final ExportTargetConsistencyDomainService service =
      new ExportTargetConsistencyDomainService();

  private TableEntity table(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, TableType.TABLE, "");
  }

  private List<String> names(List<ForeignKeyEntity> foreignKeys) {
    return foreignKeys.stream().map(ForeignKeyEntity::foreignkeyName).toList();
  }

  private List<Kind> kinds(List<ConsistencyFinding> findings) {
    return findings.stream().map(ConsistencyFinding::kind).toList();
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
        service
            .resolveForeignKeys(
                List.of(resolvable, missingParent), List.of(), Tables.of(tables), false)
            .foreignKeys();

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
        service
            .resolveForeignKeys(
                List.of(physical), List.of(logical, unresolvedLogical), Tables.of(tables), true)
            .foreignKeys();

    // 物理外部キー、論理リレーションの順に並ぶ
    assertEquals(
        List.of("fk_orders_staff", "rel_orders_staff"),
        names(result.of(table("public", "orders"))));
    assertEquals(List.of("fk_orders_staff"), names(result.physicalOf(table("public", "orders"))));
    assertEquals(List.of("rel_orders_staff"), names(result.logicalOf(table("public", "orders"))));
  }

  @Test
  @DisplayName("resolveForeignKeys: 対象が無い場合は空の集合を返し、指摘も無い")
  void testResolveForeignKeysReturnsEmptyWhenNothingToResolve() {
    ResolvedForeignKeys result =
        service.resolveForeignKeys(
            List.of(), List.of(), Tables.of(List.of(table("public", "orders"))), false);

    assertEquals(List.of(), result.foreignKeys().of(table("public", "orders")));
    assertEquals(List.of(), result.foreignKeys().crossSchema());
    assertEquals(List.of(), result.findings());
  }

  @Test
  @DisplayName("resolveForeignKeys: 絞り込みが無い場合、参照先が存在しない物理外部キーを指摘する")
  void testResolveForeignKeysReportsUnresolvedPhysicalForeignKeyWhenNotFiltered() {
    var tables = Tables.of(List.of(table("public", "orders")));
    var missingParent =
        ForeignKeyFixtures.physical("public", "orders", "fk_orders_staff", "public", "staff");

    List<ConsistencyFinding> findings =
        service.resolveForeignKeys(List.of(missingParent), List.of(), tables, false).findings();

    assertEquals(List.of(Kind.UNRESOLVED_FOREIGN_KEY), kinds(findings));
    assertEquals(Severity.WARN, findings.get(0).severity());
    assertEquals(
        "Skipping a foreign key because the referenced table was not found (renamed or dropped?)."
            + " [foreignKey=fk_orders_staff, table=public.orders, referenceTable=public.staff]",
        findings.get(0).message());
  }

  @Test
  @DisplayName("resolveForeignKeys: 絞り込み時は、物理外部キーの除外は意図したものとして指摘しない")
  void testResolveForeignKeysDoesNotReportPhysicalForeignKeyWhenFiltered() {
    var tables = Tables.of(List.of(table("public", "orders")));
    var missingParent =
        ForeignKeyFixtures.physical("public", "orders", "fk_orders_staff", "public", "staff");

    assertEquals(
        List.of(),
        service.resolveForeignKeys(List.of(missingParent), List.of(), tables, true).findings());
  }

  @Test
  @DisplayName("resolveForeignKeys: 論理リレーションの除外は絞り込みの有無によらず指摘し、合流させた件数も報告する")
  void testResolveForeignKeysReportsLogicalRelations() {
    var tables = Tables.of(List.of(table("public", "orders"), table("public", "staff")));
    var logical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_staff", "public", "staff");
    var unresolvedLogical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_coupon", "public", "coupons");

    List<ConsistencyFinding> findings =
        service
            .resolveForeignKeys(List.of(), List.of(logical, unresolvedLogical), tables, true)
            .findings();

    assertEquals(
        List.of(Kind.UNRESOLVED_LOGICAL_RELATION, Kind.LOGICAL_RELATIONS_MERGED), kinds(findings));
    assertEquals(
        "Skipping logical relation because the table was not found in the output target"
            + " (filtered, renamed or dropped?). [relation=rel_orders_coupon, table=public.orders,"
            + " parentTable=public.coupons (not found)]",
        findings.get(0).message());
    assertEquals(Severity.INFO, findings.get(1).severity());
    assertEquals(
        "Merged logical relations declared in the sidecar. [relationCount=1]",
        findings.get(1).message());
  }

  @Test
  @DisplayName("findOrphanTableAnnotations: 実在しないテーブルに対する付帯情報を指摘する")
  void testFindOrphanTableAnnotations() {
    var annotations =
        Annotations.of(
            Map.of(
                TableKey.of("public", "orders"), new TableAnnotation("説明", "", Map.of()),
                TableKey.of("public", "removed"), new TableAnnotation("説明", "", Map.of())));

    List<ConsistencyFinding> findings =
        service.findOrphanTableAnnotations(
            annotations, Tables.of(List.of(table("public", "orders"))), false);

    assertEquals(List.of(Kind.ORPHAN_TABLE_ANNOTATION), kinds(findings));
    assertEquals(
        "Annotation exists for a table that was not found (renamed or dropped?)."
            + " [table=public.removed]",
        findings.get(0).message());
  }

  @Test
  @DisplayName("findOrphanTableAnnotations: 絞り込み時は検出を行わず、行わなかったことを報告する")
  void testFindOrphanTableAnnotationsSkippedWhenFiltered() {
    var annotations =
        Annotations.of(
            Map.of(TableKey.of("public", "removed"), new TableAnnotation("説明", "", Map.of())));

    List<ConsistencyFinding> findings =
        service.findOrphanTableAnnotations(annotations, Tables.of(List.of()), true);

    assertEquals(List.of(Kind.ORPHAN_TABLE_ANNOTATION_CHECK_SKIPPED), kinds(findings));
    assertEquals(Severity.INFO, findings.get(0).severity());
  }

  @Test
  @DisplayName("findOrphanTableAnnotations: 付帯情報が無い場合は指摘も無い")
  void testFindOrphanTableAnnotationsWithoutAnnotations() {
    assertEquals(
        List.of(),
        service.findOrphanTableAnnotations(Annotations.empty(), Tables.of(List.of()), true));
  }

  @Test
  @DisplayName("findOrphanColumnAnnotations: 実在しないカラムに対するカラム備考を指摘する")
  void testFindOrphanColumnAnnotations() {
    var orders = table("public", "orders");
    var annotations =
        Annotations.of(
            Map.of(
                TableKey.of(orders),
                new TableAnnotation("", "", Map.of("id", "ID", "removed_column", "削除済み"))));
    var detail =
        new TableDetail(
            orders,
            List.of(EntityFixtures.column("public", "orders", "id", "int", true)),
            List.of(),
            List.of());

    List<ConsistencyFinding> findings = service.findOrphanColumnAnnotations(detail, annotations);

    assertEquals(List.of(Kind.ORPHAN_COLUMN_ANNOTATION), kinds(findings));
    assertEquals(
        "Column annotation exists for a column that was not found (renamed or dropped?)."
            + " [table=public.orders, column=removed_column]",
        findings.get(0).message());
  }
}
