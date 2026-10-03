package com.export_table_definition.mcp.catalog;

import java.util.List;

/**
 * 名前で指定されたオブジェクトを解決した結果
 *
 * @param <E> 解決するオブジェクトの種類
 */
public sealed interface Lookup<E extends SchemaObject> {

  /** 1つに定まった場合 */
  record Found<E extends SchemaObject>(E value) implements Lookup<E> {}

  /**
   * 同名のオブジェクトが複数のDB・スキーマにあり、1つに定まらない場合
   *
   * @param candidates 当てはまるオブジェクト（DB名・スキーマ名の順）
   */
  record Ambiguous<E extends SchemaObject>(List<E> candidates) implements Lookup<E> {}

  /**
   * 当てはまるオブジェクトが無い場合
   *
   * @param suggestions 名前の似たオブジェクト（似ている順。無ければ空）
   */
  record NotFound<E extends SchemaObject>(List<E> suggestions) implements Lookup<E> {}
}
