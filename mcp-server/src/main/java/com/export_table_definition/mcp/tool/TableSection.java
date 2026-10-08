package com.export_table_definition.mcp.tool;

import java.util.Arrays;
import java.util.List;

/** {@code get_table}で返す範囲を選べる、テーブル定義の項目（スナップショットの項目名） */
enum TableSection {
  COLUMNS("columns"),
  INDEXES("indexes"),
  CONSTRAINTS("constraints"),
  FOREIGN_KEYS("foreignKeys"),
  LOGICAL_RELATIONS("logicalRelations"),
  TRIGGERS("triggers"),
  /** ビュー・マテリアライズドビューのソース定義 */
  DEFINITION("definition"),
  /** ビュー・マテリアライズドビューが参照するテーブル */
  REFERENCED_TABLES("referencedTables"),
  /** テーブルを参照しているビュー。スナップショットの項目ではなく、ビューの{@code referencedTables}から逆引きする */
  REFERENCED_BY_VIEWS("referencedByViews");

  private final String fieldName;

  TableSection(String fieldName) {
    this.fieldName = fieldName;
  }

  String fieldName() {
    return fieldName;
  }

  /** ツールの入力スキーマ（enum）に使う、項目名の一覧 */
  static List<String> fieldNames() {
    return Arrays.stream(values()).map(TableSection::fieldName).toList();
  }

  /**
   * 項目名から求めるメソッド（入力スキーマの{@code enum}でSDKが検証済みのため、完全一致で照合する）
   *
   * @throws InvalidToolArgumentException いずれの項目名でもない場合
   */
  static TableSection of(String argumentName, String fieldName) {
    return Arrays.stream(values())
        .filter(section -> section.fieldName.equals(fieldName))
        .findFirst()
        .orElseThrow(() -> ToolArguments.invalidChoice(argumentName, fieldNames(), fieldName));
  }
}
