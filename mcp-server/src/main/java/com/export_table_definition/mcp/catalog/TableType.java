package com.export_table_definition.mcp.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** テーブルの区分（スナップショットの{@code type}の値） */
public enum TableType {
  TABLE("table"),
  VIEW("view"),
  MATERIALIZED_VIEW("materialized_view");

  private final String value;

  TableType(String value) {
    this.value = value;
  }

  /** スナップショットの{@code type}・ツールの引数で使う値を返すメソッド */
  public String value() {
    return value;
  }

  /** 全区分の値を返すメソッド（ツールの入力スキーマの{@code enum}に使う） */
  public static List<String> allValues() {
    return Arrays.stream(values()).map(TableType::value).toList();
  }

  /**
   * 値から区分を求めるメソッド
   *
   * @return 空文字・未知の値（cliが新しい区分を出力した場合等）は空
   */
  public static Optional<TableType> of(String value) {
    return Arrays.stream(values()).filter(type -> type.value.equals(value)).findFirst();
  }
}
