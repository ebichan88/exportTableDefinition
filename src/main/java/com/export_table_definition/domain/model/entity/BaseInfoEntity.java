package com.export_table_definition.domain.model.entity;

import java.time.LocalDate;

/**
 * 各ドキュメントの先頭に掲載する基本情報に関するrecordクラス<br>
 * データベースの情報（DBのカタログから取得する）と、ドキュメントの生成日（実行時に決まる）を組み合わせたもの
 *
 * @param dbName データベース名
 * @param dbmsName DBMS種別（PostgreSQL/Oracle）
 * @param generatedDate テーブル定義書の生成日
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record BaseInfoEntity(String dbName, String dbmsName, LocalDate generatedDate) {

  /**
   * データベースの情報と生成日から基本情報を生成する静的ファクトリメソッド
   *
   * @param database DBのカタログから取得したデータベースの情報
   * @param generatedDate テーブル定義書の生成日
   * @return 基本情報
   */
  public static BaseInfoEntity of(DatabaseEntity database, LocalDate generatedDate) {
    return new BaseInfoEntity(database.dbName(), database.dbmsName(), generatedDate);
  }
}
