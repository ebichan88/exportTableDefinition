package com.export_table_definition.infrastructure.db;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MyBatisSqlSessionFactories の生成（設定XML・mapperの解析）に関するテスト。DBへは接続しない */
public class MyBatisSqlSessionFactoriesTest {

  private static final String NAMESPACE_FORMAT =
      "com.export_table_definition.domain.repository.%s.TableDefinitionRepository.%s";

  private static SqlSessionFactory create() {
    return MyBatisSqlSessionFactories.create(
        ConnectionSettings.of(
            Map.of(
                "driver", "org.postgresql.Driver",
                "url", "jdbc:postgresql://localhost:5432/testdb",
                "username", "user",
                "password", "secret")));
  }

  @Test
  @DisplayName("create: DB接続情報が、mybatis-config.xmlのデータソースのプレースホルダへ渡される")
  void testPassesConnectionSettingsToDataSource() {
    final PooledDataSource dataSource =
        (PooledDataSource) create().getConfiguration().getEnvironment().getDataSource();

    assertEquals("org.postgresql.Driver", dataSource.getDriver());
    assertEquals("jdbc:postgresql://localhost:5432/testdb", dataSource.getUrl());
    assertEquals("user", dataSource.getUsername());
    assertEquals("secret", dataSource.getPassword());
  }

  @Test
  @DisplayName("create: PostgreSQL・Oracleの両mapperを解析でき、リポジトリが呼ぶSQLがそろっている")
  void testParsesMappersOfBothDatabases() {
    final Configuration configuration = create().getConfiguration();

    for (final String database : new String[] {"postgresql", "oracle"}) {
      for (final String sqlId :
          new String[] {
            "selectDatabaseInfo",
            "selectAllTableInfo",
            "selectAllColumnInfo",
            "selectAllIndexInfo",
            "selectAllConstraintInfo",
            "selectAllForeignKeyInfo"
          }) {
        final String statementId = String.format(NAMESPACE_FORMAT, database, sqlId);
        assertTrue(configuration.hasStatement(statementId), statementId);
      }
    }
  }
}
