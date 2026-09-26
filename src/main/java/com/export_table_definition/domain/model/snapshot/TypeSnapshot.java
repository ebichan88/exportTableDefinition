package com.export_table_definition.domain.model.snapshot;

import static com.export_table_definition.domain.model.snapshot.SnapshotValues.text;

import com.export_table_definition.domain.model.schemaobject.TypeEntity;

/**
 * スキーマのスナップショットのうち、1ユーザー定義型分の情報を表すrecordクラス
 *
 * @param schema スキーマ名
 * @param name 型名
 * @param category 種別（ENUM/COMPOSITE/DOMAIN/RANGE）
 * @param definition 定義
 */
public record TypeSnapshot(String schema, String name, String category, String definition) {

  /**
   * ユーザー定義型情報からスナップショットを生成するメソッド
   *
   * @param type ユーザー定義型情報
   * @return 1ユーザー定義型分のスナップショット
   */
  public static TypeSnapshot of(TypeEntity type) {
    return new TypeSnapshot(
        type.schemaName(), type.typeName(), text(type.typeCategory()), text(type.definition()));
  }
}
