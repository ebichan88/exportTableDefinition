package com.export_table_definition.mcp.catalog;

import java.util.List;

/**
 * テーブル検索の結果
 *
 * @param total 一致したテーブルの総数（件数の上限で切り捨てる前の数）
 * @param hits 一致の強い順に、件数の上限まで並べたテーブル
 */
public record SearchResult(int total, List<TableHit> hits) {}
