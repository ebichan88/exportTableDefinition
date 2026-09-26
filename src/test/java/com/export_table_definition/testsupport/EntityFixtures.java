package com.export_table_definition.testsupport;

import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;

/**
 * テストで用いるカラム・インデックス・制約・トリガーの生成ヘルパー<br>
 * 本番コードではこれらをSQLから受け取るため、表示用の属性をすべて指定するコンストラクタのみを持つ。 大半のテストはテーブルへの紐付け（スキーマ名・テーブル名）や一部の属性しか参照しないため、
 * ここで残りの属性に既定値を与え、テスト側の関心だけを記述できるようにする
 */
public final class EntityFixtures {

  private EntityFixtures() {}

  /**
   * 論理カラム名・桁数/精度・NOT NULL・デフォルト値を持たないカラムを生成する
   *
   * @param schema スキーマ名
   * @param table テーブル名
   * @param physicalColumnName 物理カラム名
   * @param columnType データ型
   * @param primaryKey 主キーを構成するカラムか
   * @return カラム
   */
  public static ColumnEntity column(
      String schema,
      String table,
      String physicalColumnName,
      String columnType,
      boolean primaryKey) {
    return new ColumnEntity(
        schema, table, "", physicalColumnName, columnType, "", primaryKey, false, "");
  }

  /**
   * スキーマ名・テーブル名のみを持つインデックスを生成する
   *
   * @param schema スキーマ名
   * @param table テーブル名
   * @return インデックス
   */
  public static IndexEntity index(String schema, String table) {
    return new IndexEntity(schema, table, "", "", false, false, "", "");
  }

  /**
   * スキーマ名・テーブル名のみを持つ制約を生成する
   *
   * @param schema スキーマ名
   * @param table テーブル名
   * @return 制約
   */
  public static ConstraintEntity constraint(String schema, String table) {
    return new ConstraintEntity(schema, table, "", "", "", "");
  }

  /**
   * スキーマ名・テーブル名のみを持つトリガーを生成する
   *
   * @param schema スキーマ名
   * @param table テーブル名
   * @return トリガー
   */
  public static TriggerEntity trigger(String schema, String table) {
    return new TriggerEntity(schema, table, "", "", "", "", "", "");
  }
}
