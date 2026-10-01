package com.export_table_definition.domain.model.schemaobject;

import java.util.List;
import java.util.stream.Stream;

/** シーケンス情報の集合を扱うクラス */
public final class Sequences {

  private final List<SequenceEntity> list;

  private Sequences(List<SequenceEntity> list) {
    this.list = List.copyOf(list);
  }

  /**
   * シーケンス情報のリストからインスタンスを生成する静的ファクトリメソッド
   *
   * @param list シーケンス情報のリスト（取得順）
   */
  public static Sequences of(List<SequenceEntity> list) {
    return new Sequences(list);
  }

  /**
   * シーケンス情報のリストを取得するメソッド
   *
   * @return シーケンス情報のリスト（取得順・変更不可）
   */
  public List<SequenceEntity> asList() {
    return list;
  }

  /** シーケンスが1件も存在しないか判定するメソッド */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /** シーケンス情報のストリームを取得するメソッド */
  public Stream<SequenceEntity> stream() {
    return list.stream();
  }
}
