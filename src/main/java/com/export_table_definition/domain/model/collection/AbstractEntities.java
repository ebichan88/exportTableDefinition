package com.export_table_definition.domain.model.collection;

import com.export_table_definition.domain.model.entity.SchemaTableKeyed;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * エンティティの集合を扱う抽象クラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 * @param <T> エンティティの型
 */
public abstract class AbstractEntities<T extends SchemaTableKeyed> {
  protected final Map<TableKey, List<T>> byKey;

  /**
   * コンストラクタ
   *
   * @param byKey テーブルキーをキー、エンティティのリストを値とするマップ
   */
  protected AbstractEntities(Map<TableKey, List<T>> byKey) {
    this.byKey = Collections.unmodifiableMap(byKey);
  }

  /**
   * リストを、各エンティティが所属するテーブルのテーブルキーでインデックス化するユーティリティメソッド
   *
   * @param <E> エンティティの型
   * @param list エンティティのリスト
   * @return テーブルキーをキー、エンティティのリストを値とするマップ
   */
  protected static <E extends SchemaTableKeyed> Map<TableKey, List<E>> index(List<E> list) {
    return index(list, SchemaTableKeyed::tableKey);
  }

  /**
   * リストをテーブルキーでインデックス化するユーティリティメソッド
   *
   * @param <E> エンティティの型
   * @param list エンティティのリスト
   * @param keyFn エンティティからテーブルキーを抽出する関数
   * @return テーブルキーをキー、エンティティのリストを値とするマップ
   */
  protected static <E> Map<TableKey, List<E>> index(List<E> list, Function<E, TableKey> keyFn) {
    Map<TableKey, List<E>> map = new LinkedHashMap<>();
    // computeIfAbsentでListを初期化してからaddする
    list.forEach(e -> map.computeIfAbsent(keyFn.apply(e), k -> new ArrayList<>()).add(e));
    map.replaceAll((k, v) -> List.copyOf(v));
    return map;
  }

  /**
   * 指定されたテーブルに関連するエンティティのリストを取得するメソッド
   *
   * @param table テーブルエンティティ
   * @return エンティティのリスト。該当するエンティティがない場合は空のリストを返す
   */
  public List<T> of(TableEntity table) {
    return byKey.getOrDefault(TableKey.of(table), List.of());
  }
}
