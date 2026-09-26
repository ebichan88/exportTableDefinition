package com.export_table_definition.domain.model.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.type.Cardinality;
import com.export_table_definition.domain.model.type.RelationType;
import com.export_table_definition.domain.model.type.TableType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TableSnapshot のテーブル定義出力情報からの変換に関するテスト */
public class TableSnapshotTest {

  private static final BaseInfoEntity BASE_INFO =
      new BaseInfoEntity("testdb", "PostgreSQL", "2026/09/25");

  private TableDefinitionContent content(
      TableEntity table,
      List<ColumnEntity> columns,
      List<IndexEntity> indexes,
      List<ConstraintEntity> constraints,
      List<ForeignKeyEntity> foreignKeys,
      List<ForeignKeyEntity> logicalRelations,
      List<TriggerEntity> triggers,
      TableAnnotation annotation) {
    return new TableDefinitionContent(
        BASE_INFO,
        table,
        columns,
        indexes,
        constraints,
        foreignKeys,
        logicalRelations,
        List.of(),
        triggers,
        annotation);
  }

  @Test
  @DisplayName("of: テーブル・カラムの各項目を個別の値として保持し、サイドカーの付帯情報をマージする")
  void testOfConvertsTableAndColumns() {
    var table = new TableEntity("testdb", "public", "受注", "orders", TableType.TABLE, "");
    var id = new ColumnEntity("public", "orders", "受注ID", "id", "integer", "", true, true, " ");
    var amount =
        new ColumnEntity(
            "public", "orders", "", "amount", "numeric(10,2)", "10,2", false, false, "0");
    var annotation = new TableAnnotation("受注を管理する。", "個人情報を含む", Map.of("amount", "税込"));

    TableSnapshot snapshot =
        TableSnapshot.of(
            content(
                table,
                List.of(id, amount),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                annotation));

    assertEquals("public", snapshot.schema());
    assertEquals("orders", snapshot.name());
    assertEquals("受注", snapshot.logicalName());
    assertEquals("table", snapshot.type());
    assertEquals("受注を管理する。", snapshot.description());
    assertEquals("個人情報を含む", snapshot.remarks());
    assertNull(snapshot.definition());
    assertEquals(
        List.of(
            new TableSnapshot.Column("id", "受注ID", "integer", null, true, true, null, null),
            new TableSnapshot.Column(
                "amount", null, "numeric(10,2)", "10,2", false, false, "0", "税込")),
        snapshot.columns());
  }

  @Test
  @DisplayName("of: インデックス・制約・外部キー・論理リレーション・トリガーを構造化して保持する")
  void testOfConvertsRelatedObjects() {
    var table = new TableEntity("testdb", "public", "", "orders", TableType.TABLE, "");
    var index =
        new IndexEntity(
            "public", "orders", "orders_pkey", "btree", true, true, "CREATE UNIQUE INDEX ...", "");
    var constraint =
        new ConstraintEntity(
            "public", "orders", "chk_amount", "CHECK", "CHECK ((amount >= 0))", "金額は0以上");
    var foreignKey =
        new ForeignKeyEntity(
            "public",
            "orders",
            "fk_orders_item",
            List.of("item_id", "item_seq"),
            "master",
            "items",
            List.of("id", "seq"),
            Cardinality.OPTIONAL_ONE_TO_MANY,
            RelationType.PHYSICAL);
    var logicalRelation =
        ForeignKeyEntity.logical(
            "public",
            "orders",
            "orders_user_id_lrel",
            List.of("user_id"),
            "public",
            "users",
            List.of("id"),
            Cardinality.ONE_TO_MANY);
    var trigger =
        new TriggerEntity(
            "public",
            "orders",
            "trg_orders",
            "BEFORE",
            List.of("INSERT", "UPDATE"),
            "ROW",
            "public.f_orders",
            "CREATE TRIGGER trg_orders ...");

    TableSnapshot snapshot =
        TableSnapshot.of(
            content(
                table,
                List.of(),
                List.of(index),
                List.of(constraint),
                List.of(foreignKey),
                List.of(logicalRelation),
                List.of(trigger),
                TableAnnotation.EMPTY));

    assertNull(snapshot.logicalName());
    assertNull(snapshot.description());
    assertEquals(
        List.of(
            new TableSnapshot.Index(
                "orders_pkey", "btree", true, true, "CREATE UNIQUE INDEX ...", null)),
        snapshot.indexes());
    assertEquals(
        List.of(
            new TableSnapshot.Constraint("chk_amount", "CHECK", "CHECK ((amount >= 0))", "金額は0以上")),
        snapshot.constraints());
    assertEquals(
        List.of(
            new TableSnapshot.Relation(
                "fk_orders_item",
                List.of("item_id", "item_seq"),
                "master",
                "items",
                List.of("id", "seq"),
                Cardinality.OPTIONAL_ONE_TO_MANY)),
        snapshot.foreignKeys());
    assertEquals(
        List.of(
            new TableSnapshot.Relation(
                "orders_user_id_lrel",
                List.of("user_id"),
                "public",
                "users",
                List.of("id"),
                Cardinality.ONE_TO_MANY)),
        snapshot.logicalRelations());
    assertEquals(
        List.of(
            new TableSnapshot.Trigger(
                "trg_orders",
                "BEFORE",
                List.of("INSERT", "UPDATE"),
                "ROW",
                "public.f_orders",
                "CREATE TRIGGER trg_orders ...")),
        snapshot.triggers());
  }

  @Test
  @DisplayName("of: view・materialized viewはソース定義を保持する")
  void testOfKeepsViewDefinition() {
    var view =
        new TableEntity(
            "testdb", "public", "", "v_orders", TableType.VIEW, " SELECT id FROM orders;");

    TableSnapshot snapshot =
        TableSnapshot.of(
            content(
                view,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                TableAnnotation.EMPTY));

    assertEquals("view", snapshot.type());
    assertEquals(" SELECT id FROM orders;", snapshot.definition());
  }
}
