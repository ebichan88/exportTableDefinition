package com.export_table_definition.domain.model.entity;

/**
 * データベースの基本情報に関するrecordクラス
 *
 * @param dbName データベース名
 * @param dbmsName DBMS種別（PostgreSQL/Oracle）
 * @param generatedDate テーブル定義書の生成日
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record BaseInfoEntity(String dbName, String dbmsName, String generatedDate) {}
