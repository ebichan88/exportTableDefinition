package com.export_table_definition.mcp.tool;

import java.util.Arrays;
import java.util.List;

/** {@code get_table}で返す範囲を選べる、テーブル定義の項目（スナップショットの項目名、またはツールが加える項目名） */
enum TableSection {
  COLUMNS("columns"),
  INDEXES("indexes"),
  CONSTRAINTS("constraints"),
  FOREIGN_KEYS("foreignKeys"),
  LOGICAL_RELATIONS("logicalRelations"),
  TRIGGERS("triggers"),
  /** ビュー・マテリアライズドビューのソース定義 */
  DEFINITION("definition"),
  /** 定義本体にテーブル名が現れる関数。スナップショットには無く、ツールが求めて加える */
  MENTIONED_IN_FUNCTIONS("mentionedInFunctions");

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
   * 項目名から求めるメソッド
   *
   * @throws InvalidToolArgumentException いずれの項目名でもない場合
   */
  static TableSection of(String argumentName, String fieldName) {
    return Arrays.stream(values())
        .filter(section -> section.fieldName.equalsIgnoreCase(fieldName))
        .findFirst()
        .orElseThrow(() -> ToolArguments.invalidChoice(argumentName, fieldNames(), fieldName));
  }
}
