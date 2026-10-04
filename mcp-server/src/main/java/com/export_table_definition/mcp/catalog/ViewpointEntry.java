package com.export_table_definition.mcp.catalog;

import java.util.List;

/**
 * 観点（業務ドメイン別にテーブルをまとめる切り口）の参考情報1件<br>
 * スキーマを持たない（DB単位の概念な）ため{@link SchemaObject}は実装せず、{@link SchemaCatalog}が専用の方法で解決する
 *
 * @param database DB名（参考情報の{@code {DB名}}ディレクトリ）
 * @param id 識別子
 * @param name 表示名
 * @param description 説明。未設定の場合は空文字
 * @param tables 所属テーブル
 */
public record ViewpointEntry(
    String database, String id, String name, String description, List<ObjectKey> tables) {

  /** 未設定の項目（null）を空文字・空リストへ揃える */
  public ViewpointEntry {
    database = TextValues.orEmpty(database);
    id = TextValues.orEmpty(id);
    name = TextValues.orEmpty(name);
    description = TextValues.orEmpty(description);
    tables = tables == null ? List.of() : List.copyOf(tables);
  }
}
