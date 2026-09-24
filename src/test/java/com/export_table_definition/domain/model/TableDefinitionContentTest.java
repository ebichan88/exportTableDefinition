package com.export_table_definition.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.export_table_definition.domain.model.collection.Columns;
import com.export_table_definition.domain.model.collection.Constraints;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.collection.Indexes;
import com.export_table_definition.domain.model.collection.Triggers;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;

/**
 * TableDefinitionContent.assemble の組み立てに関するテスト<br>
 * 各集合クラスから対象テーブル分のみが正しく抽出され、被参照側の外部キーも
 * incomingForeignKeysとして分離されることを検証する
 */
public class TableDefinitionContentTest {

    private TableEntity newTable(String schema, String physical) {
        return new TableEntity("testdb", schema, "", physical, "table", "", "", "");
    }

    @Test
    @DisplayName("assemble: 対象テーブルに属する情報のみを抽出し、他テーブルの情報は含まれない")
    void testAssembleExtractsOnlyTargetTableInformation() {
        var baseInfo = new BaseInfoEntity("testdb", "unused");
        var target = newTable("public", "orders");

        var ownColumn = new ColumnEntity("public", "orders", "unused", "id", "int", "○");
        var otherColumn = new ColumnEntity("public", "customers", "unused", "id", "int", "○");
        var columns = Columns.of(List.of(ownColumn, otherColumn));

        var ownIndex = new IndexEntity("public", "orders", "unused");
        var indexes = Indexes.of(List.of(ownIndex, new IndexEntity("public", "customers", "unused")));

        var ownConstraint = new ConstraintEntity("public", "orders", "unused");
        var constraints = Constraints.of(List.of(ownConstraint, new ConstraintEntity("public", "customers", "unused")));

        var outgoingFk = new ForeignKeyEntity("public", "orders", "unused", "fk_orders_customer", "public",
                "customers");
        var incomingFk = new ForeignKeyEntity("public", "items", "unused", "fk_items_orders", "public", "orders");
        var foreignKeys = ForeignKeys.of(List.of(outgoingFk, incomingFk));

        var ownTrigger = new TriggerEntity("public", "orders", "unused", "unused");
        var triggers = Triggers.of(List.of(ownTrigger, new TriggerEntity("public", "customers", "unused", "unused")));

        Path baseDir = Path.of("output");
        TableDefinitionContent content = TableDefinitionContent.assemble(baseInfo, target, columns, indexes,
                constraints, foreignKeys, triggers, baseDir);

        assertSame(baseInfo, content.baseInfo());
        assertSame(target, content.table());
        assertSame(baseDir, content.outputBaseDir());
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
        var baseInfo = new BaseInfoEntity("testdb", "unused");
        var target = newTable("public", "empty_table");

        TableDefinitionContent content = TableDefinitionContent.assemble(baseInfo, target, Columns.of(List.of()),
                Indexes.of(List.of()), Constraints.of(List.of()), ForeignKeys.of(List.of()), Triggers.of(List.of()),
                Path.of("output"));

        assertEquals(List.of(), content.columns());
        assertEquals(List.of(), content.indexes());
        assertEquals(List.of(), content.constraints());
        assertEquals(List.of(), content.foreignKeys());
        assertEquals(List.of(), content.incomingForeignKeys());
        assertEquals(List.of(), content.triggers());
    }
}
