package com.export_table_definition.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.export_table_definition.mcp.catalog.ObjectKey;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TableResourceUri}のテスト */
class TableResourceUriTest {

  @Test
  @DisplayName("DB名・スキーマ名・テーブル名の3区間のURIになる")
  void buildsUri() {
    assertEquals(
        "exporttable://testdb/sample/employee",
        TableResourceUri.of(new ObjectKey("testdb", "sample", "employee")));
  }

  @Test
  @DisplayName("区間に入る / や空白や日本語はパーセントエンコードされ、戻すと元の名前になる")
  void roundTripsSpecialCharacters() {
    final ObjectKey key = new ObjectKey("test db", "sch/ema", "受注+明細");

    final String uri = TableResourceUri.of(key);

    assertEquals("exporttable://test%20db/sch%2Fema/%E5%8F%97%E6%B3%A8%2B%E6%98%8E%E7%B4%B0", uri);
    assertEquals(Optional.of(key), TableResourceUri.parse(uri));
  }

  @Test
  @DisplayName("別のURI・区間の数が違うURI・空の区間があるURIは解釈できない")
  void rejectsMalformedUris() {
    assertEquals(Optional.empty(), TableResourceUri.parse(null));
    assertEquals(Optional.empty(), TableResourceUri.parse("file:///etc/passwd"));
    assertEquals(Optional.empty(), TableResourceUri.parse("exporttable://testdb/sample"));
    assertEquals(Optional.empty(), TableResourceUri.parse("exporttable://testdb/sample/a/b"));
    assertEquals(Optional.empty(), TableResourceUri.parse("exporttable://testdb//employee"));
  }

  @Test
  @DisplayName("不正なパーセントエンコードはそのまま扱い、例外にしない")
  void keepsInvalidEscapes() {
    assertEquals(
        Optional.of(new ObjectKey("testdb", "sample", "50%")),
        TableResourceUri.parse("exporttable://testdb/sample/50%"));
  }

  @Test
  @DisplayName("テンプレートの変数はSDKの照合（/を含まない区間）に合う")
  void templateMatchesBuiltUris() {
    final String uri = TableResourceUri.of(new ObjectKey("testdb", "sch/ema", "受注"));

    assertTrue(
        uri.matches("exporttable://[^/]+/[^/]+/[^/]+"), "エンコード後の各区間に / が残ると、テンプレートの変数に当てはまらない");
  }
}
