package com.export_table_definition.mcp.tool;

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

  /**
   * 引数{@code offset}・{@code limit}を読み取るメソッド
   *
   * @throws InvalidToolArgumentException 範囲外の値の場合
   */
  static Page read(ToolArguments arguments, int defaultLimit, int maxLimit) {
    return new Page(
        arguments.optionalInt("offset", 0, 0, Integer.MAX_VALUE),
        arguments.optionalInt("limit", defaultLimit, 1, maxLimit));
  }

  /** 引数{@code offset}・{@code limit}の入力スキーマ */
  static Map<String, Object> properties(int defaultLimit, int maxLimit) {
    final Map<String, Object> properties = new LinkedHashMap<>();
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
