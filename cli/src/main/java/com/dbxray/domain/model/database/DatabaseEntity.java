package com.dbxray.domain.model.database;

/**
 * DBのカタログから取得するデータベースの情報に関するrecordクラス
 *
 * @param dbmsName DBMS種別（PostgreSQL/Oracle）
 * @param majorVersion DBMSのメジャーバージョン（例: PostgreSQLの16、Oracleの23）。マイナー版の更新で出力が変わらないよう、メジャーバージョンだけを持つ
 */
public record DatabaseEntity(String dbName, String dbmsName, int majorVersion) {}
