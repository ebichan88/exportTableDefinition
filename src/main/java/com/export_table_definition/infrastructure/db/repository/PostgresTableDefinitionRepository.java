package com.export_table_definition.infrastructure.db.repository;

import com.export_table_definition.infrastructure.db.type.DatabaseType;
import jakarta.inject.Inject;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * [Postgres]テーブル定義出力に関するリポジトリクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class PostgresTableDefinitionRepository extends AbstractTableDefinitionRepository {

  /**
   * コンストラクタ
   *
   * @param sqlSessionFactory 接続先DBのSqlSessionFactory
   */
  @Inject
  public PostgresTableDefinitionRepository(SqlSessionFactory sqlSessionFactory) {
    super(DatabaseType.POSTGRESQL, sqlSessionFactory);
  }
}
