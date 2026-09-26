package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.collection.ForeignKeyGroup;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.type.Cardinality;
import com.export_table_definition.domain.model.type.TableType;
import com.export_table_definition.domain.model.value.TableKey;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ErDiagramTemplates のセクション生成テスト */
public class ErDiagramTemplatesTest {

  private BaseInfoEntity baseInfo() {
    return new BaseInfoEntity("TEST_DB", "pg", "2025-01-01");
  }

  private TableEntity newTable(String schema, String physical, String logical) {
    return new TableEntity("TEST_DB", schema, logical, physical, TableType.TABLE, "");
  }

  private ForeignKeyEntity newFk(
      String schema, String table, String fkName, String refSchema, String refTable) {
    return ForeignKeyFixtures.physical(schema, table, fkName, refSchema, refTable);
  }

  /** ER図セクションを、ノード算出込みで生成するテスト用ヘルパー */
  private String erDiagram(List<ForeignKeyEntity> foreignKeys, int maxNodes) {
    return ErDiagramTemplates.erDiagram(ForeignKeyGroup.of(foreignKeys), maxNodes);
  }

  @Test
  @DisplayName("fileHeader: タイトルとDB名を含むヘッダーを生成する")
  void testFileHeader() {
    String header = ErDiagramTemplates.fileHeader("ER図一覧", baseInfo());
    assertTrue(header.startsWith("# ER図一覧（DB名：TEST_DB）"));
  }

  @Test
  @DisplayName("schemaFileHeader: DB名とスキーマ名を含むヘッダーを生成する")
  void testSchemaFileHeader() {
    String header = ErDiagramTemplates.schemaFileHeader("public", baseInfo());
    assertTrue(header.startsWith("# ER図（DB名：TEST_DB / スキーマ名：public）"));
  }

  @Test
  @DisplayName("erDiagram: 外部キーがない場合はMermaidを出力しない")
  void testErDiagramNoForeignKeys() {
    String section = erDiagram(List.of(), 80);
    assertTrue(section.contains("外部キーによる関連を持つテーブルはありません。"));
    assertFalse(section.contains("```mermaid"));
  }

  @Test
  @DisplayName("erDiagram: 参照先 ||--o{ 参照元 の向きで関係線を出力する")
  void testErDiagramRelationDirection() {
    var fk = newFk("public", "orders", "fk_orders_customer", "public", "customers");
    String section = erDiagram(List.of(fk), 80);
    assertTrue(section.contains("```mermaid"));
    assertTrue(section.contains("erDiagram"));
    assertTrue(section.contains("public_customers ||--o{ public_orders : \"fk_orders_customer\""));
  }

  @Test
  @DisplayName("erDiagram: 外部キーの多重度に応じた関係線を出力する")
  void testErDiagramCardinality() {
    var oneToOne =
        ForeignKeyFixtures.physical(
            "public", "profiles", "fk_profiles_user", "public", "users", Cardinality.ONE_TO_ONE);
    var optional =
        ForeignKeyFixtures.physical(
            "public",
            "orders",
            "fk_orders_coupon",
            "public",
            "coupons",
            Cardinality.OPTIONAL_ONE_TO_MANY);
    String section = erDiagram(List.of(oneToOne, optional), 80);
    assertTrue(section.contains("public_users ||--o| public_profiles : \"fk_profiles_user\""));
    assertTrue(section.contains("public_coupons |o--o{ public_orders : \"fk_orders_coupon\""));
  }

  @Test
  @DisplayName("erDiagram: 属性（カラム）は出力しない")
  void testErDiagramHasNoAttributes() {
    var fk = newFk("public", "orders", "fk_orders_customer", "public", "customers");
    String section = erDiagram(List.of(fk), 80);
    // 属性を持つ箱は「<エンティティ名> {」で始まるブロックとして出力される
    assertFalse(section.contains(" {" + System.lineSeparator()));
  }

  @Test
  @DisplayName("erDiagram: スキーマを跨ぐ外部キーも関係線として出力する")
  void testErDiagramCrossSchemaRelation() {
    var fk = newFk("sales", "orders", "fk_orders_customer", "master", "customers");
    String section = erDiagram(List.of(fk), 80);
    assertTrue(section.contains("master_customers ||--o{ sales_orders : \"fk_orders_customer\""));
  }

  @Test
  @DisplayName("erDiagram: サニタイズ後に識別子が衝突する場合は連番で一意化する")
  void testErDiagramResolvesIdCollision() {
    // sanitizeIdentifier により "a_b" + "c" と "a" + "b_c" はいずれも "a_b_c" に潰れる
    var fk1 = newFk("a_b", "c", "fk1", "ref", "t");
    var fk2 = newFk("a", "b_c", "fk2", "ref", "t");
    String section = erDiagram(List.of(fk1, fk2), 80);
    // ノードはスキーマ名・テーブル名順に並ぶため "a"."b_c" が先に採番される
    assertTrue(section.contains("ref_t ||--o{ a_b_c_2 : \"fk1\""));
    assertTrue(section.contains("ref_t ||--o{ a_b_c : \"fk2\""));
  }

