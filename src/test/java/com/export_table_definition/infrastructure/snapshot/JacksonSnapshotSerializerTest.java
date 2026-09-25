package com.export_table_definition.infrastructure.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.snapshot.SequenceSnapshot;
import com.export_table_definition.domain.model.snapshot.TableSnapshot;
import com.export_table_definition.domain.model.snapshot.TypeSnapshot;
import com.export_table_definition.domain.model.type.Cardinality;
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
}
