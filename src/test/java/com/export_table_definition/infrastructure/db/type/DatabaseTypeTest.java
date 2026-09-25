package com.export_table_definition.infrastructure.db.type;

import static org.junit.jupiter.api.Assertions.*;

import com.export_table_definition.infrastructure.db.repository.OracleTableDefinitionRepository;
import com.export_table_definition.infrastructure.db.repository.PostgresTableDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DatabaseType の逆引き・リポジトリクラス解決に関するテスト */
public class DatabaseTypeTest {

  @Test
  @DisplayName("findByName: 'postgresql' はPOSTGRESQLに解決される")
  void testFindByNamePostgresql() {
    assertEquals(DatabaseType.POSTGRESQL, DatabaseType.findByName("postgresql"));
  }

  @Test
  @DisplayName("findByName: 'oracle' はORACLEに解決される")
  void testFindByNameOracle() {
    assertEquals(DatabaseType.ORACLE, DatabaseType.findByName("oracle"));
  }

  @Test
  @DisplayName("findByName: 未知の値の場合はIllegalArgumentExceptionをスローする")
  void testFindByNameUnknownThrows() {
    assertThrows(IllegalArgumentException.class, () -> DatabaseType.findByName("mysql"));
  }

  @Test
  @DisplayName("getRepositoryClass: POSTGRESQLはPostgresTableDefinitionRepositoryを返す")
  void testGetRepositoryClassPostgresql() {
    assertEquals(
        PostgresTableDefinitionRepository.class, DatabaseType.POSTGRESQL.getRepositoryClass());
  }

  @Test
  @DisplayName("getRepositoryClass: ORACLEはOracleTableDefinitionRepositoryを返す")
  void testGetRepositoryClassOracle() {
    assertEquals(OracleTableDefinitionRepository.class, DatabaseType.ORACLE.getRepositoryClass());
  }

  @Test
  @DisplayName("getName: 各Enum値に対応するDB名を返す")
  void testGetName() {
    assertEquals("postgresql", DatabaseType.POSTGRESQL.getName());
    assertEquals("oracle", DatabaseType.ORACLE.getName());
  }
}
