package com.export_table_definition.domain.model.value;

import com.export_table_definition.domain.model.entity.TableEntity;

/**
 * スキーマ名とテーブル名を組み合わせた値オブジェクト
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TableKey(String schema, String table) {

  /**
   * スキーマ名とテーブル名からTableKeyインスタンスを生成する静的ファクトリメソッド
   *
   * @param schema スキーマ名
   * @param table テーブル名
   * @return TableKeyインスタンス
   */
  public static TableKey of(String schema, String table) {
    return new TableKey(schema, table);
  }

  /**
   * TableEntityからTableKeyインスタンスを生成する静的ファクトリメソッド
   *
   * @param t TableEntityインスタンス
   * @return TableKeyインスタンス
   */
  public static TableKey of(TableEntity t) {
    return new TableKey(t.schemaName(), t.physicalTableName());
  }
}
