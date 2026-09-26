package com.export_table_definition.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.collection.Columns;
import com.export_table_definition.domain.model.collection.Constraints;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.collection.Indexes;
import com.export_table_definition.domain.model.collection.Triggers;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TableDefinitionContent.assemble の組み立てに関するテスト<br>
 * 各集合クラスから対象テーブル分のみが正しく抽出され、被参照側の外部キーも incomingForeignKeysとして分離されることを検証する
 */
public class TableDefinitionContentTest {

  private TableEntity newTable(String schema, String physical) {
    return new TableEntity("testdb", schema, "", physical, "table", "");
  }

  @Test
  @DisplayName("assemble: 対象テーブルに属する情報のみを抽出し、他テーブルの情報は含まれない")
  void testAssembleExtractsOnlyTargetTableInformation() {
    var baseInfo = new BaseInfoEntity("testdb", "unused", "unused");
    var target = newTable("public", "orders");

    var ownColumn = new ColumnEntity("public", "orders", "id", "int", true);
    var otherColumn = new ColumnEntity("public", "customers", "id", "int", true);
    var columns = Columns.of(List.of(ownColumn, otherColumn));

    var ownIndex = new IndexEntity("public", "orders");
    var indexes = Indexes.of(List.of(ownIndex, new IndexEntity("public", "customers")));

    var ownConstraint = new ConstraintEntity("public", "orders");
    var constraints =
        Constraints.of(List.of(ownConstraint, new ConstraintEntity("public", "customers")));

    var outgoingFk =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var incomingFk =
        ForeignKeyFixtures.physical("public", "items", "fk_items_orders", "public", "orders");
    var foreignKeys = ForeignKeys.of(List.of(outgoingFk, incomingFk));

    var ownTrigger = new TriggerEntity("public", "orders");
    var triggers = Triggers.of(List.of(ownTrigger, new TriggerEntity("public", "customers")));

    var ownAnnotation = new TableAnnotation("受注テーブル", "備考", java.util.Map.of("id", "主キー"));
    var annotations =
        Annotations.of(
            java.util.Map.of(
                TableKey.of("public", "orders"),
                ownAnnotation,
                TableKey.of("public", "customers"),
                new TableAnnotation("顧客テーブル", "", java.util.Map.of())));

    TableDefinitionContent content =
        TableDefinitionContent.assemble(
            baseInfo, target, columns, indexes, constraints, foreignKeys, triggers, annotations);

    assertSame(baseInfo, content.baseInfo());
    assertSame(target, content.table());
    assertSame(ownAnnotation, content.annotation());
    assertEquals(List.of(ownColumn), content.columns());
    assertEquals(List.of(ownIndex), content.indexes());
    assertEquals(List.of(ownConstraint), content.constraints());
    assertEquals(List.of(outgoingFk), content.foreignKeys());
    assertEquals(List.of(incomingFk), content.incomingForeignKeys());
    assertEquals(List.of(ownTrigger), content.triggers());

    // otherテーブルの情報が紛れ込んでいないことの確認
    assertFalse(content.columns().contains(otherColumn));
  }

  @Test
  @DisplayName("assemble: 関連する情報が存在しない場合は空リストになる")
  void testAssembleWithNoRelatedInformationReturnsEmptyLists() {
    var baseInfo = new BaseInfoEntity("testdb", "unused", "unused");
    var target = newTable("public", "empty_table");

    TableDefinitionContent content =
        TableDefinitionContent.assemble(
            baseInfo,
            target,
            Columns.of(List.of()),
            Indexes.of(List.of()),
            Constraints.of(List.of()),
            ForeignKeys.of(List.of()),
            Triggers.of(List.of()),
            Annotations.empty());

    // 付帯情報が存在しないテーブルには空の付帯情報が設定される
    assertSame(TableAnnotation.EMPTY, content.annotation());
    assertEquals(List.of(), content.columns());
    assertEquals(List.of(), content.indexes());
    assertEquals(List.of(), content.constraints());
    assertEquals(List.of(), content.foreignKeys());
    assertEquals(List.of(), content.incomingForeignKeys());
    assertEquals(List.of(), content.triggers());
  }

  @Test
  @DisplayName("assemble: 物理外部キーと論理リレーションを由来ごとに分けて保持する")
  void testAssembleSplitsRelationsByType() {
    var table = newTable("public", "orders");
    var physical =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var logical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_staff", "public", "staff");
    var foreignKeys = ForeignKeys.of(List.of(physical, logical));

    var content =
        TableDefinitionContent.assemble(
            new BaseInfoEntity("testdb", "unused", "unused"),
            table,
            Columns.of(List.of()),
            Indexes.of(List.of()),
            Constraints.of(List.of()),
            foreignKeys,
            Triggers.of(List.of()),
            Annotations.empty());

    assertEquals(List.of(physical), content.foreignKeys());
    assertEquals(List.of(logical), content.logicalRelations());
  }

  @Test
  @DisplayName("outgoingRelations: ER図用に物理外部キーと論理リレーションを結合して返す")
  void testOutgoingRelationsCombinesBothTypes() {
    var table = newTable("public", "orders");
    var physical =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var logical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_staff", "public", "staff");
    var foreignKeys = ForeignKeys.of(List.of(physical, logical));

    var content =
        TableDefinitionContent.assemble(
            new BaseInfoEntity("testdb", "unused", "unused"),
            table,
            Columns.of(List.of()),
            Indexes.of(List.of()),
            Constraints.of(List.of()),
            foreignKeys,
            Triggers.of(List.of()),
            Annotations.empty());

    assertEquals(List.of(physical, logical), content.outgoingRelations());
  }
}
