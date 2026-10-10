package com.dbxray.mcp.tool;

import static com.dbxray.mcp.catalog.TestTables.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dbxray.mcp.catalog.ColumnEntry;
import com.dbxray.mcp.catalog.DiagramScope;
import com.dbxray.mcp.catalog.ObjectKey;
import com.dbxray.mcp.catalog.RelationEntry;
import com.dbxray.mcp.catalog.SchemaCatalog;
import com.dbxray.mcp.catalog.TestCatalogs;
import com.dbxray.mcp.catalog.ViewpointEntry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link MermaidErDiagram}のテスト */
class MermaidErDiagramTest {

  @Test
  @DisplayName("DB由来の名前・論理名・関連名に含まれる改行・二重引用符・記号で、Mermaidの構文やコードブロックを壊さない")
  void neutralizesUntrustedNames() {
    final SchemaCatalog catalog =
        TestCatalogs.of(
            List.of(
                table("parent\"]\n```")
                    .logicalName("親\n```\nflowchart")
                    .column(
                        new ColumnEntry(
                            "id }\n", "ID\"\r\n", "numeric(10, 2) []", true, true, null, null))
                    .build(),
                table("child")
                    .column(new ColumnEntry("parent_id", null, "", false, false, null, null))
                    .foreignKey(
                        new RelationEntry(
                            "fk\"\n```",
                            List.of("parent_id"),
                            "sample",
                            "parent\"]\n```",
                            List.of("id }\n"),
                            "UNKNOWN"))
                    .build()));

    final String mermaid =
        MermaidErDiagram.render(
            catalog.relations().among(viewpoint("parent\"]\n```", "child").tables()));

    // 名前の中の```は行頭に来ないため、コードブロックを閉じる行にならない（閉じる行は行頭の3文字までの空白に続く```）
    assertTrue(mermaid.lines().noneMatch(line -> line.matches(" {0,3}```.*")), mermaid);
    assertEquals(
        "erDiagram\n"
            + "    sample_parent______[\"parent'] ```（親 ``` flowchart）\"]\n"
            + "    sample_child[\"child\"]\n"
            + "    sample_parent______ ||--o{ sample_child : \"fk' ```\"\n"
            + "    sample_parent______ {\n"
            + "        numeric_ id___ PK \"ID' \"\n"
            + "    }\n"
            + "    sample_child {\n"
            + "        unknown parent_id FK\n"
            + "    }\n",
        mermaid);
  }

  @Test
  @DisplayName("記号の置き換えで識別子が重なる場合は連番で区別し、同じ名前のテーブルが複数のスキーマにある場合は表示名をスキーマ名で修飾する")
  void distinguishesCollidingNames() {
    final DiagramScope scope =
        new DiagramScope(
            List.of(
                table("testdb", "a", "b-c").build(),
                table("testdb", "a", "b_c").build(),
                table("testdb", "x", "b-c").build()),
            List.of(),
            List.of());

    assertEquals(
        "erDiagram\n"
            + "    a_b_c[\"a.b-c\"]\n"
            + "    a_b_c_2[\"b_c\"]\n"
            + "    x_b_c[\"x.b-c\"]\n",
        MermaidErDiagram.render(scope));
  }

  private static ViewpointEntry viewpoint(String... tables) {
    return new ViewpointEntry(
        "testdb",
        "v",
        "v",
        "",
        List.of(tables).stream().map(name -> new ObjectKey("testdb", "sample", name)).toList());
  }
}
