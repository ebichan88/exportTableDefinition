package com.export_table_definition.domain.service.path;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.domain.model.snapshot.SnapshotKind;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SnapshotLocations のスナップショットの配置の組み立て・種別の逆引きに関するテスト */
public class SnapshotLocationsTest {

  @Test
  @DisplayName("snapshotDirectory/databaseFile/objectFile: スナップショットの配置")
  void testLocations() {
    assertEquals("snapshot", SnapshotLocations.snapshotDirectory());
    assertEquals("testdb/database.json", SnapshotLocations.databaseFile("testdb"));
    assertEquals(
        "testdb/public/tables.jsonl",
        SnapshotLocations.objectFile("testdb", "public", SnapshotKind.TABLE));
    assertEquals(
        "testdb/public/functions.jsonl",
        SnapshotLocations.objectFile("testdb", "public", SnapshotKind.FUNCTION));
  }

  @Test
  @DisplayName("kindOf: objectFileの逆変換としてファイル名から種別を判定する")
  void testKindOf() {
    for (SnapshotKind kind : SnapshotKind.values()) {
      String file = SnapshotLocations.objectFile("testdb", "public", kind);
      assertEquals(
          Optional.of(kind), SnapshotLocations.kindOf(file.substring(file.lastIndexOf('/') + 1)));
    }
    assertEquals(Optional.empty(), SnapshotLocations.kindOf("database.json"));
  }
}
