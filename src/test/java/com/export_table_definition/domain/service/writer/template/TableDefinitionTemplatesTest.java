package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.testsupport.MarkdownAssert.assertMarkdownEquals;
import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.relation.Cardinality;
import com.export_table_definition.domain.model.sidecar.TableAnnotation;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.ConstraintEntity;
import com.export_table_definition.domain.model.table.IndexEntity;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableType;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import com.export_table_definition.testsupport.EntityFixtures;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TableDefinitionTemplates のセクション生成テスト */
public class TableDefinitionTemplatesTest {

  private TableEntity newTable(
      String schema, String physical, String logical, String type, String def) {
    return new TableEntity("TEST_DB", schema, logical, physical, TableType.findByName(type), def);
  }

  @Test
  @DisplayName("fileHeader: 論理名つきのテーブル名の見出しと空行を出力する")
  void testFileHeader() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    assertMarkdownEquals(
        """
        # orders（受注）

        """,
        TableDefinitionTemplates.fileHeader(table));
  }

  @Test
  @DisplayName("baseInfo: RDBMS・DB名・作成日の表を出力する")
  void testBaseInfo() {
    var base = new BaseInfoEntity("TEST_DB", "pg", LocalDate.of(2025, 1, 1));
    assertMarkdownEquals(
        """
        ## 基本情報

        | RDBMS | データベース名 | 作成日 |
        |:---|:---|:---|
        |pg|TEST_DB|2025/01/01|

        """,
        TableDefinitionTemplates.baseInfo(base));
  }

  @Test
  @DisplayName("tableInfo: テーブル1行の表を出力する（サイドカーの備考が無い場合は末尾セルが空）")
  void testTableInfo() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    assertMarkdownEquals(
        """
        ## テーブル情報

        | スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
        |:---|:---|:---|:---|:---|
        |public|受注|orders|table||

        """,
        TableDefinitionTemplates.tableInfo(table, TableAnnotation.EMPTY));
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
  @DisplayName("tableInfo: 論理テーブル名（DBコメント由来）に含まれる|・改行は表を崩さないようエスケープする")
  void testTableInfoEscapesLogicalTableName() {
    TableEntity table = newTable("public", "orders", "受注|管理\n(旧:注文)", "table", "");
    String info = TableDefinitionTemplates.tableInfo(table, TableAnnotation.EMPTY);
    assertTrue(info.contains("|public|受注\\|管理<br>(旧:注文)|orders|table|"));
  }

  @Test
  @DisplayName("tableExplanation: サイドカーの説明が無い場合は見出しだけの空セクション")
  void testTableExplanationEmpty() {
    assertMarkdownEquals(
        """
        ## テーブル説明

        """,
        TableDefinitionTemplates.tableExplanation(TableAnnotation.EMPTY));
  }

  @Test
  @DisplayName("tableExplanation: サイドカーの説明本文を出力する（改行はそのまま）")
  void testTableExplanationWithDescription() {
    var annotation = new TableAnnotation("1行目\n2行目", "", Map.of());
    assertMarkdownEquals(
        """
        ## テーブル説明

        1行目
        2行目

        """,
        TableDefinitionTemplates.tableExplanation(annotation));
  }

  @Test
  @DisplayName("columns: 渡されたカラムを1から採番し、PK・Not Nullを○で表す")
  void testColumns() {
    var first =
        new ColumnEntity("public", "orders", "受注ID", "order_id", "int", "", true, false, "");
    var second =
        new ColumnEntity("public", "orders", "顧客ID", "customer_id", "int", "", false, true, "");
    assertMarkdownEquals(
        """
        ## カラム情報

        | No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
        |:---|:---|:---|:---|:---|:---|:---|:---|:---|
        |1|受注ID|order_id|int||○||||
        |2|顧客ID|customer_id|int|||○|||

        """,
        TableDefinitionTemplates.columns(List.of(first, second), TableAnnotation.EMPTY));
  }

  @Test
  @DisplayName("columns: 物理カラム名をキーにサイドカーのカラム備考を末尾セルへ後付けする")
  void testColumnsWithAnnotation() {
    var match =
        new ColumnEntity("public", "orders", "受注ID", "order_id", "int", "", true, false, "");
    var annotation = new TableAnnotation("", "", Map.of("order_id", "受注の主キー|連番"));
    String section = TableDefinitionTemplates.columns(List.of(match), annotation);
    assertTrue(section.contains("|1|受注ID|order_id|int||○|||受注の主キー\\|連番|"));
  }

  @Test
  @DisplayName("columns: 論理名（DBコメント由来）・デフォルト値に含まれる|・改行は表を崩さないようエスケープする")
  void testColumnsEscapesLogicalNameAndDefaultValue() {
    var column =
        new ColumnEntity(
            "public",
            "orders",
            "受注コード|旧:伝票番号\n(廃止予定)",
            "order_code",
            "text",
            "",
            false,
            true,
            "'ORD-' || nextval('orders_seq')");
    String section = TableDefinitionTemplates.columns(List.of(column), TableAnnotation.EMPTY);
    assertTrue(
        section.contains(
            "|1|受注コード\\|旧:伝票番号<br>(廃止予定)|order_code|text|||○|'ORD-' \\|\\| nextval('orders_seq')||"));
  }

  @Test
  @DisplayName("view: view でない場合は空文字")
  void testViewSectionNonView() {
    TableEntity table = newTable("public", "orders", "受注", "table", "SELECT * FROM orders");
    assertEquals("", TableDefinitionTemplates.view(table));
  }

  @Test
  @DisplayName("view: view の場合は定義をSQLコードブロックで出力する")
  void testViewSectionView() {
    TableEntity table = newTable("public", "v_orders", "受注ビュー", "view", "SELECT * FROM orders");
    assertMarkdownEquals(
        """
        ## ソース

        ```sql

        SELECT * FROM orders

        ```

        """,
        TableDefinitionTemplates.view(table));
  }

  @Test
  @DisplayName("indexes: 渡されたインデックスを1から採番して出力する")
  void testIndexes() {
    var idx1 = new IndexEntity("public", "orders", "idx_orders_1", "", false, false, "", "");
    var idx2 = new IndexEntity("public", "orders", "idx_orders_2", "", false, false, "", "");
    assertMarkdownEquals(
        """
        ## インデックス情報

        | No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
        |:---|:---|:---|:---|:---|:---|:---|
        |1|idx_orders_1||||||
        |2|idx_orders_2||||||

        """,
        TableDefinitionTemplates.indexes(List.of(idx1, idx2)));
  }

  @Test
  @DisplayName("constraints: 渡された制約を1から採番して出力する")
  void testConstraints() {
    var c1 =
        new ConstraintEntity(
            "public", "orders", "pk_orders", "PRIMARY KEY", "PRIMARY KEY (order_id)", "");
    var c2 = new ConstraintEntity("public", "orders", "uq_orders", "UNIQUE", "UNIQUE (code)", "");
    assertMarkdownEquals(
        """
        ## 制約情報

        | No. | 制約名 | 種類 | 制約定義 | 備考 |
        |:---|:---|:---|:---|:---|
        |1|pk_orders|PRIMARY KEY|PRIMARY KEY (order_id)||
        |2|uq_orders|UNIQUE|UNIQUE (code)||

        """,
        TableDefinitionTemplates.constraints(List.of(c1, c2)));
  }

  @Test
  @DisplayName("foreignKeys: 渡された外部キーを1から採番して出力する")
  void testForeignKeys() {
    var fk1 =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var fk2 = ForeignKeyFixtures.physical("public", "orders", "fk_orders_staff", "public", "staff");
    String section = TableDefinitionTemplates.foreignKeys(List.of(fk1, fk2));
    assertTrue(section.contains("|1|fk_orders_customer|"));
    assertTrue(section.contains("|2|fk_orders_staff|"));
  }

  @Test
  @DisplayName("foreignKeys: 行の末尾に多重度の列を追加する")
  void testForeignKeysCardinalityColumn() {
    var fk =
        ForeignKeyFixtures.physical(
            "public", "profiles", "fk_profiles_user", "public", "users", Cardinality.ONE_TO_ONE);
    assertMarkdownEquals(
        """
        ## 外部キー情報

        | No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
        |:---|:---|:---|:---|:---|:---|
        |1|fk_profiles_user|unused|public.users|unused|1対1|

        """,
        TableDefinitionTemplates.foreignKeys(List.of(fk)));
  }

  @Test
  @DisplayName("triggers: 渡されたトリガーを1から採番して出力する")
  void testTriggers() {
    var t1 =
        new TriggerEntity(
            "public",
            "orders",
            "trg_orders",
            "BEFORE",
            List.of("INSERT"),
            "ROW",
            "public.f_orders",
            "CREATE TRIGGER trg_orders ...");
    var t2 =
        new TriggerEntity(
            "public",
            "orders",
            "trg_orders_audit",
            "AFTER",
            List.of("UPDATE"),
            "ROW",
            "public.f_audit",
            "CREATE TRIGGER trg_orders_audit ...");
    assertMarkdownEquals(
        """
        ## トリガー情報

        | No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
        |:---|:---|:---|:---|:---|:---|
        |1|trg_orders|BEFORE|INSERT|ROW|CREATE TRIGGER trg_orders ...|
        |2|trg_orders_audit|AFTER|UPDATE|ROW|CREATE TRIGGER trg_orders_audit ...|

        """,
        TableDefinitionTemplates.triggers(List.of(t1, t2)));
  }

  @Test
  @DisplayName("indexes/constraints/triggers: 定義に含まれる|は表を崩さないようエスケープする")
  void testDefinitionsEscapePipe() {
    var idx =
        new IndexEntity(
            "public",
            "orders",
            "idx_orders_code",
            "btree",
            false,
            false,
            "CREATE INDEX idx_orders_code ON public.orders USING btree (((a || b)))",
            "");
    var c =
        new ConstraintEntity(
            "public", "orders", "chk_code", "CHECK", "CHECK ((code ~ '^(A|B)$'::text))", "");
    var t =
        new TriggerEntity(
            "public",
            "orders",
            "trg_orders",
            "BEFORE",
            List.of("INSERT"),
            "ROW",
            "public.f_orders",
            "CREATE TRIGGER trg_orders WHEN ((new.a || new.b) IS NOT NULL)");
    assertTrue(
        TableDefinitionTemplates.indexes(List.of(idx)).contains("USING btree (((a \\|\\| b)))|"));
    assertTrue(
        TableDefinitionTemplates.constraints(List.of(c))
            .contains("|CHECK ((code ~ '^(A\\|B)$'::text))|"));
    assertTrue(
        TableDefinitionTemplates.triggers(List.of(t))
            .contains("WHEN ((new.a \\|\\| new.b) IS NOT NULL)|"));
  }

  @Test
  @DisplayName("indexes/constraints: 備考（DBコメント由来）に含まれる|・改行は表を崩さないようエスケープする")
  void testRemarksEscapeTableCell() {
    var idx =
        new IndexEntity(
            "public",
            "orders",
            "idx_orders_code",
            "btree",
            false,
            false,
            "",
            "運用メモ|旧インデックス\n(削除予定)");
    var c = new ConstraintEntity("public", "orders", "chk_code", "CHECK", "", "運用メモ|注意事項\n(要確認)");
    assertTrue(
        TableDefinitionTemplates.indexes(List.of(idx)).contains("|運用メモ\\|旧インデックス<br>(削除予定)|"));
    assertTrue(TableDefinitionTemplates.constraints(List.of(c)).contains("|運用メモ\\|注意事項<br>(要確認)|"));
  }

  @Test
  @DisplayName("erDiagram: 関連テーブルがない場合はMermaidを出力せずメッセージのみ")
  void testErDiagramNoRelations() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    assertMarkdownEquals(
        """
        ## ER図

        関連するテーブルはありません。

        """,
        TableDefinitionTemplates.erDiagram(table, List.of(), List.of(), List.of()));
  }

  @Test
  @DisplayName("erDiagram: 参照先/参照元の関係線と、自テーブルの属性（型の括弧除去・空白はアンダースコア・PK表記）を出力する")
  void testErDiagramWithRelations() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var column =
        EntityFixtures.column("public", "orders", "order_id", "character varying(20)", true);
    var outgoing =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    var incoming =
        ForeignKeyFixtures.physical("public", "items", "fk_items_orders", "public", "orders");
    // 参照先(customers) -> 自テーブル(orders) -> 参照元(items) の順に関係線を出力する
    assertMarkdownEquals(
        """
        ## ER図

        ```mermaid
        erDiagram
            public_customers ||--o{ public_orders : "fk_orders_customer"
            public_orders ||--o{ public_items : "fk_items_orders"
            public_orders {
                character_varying order_id PK
            }
        ```

        """,
        TableDefinitionTemplates.erDiagram(
            table, List.of(column), List.of(outgoing), List.of(incoming)));
  }

  @Test
  @DisplayName("erDiagram: 参照先・参照元それぞれの多重度に応じた関係線を出力する")
  void testErDiagramCardinality() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var column =
        EntityFixtures.column("public", "orders", "order_id", "character varying(20)", true);
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
    var column = EntityFixtures.column("public", "orders", "amount", "numeric(10,2)", false);
    var outgoing =
        ForeignKeyFixtures.physical(
            "public", "orders", "fk_orders_customer", "public", "customers");
    String section =
        TableDefinitionTemplates.erDiagram(table, List.of(column), List.of(outgoing), List.of());
    assertTrue(section.contains("numeric amount"));
    assertFalse(section.contains("(10,2)"));
  }

  @Test
  @DisplayName("footer: 区切り線とテーブル一覧へのリンクを出力する")
  void testFooter() {
    var base = new BaseInfoEntity("TEST_DB", "pg", LocalDate.of(2025, 1, 1));
    assertMarkdownEquals(
        """
        ___

        [テーブル一覧へ](../../../tableList_TEST_DB.md)
        """,
        TableDefinitionTemplates.footer(base));
  }

  @Test
  @DisplayName("logicalRelations: 論理リレーションを専用セクションに、セクション内で1から採番して出力する")
  void testLogicalRelationsSection() {
    var rel1 =
        ForeignKeyFixtures.logical(
            "public",
            "orders",
            "rel_orders_staff",
            List.of("staff_id"),
            "public",
            "staff",
            List.of("id"),
            Cardinality.ONE_TO_MANY);
    var rel2 =
        ForeignKeyFixtures.logical(
            "public",
            "orders",
            "rel_orders_coupon",
            List.of("coupon_code"),
            "public",
            "coupons",
            List.of("code"),
            Cardinality.OPTIONAL_ONE_TO_ONE);
    // 物理外部キーの採番とは独立に、当セクション内で1から振り直す
    assertMarkdownEquals(
        """
        ## 論理リレーション情報

        ※DBに外部キー制約は存在せず、サイドカーYAMLで宣言された関連です。

        | No. | 関連名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
        |:---|:---|:---|:---|:---|:---|
        |1|rel_orders_staff|staff_id|public.staff|id|1対多|
        |2|rel_orders_coupon|coupon_code|public.coupons|code|0..1対1|

        """,
        TableDefinitionTemplates.logicalRelations(List.of(rel1, rel2)));
  }

  @Test
  @DisplayName("logicalRelations: 対象が存在しない場合はセクションごと出力しない")
  void testLogicalRelationsSectionOmittedWhenEmpty() {
    assertEquals("", TableDefinitionTemplates.logicalRelations(List.of()));
  }

  @Test
  @DisplayName("erDiagram: 論理リレーションは破線、物理外部キーは実線で描画する")
  void testErDiagramDistinguishesRelationType() {
    TableEntity table = newTable("public", "orders", "受注", "table", "");
    var column = EntityFixtures.column("public", "orders", "order_id", "int", true);
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
    var column = EntityFixtures.column("public", "orders", "order_id", "int", true);
    var incoming =
        ForeignKeyFixtures.logical("public", "audit_log", "rel_audit_orders", "public", "orders");
    String section =
        TableDefinitionTemplates.erDiagram(table, List.of(column), List.of(), List.of(incoming));

    assertTrue(section.contains("public_orders ||..o{ public_audit_log : \"rel_audit_orders\""));
  }

  @Test
  @DisplayName("viewpoints: 所属する観点が無い場合はセクションごと出力しない（観点を導入しても定義書は変わらない）")
  void testViewpointsEmpty() {
    var base = new BaseInfoEntity("TEST_DB", "pg", LocalDate.of(2025, 1, 1));
    assertEquals("", TableDefinitionTemplates.viewpoints(List.of(), base));
  }

  @Test
  @DisplayName("viewpoints: 所属する観点の表示名と、観点ページへの相対リンクを宣言順に出力する")
  void testViewpoints() {
    var base = new BaseInfoEntity("TEST_DB", "pg", LocalDate.of(2025, 1, 1));
    assertMarkdownEquals(
        """
        ## 所属する観点

        * [受注管理](../../../viewpoint_TEST_DB_order.md) \s
        * [master](../../../viewpoint_TEST_DB_master.md) \s

        """,
        TableDefinitionTemplates.viewpoints(
            List.of(
                Viewpoint.of("order", "受注管理", "", List.of("orders")),
                Viewpoint.of("master", "", "", List.of("orders"))),
            base));
  }
}
