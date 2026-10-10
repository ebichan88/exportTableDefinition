package com.dbxray.domain.model.table;

import java.util.List;
import java.util.Map;

/** トリガー情報の集合を扱うクラス */
public final class Triggers extends AbstractEntities<TriggerEntity> {

  private final List<TriggerEntity> list;

  private Triggers(Map<TableKey, List<TriggerEntity>> byKey, List<TriggerEntity> list) {
    super(byKey);
    this.list = List.copyOf(list);
  }

  /**
   * トリガー情報のリストを、所属テーブルのテーブルキーで引けるようにする
   *
   * @param list トリガー情報のリスト（取得順）
   */
  public static Triggers of(List<TriggerEntity> list) {
    return new Triggers(index(list), list);
  }

  /**
   * トリガー情報のリストを取得するメソッド
   *
   * @return トリガー情報のリスト（取得順・変更不可）
   */
  public List<TriggerEntity> asList() {
    return list;
  }

  /** トリガーが1件も存在しないか判定するメソッド */
  public boolean isEmpty() {
    return list.isEmpty();
  }
}
