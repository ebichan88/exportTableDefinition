package com.export_table_definition.infrastructure.db.repository;

import com.export_table_definition.infrastructure.db.type.DatabaseType;

/**
 * [Postgres]テーブル定義出力に関するリポジトリクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class PostgresTableDefinitionRepository extends AbstractTableDefinitionRepository {

  /** コンストラクタ */
  public PostgresTableDefinitionRepository() {
    super(DatabaseType.POSTGRESQL);
  }
}
