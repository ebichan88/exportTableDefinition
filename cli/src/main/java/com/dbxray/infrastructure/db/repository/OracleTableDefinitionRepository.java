package com.dbxray.infrastructure.db.repository;

import com.dbxray.infrastructure.db.type.DatabaseType;
import jakarta.inject.Inject;
import org.apache.ibatis.session.SqlSessionFactory;

/** [oracle]テーブル定義出力に関するリポジトリクラス */
public final class OracleTableDefinitionRepository extends AbstractTableDefinitionRepository {

  @Inject
  public OracleTableDefinitionRepository(SqlSessionFactory sqlSessionFactory) {
    super(DatabaseType.ORACLE, sqlSessionFactory);
  }
}
