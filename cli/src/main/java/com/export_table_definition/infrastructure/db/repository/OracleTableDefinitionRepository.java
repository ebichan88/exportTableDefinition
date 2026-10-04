package com.export_table_definition.infrastructure.db.repository;

import com.export_table_definition.infrastructure.db.type.DatabaseType;
import jakarta.inject.Inject;
import org.apache.ibatis.session.SqlSessionFactory;

/** [oracle]テーブル定義出力に関するリポジトリクラス */
public final class OracleTableDefinitionRepository extends AbstractTableDefinitionRepository {

  @Inject
  public OracleTableDefinitionRepository(SqlSessionFactory sqlSessionFactory) {
    super(DatabaseType.ORACLE, sqlSessionFactory);
  }
}
