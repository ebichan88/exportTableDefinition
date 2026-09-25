package com.export_table_definition.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UnifiedDiffGenerator のunified diff生成に関するテスト */
public class UnifiedDiffGeneratorTest {

  private final UnifiedDiffGenerator generator = new UnifiedDiffGenerator();

  private List<String> lines(String... values) {
    return List.of(values);
  }

  @Test
  @DisplayName("generate: 内容が完全に同じ場合は空リストを返す")
  void testNoDifferenceReturnsEmptyList() {
    List<String> result =
        generator.generate("committed", lines("a", "b", "c"), "generated", lines("a", "b", "c"));

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("generate: 中間の1行が変更された場合、前後3行の文脈付きhunkを1つ生成する")
  void testSingleLineChangeInMiddle() {
    List<String> committed = IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).toList();
    List<String> generated = new ArrayList<>(committed);
    generated.set(4, "X"); // 5行目(0始まりで4番目)を変更

    List<String> result = generator.generate("committed/t1", committed, "generated/t1", generated);

    assertEquals(
        List.of(
            "--- committed/t1",
            "+++ generated/t1",
            "@@ -2,7 +2,7 @@",
            " 2",
            " 3",
            " 4",
            "-5",
            "+X",
            " 6",
            " 7",
            " 8"),
        result);
  }

  @Test
  @DisplayName("generate: 行数が1のhunkはヘッダで件数を省略する")
  void testHunkHeaderOmitsCountWhenOne() {
    List<String> result = generator.generate("committed", lines("a"), "generated", lines("b"));

    assertEquals(List.of("--- committed", "+++ generated", "@@ -1 +1 @@", "-a", "+b"), result);
  }

  @Test
  @DisplayName("generate: 先頭への追加は、コミット済み側の行数0(0,0)として報告される")
  void testInsertionAtStartOfEmptyCommitted() {
    List<String> result =
        generator.generate("committed", List.of(), "generated", lines("x", "y", "z"));

    assertEquals(
        List.of("--- committed", "+++ generated", "@@ -0,0 +1,3 @@", "+x", "+y", "+z"), result);
  }

  @Test
  @DisplayName("generate: ファイル末尾の変更は、文脈行数が不足する分だけ切り詰められる")
  void testChangeAtEndOfFileClipsTrailingContext() {
    List<String> result =
        generator.generate("committed", lines("a", "b", "c"), "generated", lines("a", "b", "X"));

    assertEquals(
        List.of("--- committed", "+++ generated", "@@ -1,3 +1,3 @@", " a", " b", "-c", "+X"),
        result);
  }

  @Test
  @DisplayName("generate: 変更箇所の間が2*文脈行数(6行)以内の場合は1つのhunkへ結合する")
  void testNearbyChangesAreMergedIntoOneHunk() {
    List<String> committed = IntStream.rangeClosed(1, 10).mapToObj(String::valueOf).toList();
    List<String> generated = new ArrayList<>(committed);
    generated.set(3, "X4"); // 4行目を変更
    generated.set(6, "X7"); // 7行目を変更(間の5,6行目=2行はそのまま。6行以内なので結合される)

    List<String> result = generator.generate("committed", committed, "generated", generated);

    long hunkHeaderCount = result.stream().filter(line -> line.startsWith("@@")).count();
    assertEquals(1, hunkHeaderCount, "hunkは1つに結合されるはず: " + result);
    assertTrue(result.contains("-4"));
    assertTrue(result.contains("+X4"));
    assertTrue(result.contains("-7"));
    assertTrue(result.contains("+X7"));
  }

  @Test
  @DisplayName("generate: 変更箇所の間が2*文脈行数(6行)を超える場合は2つのhunkに分かれる")
  void testDistantChangesProduceSeparateHunks() {
    List<String> committed = IntStream.rangeClosed(1, 20).mapToObj(String::valueOf).toList();
    List<String> generated = new ArrayList<>(committed);
    generated.set(4, "X5"); // 5行目を変更
    generated.set(15, "X16"); // 16行目を変更(間の6〜15行目=10行はそのまま。6行を超えるので分かれる)

    List<String> result = generator.generate("committed", committed, "generated", generated);

    assertEquals(
        List.of(
            "--- committed",
            "+++ generated",
            "@@ -2,7 +2,7 @@",
            " 2",
            " 3",
            " 4",
            "-5",
            "+X5",
            " 6",
            " 7",
            " 8",
            "@@ -13,7 +13,7 @@",
            " 13",
            " 14",
            " 15",
            "-16",
            "+X16",
            " 17",
            " 18",
            " 19"),
        result);
  }

  @Test
  @DisplayName("generate: 先頭・末尾の共通行は編集手順の探索対象から除かれても正しく前後の文脈に含まれる")
  void testCommonPrefixAndSuffixAreTrimmedButStillUsedAsContext() {
    // 変更箇所の前後3行(文脈)より内側に共通行が多く存在するケース
    List<String> committed = IntStream.rangeClosed(1, 100).mapToObj(String::valueOf).toList();
    List<String> generated = new ArrayList<>(committed);
    generated.set(49, "CHANGED"); // 50行目のみ変更

    List<String> result = generator.generate("committed", committed, "generated", generated);

    assertEquals(
        List.of(
            "--- committed",
            "+++ generated",
            "@@ -47,7 +47,7 @@",
            " 47",
            " 48",
            " 49",
            "-50",
            "+CHANGED",
            " 51",
            " 52",
            " 53"),
        result);
  }

  @Test
  @DisplayName("generate: 編集距離が上限を超える場合は全行削除+全行追加のhunkにフォールバックし、結果は正しいまま返す")
  void testFallsBackToFullReplacementWhenEditDistanceExceedsLimit() {
    // 共通する行が1つも無い大きなリスト同士(編集距離 = 行数の合計 > 上限)を比較する
    List<String> committed =
        IntStream.rangeClosed(1, 501).mapToObj(i -> "committed-line-" + i).toList();
    List<String> generated =
        IntStream.rangeClosed(1, 501).mapToObj(i -> "generated-line-" + i).toList();

    List<String> result = generator.generate("committed", committed, "generated", generated);

    assertEquals("--- committed", result.get(0));
    assertEquals("+++ generated", result.get(1));
    assertEquals("@@ -1,501 +1,501 @@", result.get(2));
    assertEquals(2 + 1 + 501 + 501, result.size());
    // 削除側(committed)が先に、追加側(generated)が後にまとまって出力される
    assertTrue(result.subList(3, 504).stream().allMatch(line -> line.startsWith("-committed-")));
    assertTrue(result.subList(504, 1005).stream().allMatch(line -> line.startsWith("+generated-")));
  }
}
