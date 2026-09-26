package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.testsupport.MarkdownAssert.assertMarkdownEquals;
import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.relation.ForeignKeyGroup;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableType;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import com.export_table_definition.domain.model.viewpoint.ViewpointContent;
import com.export_table_definition.testsupport.ForeignKeyFixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ViewpointTemplates のセクション生成テスト */
public class ViewpointTemplatesTest {

  private BaseInfoEntity baseInfo() {
    return new BaseInfoEntity("TEST_DB", "pg", LocalDate.of(2025, 1, 1));
  }

  private TableEntity newTable(String schema, String physical, String logical) {
    return new TableEntity("TEST_DB", schema, logical, physical, TableType.TABLE, "");
  }

  @Test
  @DisplayName("fileHeader: 観点の表示名とDB名の見出しと空行を出力する")
  void testFileHeader() {
    assertMarkdownEquals(
        """
        # 観点：受注管理（DB名：TEST_DB）

        """,
        ViewpointTemplates.fileHeader(
            Viewpoint.of("order", "受注管理", "", List.of("orders")), baseInfo()));
  }

  @Test
  @DisplayName("description: 説明を自由記述のブロックとしてそのまま出力し、説明が無い場合はセクションごと省く")
  void testDescription() {
    assertMarkdownEquals(
        """
        ## 説明

        受注から出荷指示まで。
        請求は含まない。

        """,
        ViewpointTemplates.description(
            Viewpoint.of("order", "", "受注から出荷指示まで。\n請求は含まない。\n", List.of("orders"))));
    assertEquals(
        "", ViewpointTemplates.description(Viewpoint.of("order", "", "", List.of("orders"))));
  }

  @Test
  @DisplayName("erDiagram: 所属テーブル同士の関連が無い場合はMermaidを出力せずメッセージのみ")
  void testErDiagramNoRelations() {
    assertMarkdownEquals(
        """
        ## ER図

        所属テーブル同士の関連（外部キー・論理リレーション）はありません。

        """,
        ViewpointTemplates.erDiagram(ForeignKeyGroup.of(List.of()), 80));
  }

  @Test
  @DisplayName("erDiagram: 関連がある場合はスキーマ別ER図と同じ描画を行う")
  void testErDiagramDelegatesToSchemaErDiagram() {
    var group =
        ForeignKeyGroup.of(
            List.of(
                ForeignKeyFixtures.physical(
                    "public", "orders", "fk_orders_customer", "public", "customer")));

    assertEquals(ErDiagramTemplates.erDiagram(group, 80), ViewpointTemplates.erDiagram(group, 80));
  }

  @Test
  @DisplayName("noTables: 所属テーブルが無いことを示すセクションを出力する")
  void testNoTables() {
    assertMarkdownEquals(
        """
        ## 所属テーブル

        出力対象のテーブルのうち、この観点に所属するものはありません。

        """,
        ViewpointTemplates.noTables());
  }

  @Test
  @DisplayName("outsideRelations: 観点外のテーブルとの関連を一覧で出力し、関連が無い場合は空文字")
  void testOutsideRelations() {
    assertMarkdownEquals(
        """
        ## 観点外のテーブルとの関連

        | No. | 参照元 | 外部キー名 | 参照先 |
        |:---|:---|:---|:---|
        | 1 | public.orders | rel_orders_product | public.product |

        """,
        ViewpointTemplates.outsideRelations(
            List.of(
                ForeignKeyFixtures.logical(
                    "public", "orders", "rel_orders_product", "public", "product"))));
    assertEquals("", ViewpointTemplates.outsideRelations(List.of()));
  }

  @Test
  @DisplayName("indexLine: 観点名・説明の先頭1行・テーブル数・観点ページへのリンクを出力し、|・改行はエスケープする")
  void testIndexLine() {
    var viewpoint = Viewpoint.of("order", "受注|管理", "受注から出荷まで。\n請求は含まない。", List.of("orders"));
    var content =
        new ViewpointContent(
            viewpoint,
            List.of(newTable("public", "orders", "受注"), newTable("public", "customer", "")),
            ForeignKeyGroup.of(List.of()),
            List.of());

    assertMarkdownEquals(
        """
        |1|受注\\|管理|受注から出荷まで。|2|[■](./viewpoint_TEST_DB_order.md)|
        """,
        ViewpointTemplates.indexLine(1, content, baseInfo()));
  }

  @Test
  @DisplayName("footer: 観点一覧・テーブル一覧へ戻るリンクを出力する")
  void testFooter() {
    assertMarkdownEquals(
        """
        ___

        [観点一覧へ](./viewpointList_TEST_DB.md) [テーブル一覧へ](./tableList_TEST_DB.md)
        """,
        ViewpointTemplates.footer(baseInfo()));
  }
}
