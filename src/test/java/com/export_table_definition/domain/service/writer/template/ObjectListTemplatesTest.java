package com.export_table_definition.domain.service.writer.template;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ObjectListTemplates のセクション生成テスト */
public class ObjectListTemplatesTest {

  private final BaseInfoEntity base =
      new BaseInfoEntity("TEST_DB", "| pg | TEST_DB | 2025-01-01 |");

  @Test
  @DisplayName("fileHeader: タイトルとDB名を含む")
  void testFileHeader() {
    String header = ObjectListTemplates.fileHeader("トリガー一覧", base);
    assertTrue(header.startsWith("# トリガー一覧（DB名：TEST_DB）"));
  }

  @Test
  @DisplayName("baseInfo: 基本情報の表を出力する")
  void testBaseInfo() {
    String section = ObjectListTemplates.baseInfo(base);
    assertTrue(section.startsWith("## 基本情報"));
    assertTrue(section.contains("| pg | TEST_DB | 2025-01-01 |"));
  }

  @Test
  @DisplayName("triggerTableHeader: トリガー一覧の列定義を出力する")
  void testTriggerTableHeader() {
    assertTrue(
        ObjectListTemplates.triggerTableHeader()
            .startsWith("| No. | スキーマ名 | テーブル名 | トリガー名 | タイミング | イベント | 実行関数 |"));
  }

  @Test
  @DisplayName("functionTableHeader: 関数・プロシージャ一覧の列定義を出力する")
  void testFunctionTableHeader() {
    assertTrue(
        ObjectListTemplates.functionTableHeader()
            .startsWith("| No. | スキーマ名 | 種別 | 名前 | 引数 | 戻り値 | 言語 | Link |"));
  }

  @Test
  @DisplayName("sequenceTableHeader: シーケンス一覧の列定義を出力する")
  void testSequenceTableHeader() {
    assertTrue(
        ObjectListTemplates.sequenceTableHeader()
            .startsWith(
                "| No. | スキーマ名 | シーケンス名 | 増分 | 最小値 | 最大値 | キャッシュ | 開始値 | 循環 | 所有カラム | Link |"));
  }

  @Test
  @DisplayName("typeTableHeader: ユーザー定義型一覧の列定義を出力する")
  void testTypeTableHeader() {
    assertTrue(
        ObjectListTemplates.typeTableHeader().startsWith("| No. | スキーマ名 | 型名 | 種別 | 定義 | Link |"));
  }

  @Test
  @DisplayName("listLine: SQL側で組み立てた行に改行を付与する")
  void testListLine() {
    String row = "| 1 | public | orders | trg_orders | BEFORE | INSERT | public.f_orders |";
    assertEquals(row + System.lineSeparator(), ObjectListTemplates.listLine(row));
  }

  @Test
  @DisplayName("footer: テーブル一覧へ戻るリンクを含む")
  void testFooter() {
    String footer = ObjectListTemplates.footer(base);
    assertTrue(footer.startsWith("___"));
    assertTrue(footer.contains("[テーブル一覧へ](./tableList_TEST_DB.md)"));
  }
}
