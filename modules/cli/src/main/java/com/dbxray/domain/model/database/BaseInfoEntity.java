package com.dbxray.domain.model.database;

import java.time.LocalDate;

/**
 * 各ドキュメントの先頭に掲載する基本情報に関するrecordクラス<br>
 * データベースの情報（DBのカタログから取得する）と、ドキュメントの生成日（実行時に決まる）を組み合わせたもの
 *
 * @param dbmsName DBMS種別（PostgreSQL/Oracle）
 * @param majorVersion DBMSのメジャーバージョン
 */
public record BaseInfoEntity(
    String dbName, String dbmsName, int majorVersion, LocalDate generatedDate) {

  /**
   * データベースの情報と生成日から基本情報を生成する静的ファクトリメソッド
   *
   * @param database DBのカタログから取得したデータベースの情報
   */
  public static BaseInfoEntity of(DatabaseEntity database, LocalDate generatedDate) {
    return new BaseInfoEntity(
        database.dbName(), database.dbms().displayName(), database.majorVersion(), generatedDate);
  }
}
