package com.export_table_definition.domain.model.type;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** OutputObjectType の逆引き・出力対象種別解決に関するテスト */
public class OutputObjectTypeTest {

  @Test
  @DisplayName("findByName: 各値が対応するEnumに解決される")
  void testFindByName() {
    assertEquals(OutputObjectType.TRIGGER, OutputObjectType.findByName("trigger"));
    assertEquals(OutputObjectType.FUNCTION, OutputObjectType.findByName("function"));
    assertEquals(OutputObjectType.SEQUENCE, OutputObjectType.findByName("sequence"));
    assertEquals(OutputObjectType.TYPE, OutputObjectType.findByName("type"));
  }

  @Test
  @DisplayName("findByName: 未知の値の場合はIllegalArgumentExceptionをスローする")
  void testFindByNameUnknownThrows() {
    assertThrows(IllegalArgumentException.class, () -> OutputObjectType.findByName("unknown"));
  }

  @Test
  @DisplayName("parse: 空リストの場合は全種別を返す")
  void testParseEmptyReturnsAllTypes() {
    assertEquals(EnumSet.allOf(OutputObjectType.class), OutputObjectType.parse(List.of()));
  }

  @Test
  @DisplayName("parse: 指定した種別のみを含む集合を返す")
  void testParseReturnsOnlySpecifiedTypes() {
    final Set<OutputObjectType> result = OutputObjectType.parse(List.of("function", "sequence"));
    assertEquals(Set.of(OutputObjectType.FUNCTION, OutputObjectType.SEQUENCE), result);
  }

  @Test
  @DisplayName("parse: 未知の値が含まれる場合はIllegalArgumentExceptionをスローする")
  void testParseUnknownThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () -> OutputObjectType.parse(List.of("function", "unknown")));
  }

  @Test
  @DisplayName("getName: 各Enum値に対応する種別名を返す")
  void testGetName() {
    assertEquals("trigger", OutputObjectType.TRIGGER.getName());
    assertEquals("function", OutputObjectType.FUNCTION.getName());
    assertEquals("sequence", OutputObjectType.SEQUENCE.getName());
    assertEquals("type", OutputObjectType.TYPE.getName());
  }
}
