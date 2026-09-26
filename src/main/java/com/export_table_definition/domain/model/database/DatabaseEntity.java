package com.export_table_definition.domain.model.database;

/**
 * DBのカタログから取得するデータベースの情報に関するrecordクラス
 *
 * @param dbName データベース名
 * @param dbmsName DBMS種別（PostgreSQL/Oracle）
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record DatabaseEntity(String dbName, String dbmsName) {}