  @Test
  @DisplayName("erDiagram: ノード数が上限を超える場合はMermaidを出力せず省略メッセージを返す")
  void testErDiagramOmittedOnOverflow() {
    var fk1 = newFk("public", "orders", "fk_orders_customer", "public", "customers");
    var fk2 = newFk("public", "items", "fk_items_orders", "public", "orders");
    // ノードは orders / customers / items の3件のため、上限2件で超過する
    String section = erDiagram(List.of(fk1, fk2), 2);
    assertFalse(section.contains("```mermaid"));
    assertTrue(section.contains("ER図に描画するテーブル数が3件となり、上限（erDiagramMaxNodes = 2件）を超えるため描画を省略しました。"));
  }

  @Test
  @DisplayName("erDiagram: 上限が0以下の場合は件数に関わらずMermaidを出力する")
  void testErDiagramNoLimit() {
    var fk1 = newFk("public", "orders", "fk_orders_customer", "public", "customers");
    var fk2 = newFk("public", "items", "fk_items_orders", "public", "orders");
    assertTrue(erDiagram(List.of(fk1, fk2), 0).contains("```mermaid"));
  }

  @Test
  @DisplayName("diagramTableLine: 定義書へのリンク付きで1行分を出力する（既存一覧と同じ[■]表記）")
  void testDiagramTableLine() {
    var table = newTable("public", "orders", "受注");
    String line = ErDiagramTemplates.diagramTableLine(1, TableKey.of(table), table);
    assertTrue(
        line.startsWith(
            "| 1 | public | orders | 受注 | table | [■](./TEST_DB/public/table/orders.md) |"));
  }

  @Test
  @DisplayName("diagramTableLine: 出力対象範囲外のテーブルはリンクを張らない")
  void testDiagramTableLineWithoutDefinition() {
    String line =
        ErDiagramTemplates.diagramTableLine(1, TableKey.of("external", "master_data"), null);
    assertTrue(line.startsWith("| 1 | external | master_data |  |  | - |"));
  }

  @Test
  @DisplayName("foreignKeyTableLine: 参照元・外部キー名・参照先を出力する")
  void testForeignKeyTableLine() {
    var fk = newFk("sales", "orders", "fk_cross", "master", "customers");
    String line = ErDiagramTemplates.foreignKeyTableLine(1, fk);
    assertTrue(line.startsWith("| 1 | sales.orders | fk_cross | master.customers |"));
  }

  @Test
  @DisplayName("groupFileHeader: DB名・スキーマ名・グループ番号を含むヘッダーを生成する")
  void testGroupFileHeader() {
    String header = ErDiagramTemplates.groupFileHeader("public", 2, baseInfo());
    assertTrue(header.startsWith("# ER図（DB名：TEST_DB / スキーマ名：public / グループ2）"));
  }

  @Test
  @DisplayName("groupedMessage: 分割した理由とグループ数を出力する")
  void testGroupedMessage() {
    String section = ErDiagramTemplates.groupedMessage(200, 80, 3);
    assertTrue(section.startsWith("## ER図"));
    assertTrue(section.contains("ER図に描画するテーブル数が200件となり、上限（erDiagramMaxNodes = 80件）を超えるため、"));
    assertTrue(section.contains("外部キーで繋がったテーブルのまとまりごとに3個のグループへ分割しました。"));
    assertFalse(section.contains("```mermaid"));
  }

  @Test
  @DisplayName("groupIndexLine: グループの規模と主なテーブルを出力する")
  void testGroupIndexLine() {
    String line =
        ErDiagramTemplates.groupIndexLine(
            1, 12, 15, TableKey.of("public", "orders"), "./erDiagram_TEST_DB_public_group1.md");
    assertTrue(
        line.startsWith(
            "| 1 | 12 | 15 | public.orders | [■](./erDiagram_TEST_DB_public_group1.md) |"));
  }

  @Test
  @DisplayName("groupFooter: スキーマのER図へ戻るリンクを含む")
  void testGroupFooter() {
    String footer = ErDiagramTemplates.groupFooter("public", baseInfo());
    assertTrue(footer.startsWith("___"));
    assertTrue(footer.contains("[スキーマのER図へ](./erDiagram_TEST_DB_public.md)"));
    assertTrue(footer.contains("[ER図一覧へ](./erDiagramList_TEST_DB.md)"));
    assertTrue(footer.contains("[テーブル一覧へ](./tableList_TEST_DB.md)"));
  }

  @Test
  @DisplayName("schemaIndex: スキーマ別ER図へのリンクとテーブル数を出力する")
  void testSchemaIndex() {
    final Map<String, List<TableEntity>> tablesBySchema = new LinkedHashMap<>();
    tablesBySchema.put(
        "public", List.of(newTable("public", "orders", "受注"), newTable("public", "items", "明細")));
    tablesBySchema.put("master", List.of(newTable("master", "customers", "顧客")));
    String section = ErDiagramTemplates.schemaIndex(baseInfo(), tablesBySchema);
    assertTrue(section.contains("| 1 | public | 2 | [■](./erDiagram_TEST_DB_public.md) |"));
    assertTrue(section.contains("| 2 | master | 1 | [■](./erDiagram_TEST_DB_master.md) |"));
  }

  @Test
  @DisplayName("schemaFooter: ER図一覧とテーブル一覧へのリンクを含む")
  void testSchemaFooter() {
    String footer = ErDiagramTemplates.schemaFooter(baseInfo());
    assertTrue(footer.startsWith("___"));
    assertTrue(footer.contains("[ER図一覧へ](./erDiagramList_TEST_DB.md)"));
    assertTrue(footer.contains("[テーブル一覧へ](./tableList_TEST_DB.md)"));
  }
}
