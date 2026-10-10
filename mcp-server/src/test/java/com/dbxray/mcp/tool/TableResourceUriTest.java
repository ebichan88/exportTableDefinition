package com.dbxray.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dbxray.mcp.catalog.ObjectKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link TableResourceUri}のテスト */
class TableResourceUriTest {

  @Test
  @DisplayName("DB名・スキーマ名・テーブル名の3区間のURIになる")
  void buildsUri() {
    assertEquals(
        "dbxray://testdb/sample/employee",
        TableResourceUri.of(new ObjectKey("testdb", "sample", "employee")));
  }

  @Test
  @DisplayName("区間に入る / や空白や日本語はパーセントエンコードされ、URIの区間が増えない")
  void encodesSpecialCharacters() {
    final String uri = TableResourceUri.of(new ObjectKey("test db", "sch/ema", "受注+明細"));

    assertEquals("dbxray://test%20db/sch%2Fema/%E5%8F%97%E6%B3%A8%2B%E6%98%8E%E7%B4%B0", uri);
  }

  @Test
  @DisplayName("英数字と -._~ はそのまま残す")
  void keepsUnreservedCharacters() {
    assertEquals("a-Z_0.9~", TableResourceUri.encode("a-Z_0.9~"));
  }
}
