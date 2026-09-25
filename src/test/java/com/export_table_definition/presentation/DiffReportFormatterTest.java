package com.export_table_definition.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.ContentDiff;
import com.export_table_definition.domain.model.DiffResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DiffReportFormatter のメッセージ組み立てに関するテスト */
public class DiffReportFormatterTest {

  @Test
  @DisplayName("format: 差分が無い場合はその旨のメッセージのみを返す")
  void testFormatNoDifference() {
    String message = DiffReportFormatter.format(new DiffResult(List.of(), List.of(), List.of()));
    assertEquals(
        "No difference detected between the generated document and the committed document.",
        message);
  }

  @Test
  @DisplayName("format: 生成のみ・コミット済みのみ・内容不一致のオブジェクトをそれぞれ一覧に含める")
  void testFormatListsEachDiffKind() {
    DiffResult diffResult =
        new DiffResult(
            List.of("table public.added"),
            List.of("table public.removed"),
            List.of(new ContentDiff("table public.changed", List.of("-old", "+new"))));

    String message = DiffReportFormatter.format(diffResult);

    assertTrue(message.contains("Only in generated document (possibly missing commit):"));
    assertTrue(message.contains(" - table public.added"));
    assertTrue(message.contains("Only in committed document (possibly a stale file):"));
    assertTrue(message.contains(" - table public.removed"));
    assertTrue(message.contains("Content differs:"));
    assertTrue(message.contains(" - table public.changed"));
    assertTrue(message.contains("-old"));
    assertTrue(message.contains("+new"));
  }

  @Test
  @DisplayName("format: 内容不一致が無い区分の見出しは出力しない")
  void testFormatOmitsEmptySections() {
    DiffResult diffResult = new DiffResult(List.of("table public.added"), List.of(), List.of());

    String message = DiffReportFormatter.format(diffResult);

    assertTrue(message.contains("Only in generated document"));
    assertFalse(message.contains("Only in committed document"));
    assertFalse(message.contains("Content differs:"));
  }

  @Test
  @DisplayName("format: 1オブジェクトあたりの上限を超えるunified diffは残り行数を省略表示する")
  void testFormatTruncatesLongDiffPerTarget() {
    List<String> longDiff =
        java.util.stream.IntStream.range(0, 250).mapToObj(i -> "line" + i).toList();
    DiffResult diffResult =
        new DiffResult(List.of(), List.of(), List.of(new ContentDiff("table public.t", longDiff)));

    String message = DiffReportFormatter.format(diffResult);

    assertTrue(message.contains("line0"));
    assertTrue(message.contains("line199"));
    assertFalse(message.contains("line200"));
    assertTrue(message.contains("... (50 more lines)"));
  }

  @Test
  @DisplayName("format: 全体の上限を超えるオブジェクトは、以降の差分本体をまとめて省略する")
  void testFormatTruncatesRemainingObjectsWhenTotalBudgetExceeded() {
    // 1オブジェクトあたり200行×11件で2200行となり、全体上限2000行を超える
    List<ContentDiff> diffs =
        java.util.stream.IntStream.range(0, 11)
            .mapToObj(
                i ->
                    new ContentDiff(
                        "table public.t" + i,
                        java.util.stream.IntStream.range(0, 200)
                            .mapToObj(j -> "t" + i + "_line" + j)
                            .toList()))
            .toList();
    DiffResult diffResult = new DiffResult(List.of(), List.of(), diffs);

    String message = DiffReportFormatter.format(diffResult);

    assertTrue(message.contains("t0_line0"));
    assertTrue(message.contains("(diff omitted for"));
    // 省略されたオブジェクトも「Content differs:」の一覧には全件掲載される
    assertTrue(message.contains(" - table public.t10"));
  }
}
