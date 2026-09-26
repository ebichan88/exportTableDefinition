package com.export_table_definition.infrastructure.db.type;

import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.infrastructure.db.repository.OracleTableDefinitionRepository;
import com.export_table_definition.infrastructure.db.repository.PostgresTableDefinitionRepository;
import java.util.Arrays;
import java.util.Objects;

/** Databaseの種別をもつ列挙型クラス */
public enum DatabaseType {
  /** PostgreSQL */
  POSTGRESQL("postgresql", PostgresTableDefinitionRepository.class),
  /** Oracle */
  ORACLE("oracle", OracleTableDefinitionRepository.class);

  private final String name;
  private final Class<? extends TableDefinitionRepository> repositoryClass;

  /**
   * コンストラクタ
   *
   * @param name Database名
   * @param repositoryClass Databaseに紐づくリポジトリクラス
   */
  DatabaseType(String name, Class<? extends TableDefinitionRepository> repositoryClass) {
    this.name = name;
    this.repositoryClass = repositoryClass;
  }

  /**
   * Database名を返却する。
   *
   * @return Database名を返却する。
   */
  public String getName() {
    return name;
  }

  /**
   * Databaseに紐づくリポジトリクラスを返却する。
   *
   * @return リポジトリクラスを返却する。
   */
  public Class<? extends TableDefinitionRepository> getRepositoryClass() {
    return repositoryClass;
  }

  /**
   * Database名に紐づくEnumを返却する。
   *
   * @param name Enum逆引きに用いる値
   * @return DatabaseTypeを返却する。
   * @throws IllegalArgumentException 対象のEnumが存在しない場合にthrowする。
   */
  public static DatabaseType findByName(String name) {
    return Arrays.stream(DatabaseType.values())
        .filter(e -> Objects.equals(name, e.getName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported database type: " + name));
  }
}
