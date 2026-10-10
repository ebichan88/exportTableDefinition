package com.dbxray.infrastructure.db.repository;

import com.dbxray.infrastructure.db.type.DatabaseType;
import jakarta.inject.Inject;
import org.apache.ibatis.session.SqlSessionFactory;

/** [Postgres]DBのカタログの取得に関するリポジトリクラス */
public final class PostgresCatalogRepository extends AbstractCatalogRepository {

  @Inject
  public PostgresCatalogRepository(SqlSessionFactory sqlSessionFactory) {
    super(DatabaseType.POSTGRESQL, sqlSessionFactory);
  }
}
