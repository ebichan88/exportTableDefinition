package com.dbxray.mcp.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一覧を返すツールの、返す範囲（{@code offset}・{@code limit}）
 *
 * @param offset 先頭から読み飛ばす件数
 * @param limit 返す件数の上限
 */
record Page(int offset, int limit) {

  /** 一覧を返すツールの{@code limit}の既定値（ツール固有の値を持たない場合） */
  static final int DEFAULT_LIMIT = 100;

  /** 一覧を返すツールの{@code limit}の上限（ツール固有の値を持たない場合） */
  static final int MAX_LIMIT = 500;

  /**
   * 引数{@code offset}・{@code limit}を読み取るメソッド（範囲は入力スキーマで検証済み）
   *
   * @throws InvalidToolArgumentException 数値でない場合
   */
  static Page read(ToolArguments arguments, int defaultLimit) {
    return new Page(
        arguments.optionalInt("offset", 0), arguments.optionalInt("limit", defaultLimit));
  }

  /** ツール固有の引数に、引数{@code offset}・{@code limit}の入力スキーマを加えたプロパティ */
  static Map<String, Object> withPageProperties(
      Map<String, Object> toolProperties, int defaultLimit, int maxLimit) {
    final Map<String, Object> properties = new LinkedHashMap<>(toolProperties);
    properties.put(
        "limit",
        ToolSpecifications.integerProperty("返す件数の上限（既定" + defaultLimit + "）", 1, maxLimit));
    properties.put(
        "offset",
        ToolSpecifications.integerProperty(
            "先頭から読み飛ばす件数（既定0）。続きは前回の結果のnextOffsetを指定する", 0, Integer.MAX_VALUE));
    return properties;
  }

  /** 全件のうち、この範囲の要素を返すメソッド */
  <T> List<T> apply(List<T> all) {
    return all.subList(
        Math.min(offset, all.size()), (int) Math.min((long) offset + limit, all.size()));
  }

  /**
   * 続きを取得するための{@code offset}を返すメソッド
   *
   * @param total 全件の数
   * @return 続きが無い場合はnull（結果のJSONに出力しない）
   */
  Integer nextOffset(int total) {
    final long next = (long) offset + limit;
    return next < total ? (int) next : null;
  }
}
