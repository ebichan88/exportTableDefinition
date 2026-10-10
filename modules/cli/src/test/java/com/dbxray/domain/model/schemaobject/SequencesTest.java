package com.dbxray.domain.model.schemaobject;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Sequences の取得順の保持・空判定・反復に関するテスト */
public class SequencesTest {

  private SequenceEntity sequence(String name) {
    return new SequenceEntity("testdb", "public", name, "", "", "", "", "", false, "");
  }

  @Test
  @DisplayName("asList・iterator: 渡した順序のまま返す")
  void testKeepsOrder() {
    var s1 = sequence("s2");
    var s2 = sequence("s1");
    var sequences = Sequences.of(List.of(s1, s2));

    var iterated = new ArrayList<SequenceEntity>();
    sequences.forEach(iterated::add);

    assertEquals(List.of(s1, s2), sequences.asList());
    assertEquals(List.of(s1, s2), iterated);
  }

  @Test
  @DisplayName("isEmpty: 1件も無い場合のみtrueを返す")
  void testIsEmpty() {
    assertTrue(Sequences.of(List.of()).isEmpty());
    assertFalse(Sequences.of(List.of(sequence("s1"))).isEmpty());
  }
}
