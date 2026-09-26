package com.export_table_definition.infrastructure.db;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.infrastructure.db.type.DatabaseType;
import com.export_table_definition.shared.exception.UserCorrectableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DatabaseTypeDetector のDB種別の判定に関するテスト（DBへは接続しない） */
public class DatabaseTypeDetectorTest {

  @Test
  @DisplayName("toDatabaseType: JDBCドライバが返す製品名から、大文字小文字を問わずDB種別を判定する")
  void testConvertsProductNameToDatabaseType() {
    assertEquals(DatabaseType.POSTGRESQL, DatabaseTypeDetector.toDatabaseType("PostgreSQL"));
    assertEquals(DatabaseType.ORACLE, DatabaseTypeDetector.toDatabaseType("Oracle"));
  }

  @Test
  @DisplayName("toDatabaseType: 対応していないDBは、利用者が直せる誤りとする")
  void testRejectsUnsupportedDatabase() {
    UserCorrectableException e =
        assertThrows(
            UserCorrectableException.class, () -> DatabaseTypeDetector.toDatabaseType("MySQL"));

    assertTrue(e.getMessage().contains("mysql"));
    assertTrue(e.getMessage().contains("PostgreSQL or Oracle"));
  }
}
