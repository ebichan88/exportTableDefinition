package com.dbxray.domain.service.writer.template;

import static com.dbxray.testsupport.MarkdownAssert.assertMarkdownEquals;
import static org.junit.jupiter.api.Assertions.*;

import com.dbxray.domain.model.relation.Cardinality;
import com.dbxray.domain.model.relation.DiagramColumn;
import com.dbxray.domain.model.relation.ForeignKeyEntity;
import com.dbxray.domain.model.relation.RelationType;
import com.dbxray.domain.model.table.ColumnEntity;
import com.dbxray.domain.model.table.TableEntity;
import com.dbxray.domain.model.table.TableKey;
import com.dbxray.domain.model.table.TableType;
import com.dbxray.testsupport.DiagramBoxesFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MermaidSupport のエンティティ識別子・表示ラベル生成に関するテスト */
public class MermaidSupportTest {

  @Test
  @DisplayName("mermaidId: スキーマ名とテーブル名をアンダースコアで連結する")
  void testMermaidId() {
    assertEquals("public_orders", MermaidSupport.mermaidId(TableKey.of("public", "orders")));
  }

  @Test
  @DisplayName("assignLabels: 同名テーブルが無ければテーブル名のみをラベルにする")
  void testAssignLabelsWithoutCollision() {
    var orders = TableKey.of("public", "orders");
    var customers = TableKey.of("public", "customers");

    Map<TableKey, String> labels =
        MermaidSupport.assignLabels(List.of(orders, customers), DiagramBoxesFixtures.none());

    assertEquals("orders", labels.get(orders));
    assertEquals("customers", labels.get(customers));
  }

  @Test
  @DisplayName("assignLabels: 同じ図内に同名テーブルが複数スキーマにまたがる場合はスキーマ.テーブルにする")
  void testAssignLabelsWithCollisionAcrossSchemas() {
    var salesCustomers = TableKey.of("sales", "customers");
    var masterCustomers = TableKey.of("master", "customers");

    Map<TableKey, String> labels =
        MermaidSupport.assignLabels(
            List.of(salesCustomers, masterCustomers), DiagramBoxesFixtures.none());

    assertEquals("sales.customers", labels.get(salesCustomers));
    assertEquals("master.customers", labels.get(masterCustomers));
  }

  @Test
  @DisplayName("assignLabels: 衝突していないテーブルは同じ図内に衝突テーブルがあってもテーブル名のみのまま")
  void testAssignLabelsOnlyQualifiesCollidingTables() {
    var salesCustomers = TableKey.of("sales", "customers");
    var masterCustomers = TableKey.of("master", "customers");
    var orders = TableKey.of("sales", "orders");

    Map<TableKey, String> labels =
        MermaidSupport.assignLabels(
            List.of(salesCustomers, masterCustomers, orders), DiagramBoxesFixtures.none());

    assertEquals("sales.customers", labels.get(salesCustomers));
    assertEquals("master.customers", labels.get(masterCustomers));
    assertEquals("orders", labels.get(orders));
  }

  @Test
  @DisplayName("aliasLine: 識別子とラベルを角括弧・二重引用符で結んだ1行を出力する")
  void testAliasLine() {
    assertEquals(
        "    public_orders[\"orders\"]" + System.lineSeparator(),
        MermaidSupport.aliasLine("public_orders", "orders"));
  }

  @Test
  @DisplayName("assignLabels: 論理テーブル名がある場合は「テーブル名（論理テーブル名）」にする")
  void testAssignLabelsWithLogicalName() {
    var orders = TableKey.of("public", "orders");
    var customers = TableKey.of("public", "customers");
    var boxes =
        DiagramBoxesFixtures.of(
            List.of(new TableEntity("testdb", "public", "受注", "orders", TableType.TABLE, "")),
            List.of(),
            List.of());

    Map<TableKey, String> labels = MermaidSupport.assignLabels(List.of(orders, customers), boxes);

    assertEquals("orders（受注）", labels.get(orders));
    assertEquals("customers", labels.get(customers));
  }

  @Test
  @DisplayName("aliasLine: ラベル中の改行は、図の次の行として解釈されないよう空白に置き換える")
  void testAliasLineReplacesLineBreaks() {
    assertEquals(
        "    public_orders[\"orders ``` x\"]" + System.lineSeparator(),
        MermaidSupport.aliasLine("public_orders", "orders\r\n```\nx"));
  }

  @Test
  @DisplayName("relationLine: 外部キー名の二重引用符・改行も置き換える")
  void testRelationLineQuotesForeignKeyName() {
    final ForeignKeyEntity fk =
        new ForeignKeyEntity(
            "public",
            "orders",
            "fk\"x\n```",
            List.of("customer_id"),
            "public",
            "customers",
            List.of("id"),
            Cardinality.ONE_TO_MANY,
            RelationType.PHYSICAL);
    assertEquals(
        "    a ||--o{ b : \"fk'x ```\"" + System.lineSeparator(),
        MermaidSupport.relationLine("a", fk, "b"));
  }

  @Test
  @DisplayName("aliasLine: ラベル中の二重引用符は単一引用符に置き換える")
  void testAliasLineReplacesDoubleQuote() {
    assertEquals(
        "    public_orders[\"orders（'受注'）\"]" + System.lineSeparator(),
        MermaidSupport.aliasLine("public_orders", "orders（\"受注\"）"));
  }

  @Test
  @DisplayName("attributeBlock: 型・物理カラム名・キー・論理カラム名（コメント）を1行ずつ出力する")
  void testAttributeBlock() {
    var id = new ColumnEntity("public", "orders", "受注ID", "id", "integer", "", true, true, "");
    var customerId =
        new ColumnEntity(
            "public",
            "orders",
            "顧客\"ID\"",
            "customer_id",
            "character varying(20)",
            "",
            false,
            true,
            "");
    var note = new ColumnEntity("public", "orders", "", "note", "text", "", false, false, "");
    var parentId =
        new ColumnEntity("public", "orders", "親受注ID", "parent_id", "integer", "", true, true, "");

    assertMarkdownEquals(
        """
            public_orders {
                integer id PK "受注ID"
                character_varying customer_id FK "顧客'ID'"
                text note
                integer parent_id PK, FK "親受注ID"
            }
        """,
        MermaidSupport.attributeBlock(
            "public_orders",
            List.of(
                new DiagramColumn(id, false),
                new DiagramColumn(customerId, true),
                new DiagramColumn(note, false),
                new DiagramColumn(parentId, true))));
  }

  @Test
  @DisplayName("attributeBlock: 表示するカラムが無い場合は属性ブロックを出力しない")
  void testAttributeBlockEmpty() {
    assertEquals("", MermaidSupport.attributeBlock("public_orders", List.of()));
  }
}
