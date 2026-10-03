package com.export_table_definition.infrastructure.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.relation.Cardinality;
import com.export_table_definition.domain.model.snapshot.SequenceSnapshot;
import com.export_table_definition.domain.model.snapshot.TableSnapshot;
import com.export_table_definition.domain.model.snapshot.TypeSnapshot;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** JacksonSnapshotSerializer のJSON変換に関するテスト */
public class JacksonSnapshotSerializerTest {

  private final JacksonSnapshotSerializer serializer = new JacksonSnapshotSerializer();

  @Test
  @DisplayName("serialize: recordのコンポーネントを宣言順に出力する")
  void testSerializeKeepsComponentOrder() {
    var type = new TypeSnapshot("public", "mood", "ENUM", "sad, ok");
    assertEquals(
        "{\"schema\":\"public\",\"name\":\"mood\",\"category\":\"ENUM\",\"definition\":\"sad, ok\"}",
        serializer.serialize(type));
  }

  @Test
  @DisplayName("serialize: null・空リストの項目は出力せず、falseの真偽値は出力する")
  void testSerializeOmitsEmptyValues() {
    var sequence = new SequenceSnapshot("public", "seq1", "1", null, null, null, null, false, null);
    assertEquals(
        "{\"schema\":\"public\",\"name\":\"seq1\",\"incrementBy\":\"1\",\"cycle\":false}",
        serializer.serialize(sequence));

    var table =
        new TableSnapshot(
            "public", "t1", null, "table", null, null, null, List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of());
    assertEquals(
        "{\"schema\":\"public\",\"name\":\"t1\",\"type\":\"table\"}", serializer.serialize(table));
  }

  @Test
  @DisplayName("serialize: 改行を含む値もエスケープされ、常に1行になる。日本語はエスケープしない")
  void testSerializeProducesSingleLine() {
    var type = new TypeSnapshot("public", "区分", "DOMAIN", "line1\nline2");
    String json = serializer.serialize(type);
    assertFalse(json.contains("\n"));
    assertTrue(json.contains("\"name\":\"区分\""));
    assertTrue(json.contains("\"definition\":\"line1\\nline2\""));
  }

  @Test
  @DisplayName("deserialize: serializeで出力した1行を、項目名をキーとするマップへ変換する")
  void testDeserialize() {
    var type = new TypeSnapshot("public", "mood", "ENUM", "sad, ok");
    assertEquals(
        Map.of("schema", "public", "name", "mood", "category", "ENUM", "definition", "sad, ok"),
        serializer.deserialize(serializer.serialize(type)));
  }

  @Test
  @DisplayName("deserialize: JSONとして解釈できない行は例外とする")
  void testDeserializeInvalidLine() {
    assertThrows(UncheckedIOException.class, () -> serializer.deserialize("{broken"));
  }

  @Test
  @DisplayName("serialize: 入れ子のrecord・リスト・列挙型を出力する")
  void testSerializeNestedRecords() {
    var relation =
        new TableSnapshot.Relation(
            "fk_orders_customer",
            List.of("customer_id"),
            "public",
            "customers",
            List.of("id"),
            Cardinality.OPTIONAL_ONE_TO_MANY);
    assertEquals(
        "{\"name\":\"fk_orders_customer\",\"columns\":[\"customer_id\"],\"referenceSchema\":\"public\","
            + "\"referenceTable\":\"customers\",\"referenceColumns\":[\"id\"],"
            + "\"cardinality\":\"OPTIONAL_ONE_TO_MANY\"}",
        serializer.serialize(relation));
  }

  @Test
  @DisplayName("formatForDiff: トップレベルの項目を1項目1行に整形する。行末にカンマは付けない")
  void testFormatForDiffFormatsTopLevelFieldsOnePerLine() {
    var type = new TypeSnapshot("public", "mood", "ENUM", "sad, ok");

    assertEquals(
        List.of(
            "{",
            "  \"schema\": \"public\"",
            "  \"name\": \"mood\"",
            "  \"category\": \"ENUM\"",
            "  \"definition\": \"sad, ok\"",
            "}"),
        serializer.formatForDiff(serializer.serialize(type)));
  }

  @Test
  @DisplayName("formatForDiff: 配列は1要素を1行に整形し、要素自体はコンパクトなJSONのままとする")
  void testFormatForDiffFormatsArrayElementsOnePerLine() {
    var table =
        new TableSnapshot(
            "public",
            "t1",
            null,
            "table",
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new TableSnapshot.Relation(
                    "fk1", List.of("customer_id"), "public", "customers", List.of("id"), null),
                new TableSnapshot.Relation(
                    "fk2", List.of("owner_id"), "public", "owners", List.of("id"), null)),
            List.of(),
            List.of());

    assertEquals(
        List.of(
            "{",
            "  \"schema\": \"public\"",
            "  \"name\": \"t1\"",
            "  \"type\": \"table\"",
            "  \"foreignKeys\": [",
            "    {\"name\":\"fk1\",\"columns\":[\"customer_id\"],\"referenceSchema\":\"public\","
                + "\"referenceTable\":\"customers\",\"referenceColumns\":[\"id\"]}",
            "    {\"name\":\"fk2\",\"columns\":[\"owner_id\"],\"referenceSchema\":\"public\","
                + "\"referenceTable\":\"owners\",\"referenceColumns\":[\"id\"]}",
            "  ]",
            "}"),
        serializer.formatForDiff(serializer.serialize(table)));
  }

  @Test
  @DisplayName("formatForDiff: 同じ内容からは常に同じ結果を返す")
  void testFormatForDiffIsDeterministic() {
    var type = new TypeSnapshot("public", "mood", "ENUM", "sad, ok");
    String json = serializer.serialize(type);

    assertEquals(serializer.formatForDiff(json), serializer.formatForDiff(json));
  }

  @Test
  @DisplayName("formatForDiff: JSONとして解釈できない行はそのまま1件だけ含むリストとして返す")
  void testFormatForDiffReturnsInputAsIsWhenNotJson() {
    assertEquals(List.of("not json"), serializer.formatForDiff("not json"));
  }
}
