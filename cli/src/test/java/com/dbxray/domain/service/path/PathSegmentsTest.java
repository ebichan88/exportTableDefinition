package com.dbxray.domain.service.path;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** PathSegments の名前の置き換えに関するテスト */
public class PathSegmentsTest {

  @ParameterizedTest
  @ValueSource(strings = {"orders", "受注明細", "order_items-2024", "v1.2", "...a", "a b"})
  @DisplayName("encode: 通常の名前はそのまま返す")
  void testEncodeKeepsOrdinaryNames(String name) {
    assertEquals(name, PathSegments.encode(name));
  }

  @Test
  @DisplayName("encode: パスの区切りとWindowsでファイル名に使えない文字を~と16進数に置き換える")
  void testEncodeReplacesUnsafeCharacters() {
    assertEquals("..~2F..~2Ftmp~2Fpwn", PathSegments.encode("../../tmp/pwn"));
    assertEquals("a~5Cb", PathSegments.encode("a\\b"));
    assertEquals("C~3Aevil", PathSegments.encode("C:evil"));
    assertEquals("~2A~3F~22~3C~3E~7C", PathSegments.encode("*?\"<>|"));
    assertEquals("a~00b~0Ac~7F", PathSegments.encode("a\u0000b\nc\u007F"));
  }

  @Test
  @DisplayName("encode: .だけからなる名前（.・..・...）は置き換え、.を含むだけの名前は置き換えない")
  void testEncodeReplacesDotOnlyNames() {
    assertEquals("~2E", PathSegments.encode("."));
    assertEquals("~2E~2E", PathSegments.encode(".."));
    assertEquals("~2E~2E~2E", PathSegments.encode("..."));
    assertEquals("a..b", PathSegments.encode("a..b"));
  }

  @Test
  @DisplayName("encode: 空・nullは~、~自体は~7Eにし、置き換えた結果が他の名前と重ならない")
  void testEncodeIsInjectiveForEscapeCharacter() {
    assertEquals("~", PathSegments.encode(""));
    assertEquals("~", PathSegments.encode(null));
    assertEquals("~7E", PathSegments.encode("~"));
    assertNotEquals(PathSegments.encode("a/b"), PathSegments.encode("a~2Fb"));
  }
}
