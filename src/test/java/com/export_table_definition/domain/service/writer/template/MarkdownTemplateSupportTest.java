package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MarkdownTemplateSupport の表セルエスケープに関するテスト */
public class MarkdownTemplateSupportTest {

  @Test
  @DisplayName("escapeTableCell: null・空文字は空文字を返す")
  void testEscapeEmpty() {
    assertEquals("", MarkdownTemplateSupport.escapeTableCell(null));
    assertEquals("", MarkdownTemplateSupport.escapeTableCell(""));
  }

  @Test
  @DisplayName("escapeTableCell: パイプはエスケープされる")
  void testEscapePipe() {
    assertEquals("a\\|b\\|c", MarkdownTemplateSupport.escapeTableCell("a|b|c"));
  }

  @Test
  @DisplayName("escapeTableCell: 改行（CRLF/LF/CR）は<br>へ置換される")
  void testEscapeNewlines() {
    assertEquals("a<br>b<br>c<br>d", MarkdownTemplateSupport.escapeTableCell("a\r\nb\nc\rd"));
  }

  @Test
  @DisplayName("escapeTableCell: 通常の文字列はそのまま返す")
  void testEscapePlain() {
    assertEquals("個人情報を含む", MarkdownTemplateSupport.escapeTableCell("個人情報を含む"));
  }

  @Test
  @DisplayName("escapePipe: null・空文字は空文字を返す")
  void testEscapePipeEmpty() {
    assertEquals("", MarkdownTemplateSupport.escapePipe(null));
    assertEquals("", MarkdownTemplateSupport.escapePipe(""));
  }

  @Test
  @DisplayName("escapePipe: パイプのみエスケープし、改行はそのまま残す")
  void testEscapePipeOnly() {
    assertEquals(
        "CHECK (((a \\|\\| b) <> ''::text))",
        MarkdownTemplateSupport.escapePipe("CHECK (((a || b) <> ''::text))"));
    assertEquals("a\nb", MarkdownTemplateSupport.escapePipe("a\nb"));
  }
}
