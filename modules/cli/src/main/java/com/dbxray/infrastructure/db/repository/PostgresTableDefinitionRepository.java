package com.dbxray.infrastructure.db.repository;

import com.dbxray.infrastructure.db.type.DatabaseType;
import jakarta.inject.Inject;
import org.apache.ibatis.session.SqlSessionFactory;

/** [Postgres]テーブル定義出力に関するリポジトリクラス */
public final class PostgresTableDefinitionRepository extends AbstractTableDefinitionRepository {

  @Inject
  public PostgresTableDefinitionRepository(SqlSessionFactory sqlSessionFactory) {
    super(DatabaseType.POSTGRESQL, sqlSessionFactory);
  }
}
