package com.dbxray.domain.model.snapshot;

import static com.dbxray.domain.model.snapshot.SnapshotValues.text;

import com.dbxray.domain.model.schemaobject.TypeEntity;

/**
 * スキーマのスナップショットのうち、1ユーザー定義型分の情報を表すrecordクラス
 *
 * @param category 種別（PostgreSQLはENUM/COMPOSITE/DOMAIN/RANGE、OracleはOBJECT/VARRAY/NESTED TABLE）
 */
public record TypeSnapshot(String schema, String name, String category, String definition) {

  /**
   * ユーザー定義型情報からスナップショットを生成するメソッド
   *
   * @return 1ユーザー定義型分のスナップショット
   */
  public static TypeSnapshot of(TypeEntity type) {
    return new TypeSnapshot(
        type.schemaName(), type.typeName(), text(type.typeCategory()), text(type.definition()));
  }
}
