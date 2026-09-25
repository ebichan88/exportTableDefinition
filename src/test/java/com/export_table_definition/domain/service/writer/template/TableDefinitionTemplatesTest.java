package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.type.Cardinality;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TableDefinitionTemplates のセクション生成テスト */
public class TableDefinitionTemplatesTest {

  private TableEntity newTable(
      String schema, String physical, String logical, String type, String def) {
    return new TableEntity("TEST_DB", schema, logical, physical, type, "", def);
  }

  @Test
  @DisplayName("fileHeader: テーブル名ヘッダー + 改行2つ")
  void testFileHeader() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    String header = TableDefinitionTemplates.fileHeader(table);
    assertTrue(header.startsWith("# orders（受注）"));
  }

  @Test
  @DisplayName("baseInfo: baseInfo の内容を含む")
  void testBaseInfo() {
    var base = new BaseInfoEntity("TEST_DB", "pg", "2025-01-01");
    String txt = TableDefinitionTemplates.baseInfo(base);
    assertTrue(txt.contains("|pg|TEST_DB|2025-01-01|"));
  }

  @Test
  @DisplayName("tableInfo: 単体テーブル行が含まれる")
  void testTableInfo() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    String info = TableDefinitionTemplates.tableInfo(table, TableAnnotation.EMPTY);
    assertTrue(info.contains("|public|受注|orders|table|"));
  }

  @Test
  @DisplayName("tableInfo: サイドカーのテーブル備考を末尾セルへ後付けし、|・改行はエスケープする")
  void testTableInfoWithAnnotation() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var annotation = new TableAnnotation("", "個人情報|取扱\n注意", Map.of());
    String info = TableDefinitionTemplates.tableInfo(table, annotation);
    assertTrue(info.contains("|public|受注|orders|table|個人情報\\|取扱<br>注意|"));
  }

  @Test
  @DisplayName("tableExplanation: サイドカーの説明が無い場合は空セクション")
  void testTableExplanationEmpty() {
    String section = TableDefinitionTemplates.tableExplanation(TableAnnotation.EMPTY);
    assertTrue(section.contains("## テーブル説明"));
    assertFalse(section.contains("ユーザー"));
  }

  @Test
  @DisplayName("tableExplanation: サイドカーの説明本文を出力する（改行はそのまま）")
  void testTableExplanationWithDescription() {
    var annotation = new TableAnnotation("1行目\n2行目", "", Map.of());
    String section = TableDefinitionTemplates.tableExplanation(annotation);
    assertTrue(section.contains("## テーブル説明"));
    assertTrue(section.contains("1行目\n2行目"));
  }

  @Test
  @DisplayName("columns: schema.table が一致する行のみ含まれる")
  void testColumnsFiltered() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var match = new ColumnEntity("public", "orders", "受注ID", "order_id", "int", "", "○", "", "");
    var other =
        new ColumnEntity("other", "customers", "顧客ID", "customer_id", "int", "", "○", "", "");
    String section =
        TableDefinitionTemplates.columns(List.of(match, other), table, TableAnnotation.EMPTY);
    assertTrue(section.contains("|1|受注ID|order_id|int|"));
    assertFalse(section.contains("顧客ID"));
  }

  @Test
  @DisplayName("columns: 物理カラム名をキーにサイドカーのカラム備考を末尾セルへ後付けする")
  void testColumnsWithAnnotation() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var match = new ColumnEntity("public", "orders", "受注ID", "order_id", "int", "", "○", "", "");
    var annotation = new TableAnnotation("", "", Map.of("order_id", "受注の主キー|連番"));
    String section = TableDefinitionTemplates.columns(List.of(match), table, annotation);
    assertTrue(section.contains("|1|受注ID|order_id|int||○|||受注の主キー\\|連番|"));
  }

  @Test
  @DisplayName("view: view でない場合は空文字")
  void testViewSectionNonView() {
    TableEntity table = newTable("public", "orders", "受注", "table", "SELECT * FROM orders");
    assertEquals("", TableDefinitionTemplates.view(table));
  }

  @Test
  @DisplayName("view: view の場合はSQLコードブロック含む")
  void testViewSectionView() {
    TableEntity table = newTable("public", "v_orders", "受注ビュー", "view", "SELECT * FROM orders");
    String section = TableDefinitionTemplates.view(table);
    assertTrue(section.contains("```sql"));
    assertTrue(section.contains("SELECT * FROM orders"));
  }

  @Test
  @DisplayName("indexes: schema.table 一致行のみ")
  void testIndexesFiltered() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var idx1 = new IndexEntity("public", "orders", "idx_orders_1", "", "", "", "", "");
    var idx2 = new IndexEntity("other", "orders", "idx_other", "", "", "", "", "");
    String section = TableDefinitionTemplates.indexes(List.of(idx1, idx2), table);
    assertTrue(section.contains("idx_orders_1"));
    assertFalse(section.contains("idx_other"));
  }

  @Test
  @DisplayName("constraints: schema.table 一致行のみ")
  void testConstraintsFiltered() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var c1 =
        new ConstraintEntity(
            "public", "orders", "pk_orders", "PRIMARY KEY", "PRIMARY KEY (order_id)", "");
    var c2 = new ConstraintEntity("x", "y", "pk_other", "PRIMARY KEY", "PRIMARY KEY (id)", "");
    String section = TableDefinitionTemplates.constraints(List.of(c1, c2), table);
    assertTrue(section.contains("pk_orders"));
    assertFalse(section.contains("pk_other"));
  }

  @Test
  @DisplayName("foreignKeys: schema.table 一致行のみ")
  void testForeignKeysFiltered() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var fk1 =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var fk2 = ForeignKeyFixtures.physical("sales", "orders", "fk_sales_orders", "sales", "y");
    String section = TableDefinitionTemplates.foreignKeys(List.of(fk1, fk2), table);
    assertTrue(section.contains("fk_orders_customer"));
    assertFalse(section.contains("fk_sales_orders"));
  }

  @Test
  @DisplayName("foreignKeys: 行の末尾に多重度の列を追加する")
  void testForeignKeysCardinalityColumn() {
    TableEntity table = newTable("public", "profiles", "プロフィール", "table", "");
    var fk =
        ForeignKeyFixtures.physical(
            "public", "profiles", "fk_profiles_user", "public", "users", Cardinality.ONE_TO_ONE);
    String section = TableDefinitionTemplates.foreignKeys(List.of(fk), table);
    assertTrue(section.contains("| No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |"));
    assertTrue(section.contains("|1|fk_profiles_user|unused|public.users|unused|1対1|"));
  }

  @Test
  @DisplayName("triggers: schema.table 一致行のみ")
  void testTriggersFiltered() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var t1 =
        new TriggerEntity(
            "public",
            "orders",
            "trg_orders",
            "BEFORE",
            "INSERT",
            "ROW",
            "public.f_orders",
            "CREATE TRIGGER trg_orders ...");
    var t2 =
        new TriggerEntity(
            "sales",
            "orders",
            "trg_sales",
            "AFTER",
            "UPDATE",
            "ROW",
            "sales.f_sales",
            "CREATE TRIGGER trg_sales ...");
    String section = TableDefinitionTemplates.triggers(List.of(t1, t2), table);
    assertTrue(section.contains("## トリガー情報"));
    assertTrue(section.contains("trg_orders"));
    assertFalse(section.contains("trg_sales"));
  }

  @Test
  @DisplayName("erDiagram: 関連テーブルがない場合はメッセージのみ")
  void testErDiagramNoRelations() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    String section = TableDefinitionTemplates.erDiagram(table, List.of(), List.of(), List.of());
    assertTrue(section.contains("関連するテーブルはありません。"));
    assertFalse(section.contains("```mermaid"));
  }

  @Test
  @DisplayName("erDiagram: 自テーブルの属性・PK表記と参照先/参照元の関係線が含まれる")
  void testErDiagramWithRelations() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var column = new ColumnEntity("public", "orders", "order_id", "character varying(20)", "○");
    var outgoing =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var incoming =
        ForeignKeyFixtures.physical("public", "items", "fk_items_orders", "public", "orders");
    String section =
        TableDefinitionTemplates.erDiagram(
            table, List.of(column), List.of(outgoing), List.of(incoming));
    assertTrue(section.contains("```mermaid"));
    assertTrue(section.contains("erDiagram"));
    // 参照先(customers) -> 自テーブル(orders)
    assertTrue(section.contains("public_customers ||--o{ public_orders : \"fk_orders_customer\""));
    // 自テーブル(orders) -> 参照元(items)
    assertTrue(section.contains("public_orders ||--o{ public_items : \"fk_items_orders\""));
    // 自テーブルの属性: 型の括弧部分は除去、空白はアンダースコア、PKマーカー付き
    assertTrue(section.contains("character_varying order_id PK"));
  }

  @Test
  @DisplayName("erDiagram: 参照先・参照元それぞれの多重度に応じた関係線を出力する")
  void testErDiagramCardinality() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var column = new ColumnEntity("public", "orders", "order_id", "character varying(20)", "○");
    var outgoing =
        ForeignKeyFixtures.physical(
            "public",
            "orders",
            "fk_orders_coupon",
            "public",
            "coupons",
            Cardinality.OPTIONAL_ONE_TO_MANY);
    var incoming =
        ForeignKeyFixtures.physical(
            "public",
            "order_details",
            "fk_details_orders",
            "public",
            "orders",
            Cardinality.ONE_TO_ONE);
    String section =
        TableDefinitionTemplates.erDiagram(
            table, List.of(column), List.of(outgoing), List.of(incoming));
    assertTrue(section.contains("public_coupons |o--o{ public_orders : \"fk_orders_coupon\""));
    assertTrue(
        section.contains("public_orders ||--o| public_order_details : \"fk_details_orders\""));
  }

  @Test
  @DisplayName("erDiagram: データ型の桁数指定は除去される")
  void testErDiagramSanitizesType() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var column = new ColumnEntity("public", "orders", "amount", "numeric(10,2)", "");
    var outgoing =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    String section =
        TableDefinitionTemplates.erDiagram(table, List.of(column), List.of(outgoing), List.of());
    assertTrue(section.contains("numeric amount"));
    assertFalse(section.contains("(10,2)"));
  }

  @Test
  @DisplayName("footer: 一覧へのリンクが含まれる")
  void testFooter() {
    var base = new BaseInfoEntity("TEST_DB", "pg", "2025-01-01");
    String footer = TableDefinitionTemplates.footer(base);
    assertTrue(footer.contains("[テーブル一覧へ](../../../tableList_TEST_DB.md)"));
    assertTrue(footer.startsWith("___"));
  }

  @Test
  @DisplayName("logicalRelations: 論理リレーションを専用セクションに、セクション内で1から採番して出力する")
  void testLogicalRelationsSection() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var rel1 =
        ForeignKeyFixtures.logical(
            "public",
            "orders",
            "rel_orders_staff",
            "staff_id",
            "public",
            "staff",
            "id",
            Cardinality.ONE_TO_MANY);
    var rel2 =
        ForeignKeyFixtures.logical(
            "public",
            "orders",
            "rel_orders_coupon",
            "coupon_code",
            "public",
            "coupons",
            "code",
            Cardinality.OPTIONAL_ONE_TO_ONE);
    String section = TableDefinitionTemplates.logicalRelations(List.of(rel1, rel2), table);

    assertTrue(section.contains("## 論理リレーション情報"));
    assertTrue(section.contains("※DBに外部キー制約は存在せず、サイドカーYAMLで宣言された関連です。"));
    assertTrue(section.contains("| No. | 関連名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |"));
    // 物理外部キーの採番とは独立に、当セクション内で1から振り直す
    assertTrue(section.contains("|1|rel_orders_staff|staff_id|public.staff|id|1対多|"));
    assertTrue(section.contains("|2|rel_orders_coupon|coupon_code|public.coupons|code|0..1対1|"));
  }

  @Test
  @DisplayName("logicalRelations: 対象が存在しない場合はセクションごと出力しない")
  void testLogicalRelationsSectionOmittedWhenEmpty() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    assertEquals("", TableDefinitionTemplates.logicalRelations(List.of(), table));
    // 他テーブルの論理リレーションしか無い場合も出力しない
    var other = ForeignKeyFixtures.logical("public", "items", "rel_items_staff", "public", "staff");
    assertEquals("", TableDefinitionTemplates.logicalRelations(List.of(other), table));
  }

  @Test
  @DisplayName("erDiagram: 論理リレーションは破線、物理外部キーは実線で描画する")
  void testErDiagramDistinguishesRelationType() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var column = new ColumnEntity("public", "orders", "order_id", "int", "○");
    var physical =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var logical =
        ForeignKeyFixtures.logical("public", "orders", "rel_orders_staff", "public", "staff");
    String section =
        TableDefinitionTemplates.erDiagram(
            table, List.of(column), List.of(physical, logical), List.of());

    assertTrue(section.contains("public_customers ||--o{ public_orders : \"fk_orders_customer\""));
    assertTrue(section.contains("public_staff ||..o{ public_orders : \"rel_orders_staff\""));
  }

  @Test
  @DisplayName("erDiagram: 被参照側の論理リレーションも破線で描画する")
  void testErDiagramIncomingLogicalIsDashed() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var column = new ColumnEntity("public", "orders", "order_id", "int", "○");
    var incoming =
        ForeignKeyFixtures.logical("public", "audit_log", "rel_audit_orders", "public", "orders");
    String section =
        TableDefinitionTemplates.erDiagram(table, List.of(column), List.of(), List.of(incoming));

    assertTrue(section.contains("public_orders ||..o{ public_audit_log : \"rel_audit_orders\""));
  }
}
