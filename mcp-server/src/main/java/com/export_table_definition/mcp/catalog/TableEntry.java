package com.export_table_definition.mcp.catalog;

import java.util.List;

/**
 * スナップショットの{@code tables.jsonl}の1行（1テーブル）を、検索・リレーションのたどりに必要な項目に絞って保持するrecord
 *
 * @param type 区分（table/view/materialized_view）
 * @param description テーブル説明（サイドカーYAML由来）。未設定の場合は空文字
 * @param remarks テーブル備考（サイドカーYAML由来）。未設定の場合は空文字
 * @param foreignKeys DBに実在する外部キー制約
 * @param logicalRelations サイドカーYAMLで宣言された論理リレーション
 * @param triggers トリガー
 * @param referencedTables ビューの場合の、参照するテーブル（ビューを含む）。スナップショットに含まれないテーブルの場合もある
 * @param json スナップショットの1行そのもの。テーブル定義の全項目を返すときに使う
 */
public record TableEntry(
    ObjectKey key,
    String logicalName,
    String type,
    String description,
    String remarks,
    List<ColumnEntry> columns,
    List<RelationEntry> foreignKeys,
    List<RelationEntry> logicalRelations,
    List<TriggerEntry> triggers,
    List<ObjectKey> referencedTables,
    String json)
    implements SchemaObject {

  /** 未設定の項目（null）を空文字・空リストへ揃える */
  public TableEntry {
    logicalName = TextValues.orEmpty(logicalName);
    type = TextValues.orEmpty(type);
    description = TextValues.orEmpty(description);
    remarks = TextValues.orEmpty(remarks);
    columns = columns == null ? List.of() : List.copyOf(columns);
    foreignKeys = foreignKeys == null ? List.of() : List.copyOf(foreignKeys);
    logicalRelations = logicalRelations == null ? List.of() : List.copyOf(logicalRelations);
    triggers = triggers == null ? List.of() : List.copyOf(triggers);
    referencedTables = referencedTables == null ? List.of() : List.copyOf(referencedTables);
  }
}
