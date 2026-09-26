package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.database.DatabaseEntity;

/**
 * データベースの情報に関してORMのデータの受け渡しに利用するDTOクラス
 *
 * @param dbName データベース名
 * @param dbmsName DBMS種別（PostgreSQL/Oracle）
 */
public record DatabaseDto(String dbName, String dbmsName) {
  /**
   * DTOからEntityへの変換メソッド
   *
   * @return DatabaseEntityのインスタンス
   */
  public DatabaseEntity toEntity() {
    return new DatabaseEntity(dbName, dbmsName);
  }
}
