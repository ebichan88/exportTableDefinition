package com.export_table_definition.mcp.catalog;

import java.util.List;

/**
 * 2つのテーブルをつなぐ最短経路の探索結果
 *
 * @param paths 最短の経路（どれも同じ長さ）。上限の段数以内でつながらない場合は空
 * @param hasMore 件数の上限で切り捨てた、同じ長さの経路が他にもあるか
 */
public record JoinPaths(List<JoinPath> paths, boolean hasMore) {}
