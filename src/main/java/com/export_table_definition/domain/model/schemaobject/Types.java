package com.export_table_definition.domain.model.schemaobject;

import java.util.List;
import java.util.stream.Stream;

/** ユーザー定義型情報の集合を扱うクラス */
public final class Types {

  private final List<TypeEntity> list;

  private Types(List<TypeEntity> list) {
    this.list = List.copyOf(list);
  }

  /**
   * ユーザー定義型情報のリストからインスタンスを生成する静的ファクトリメソッド
   *
   * @param list ユーザー定義型情報のリスト（取得順）
   */
  public static Types of(List<TypeEntity> list) {
    return new Types(list);
  }

  /**
   * ユーザー定義型情報のリストを取得するメソッド
   *
   * @return ユーザー定義型情報のリスト（取得順・変更不可）
   */
  public List<TypeEntity> asList() {
    return list;
  }

  /** ユーザー定義型が1件も存在しないか判定するメソッド */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /** ユーザー定義型情報のストリームを取得するメソッド */
  public Stream<TypeEntity> stream() {
    return list.stream();
  }
}
