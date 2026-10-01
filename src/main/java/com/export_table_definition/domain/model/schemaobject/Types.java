package com.export_table_definition.domain.model.schemaobject;

import java.util.Iterator;
import java.util.List;

/** ユーザー定義型情報の集合を扱うクラス */
public final class Types implements Iterable<TypeEntity> {

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

  /** {@inheritDoc} */
  @Override
  public Iterator<TypeEntity> iterator() {
    return list.iterator();
  }
}
