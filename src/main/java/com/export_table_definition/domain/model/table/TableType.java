package com.export_table_definition.domain.model.table;

import java.util.Arrays;
import java.util.Objects;

/**
 * テーブルの種類を表す列挙型
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public enum TableType {
  /** テーブル */
  TABLE("table"),
  /** ビュー */
  VIEW("view"),
  /** マテリアライズドビュー */
  MATERIALIZED_VIEW("materialized_view");

  private final String name;

  /**
   * コンストラクタ
   *
   * @param name テーブル種類名
   */
  TableType(String name) {
    this.name = name;
  }

  /**
   * テーブル種類名を返却する。
   *
   * @return テーブル種類名を返却する。
   */
  public String getName() {
    return name;
  }

  /**
   * テーブルの種類に紐づくEnumを返却する。
   *
   * @param name Enum逆引きに用いる値
   * @return TableTypeを返却する。
   * @throws IllegalArgumentException 対象のEnumが存在しない場合にthrowする。
   */
  public static TableType findByName(String name) {
    return Arrays.stream(TableType.values())
        .filter(e -> Objects.equals(name, e.getName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown table type: " + name));
  }

  /**
   * view または materialized viewであるか判定するメソッド
   *
   * @return view または materialized viewの場合はtrue、それ以外の場合はfalse
   */
  public boolean isView() {
    return switch (this) {
      case VIEW, MATERIALIZED_VIEW -> true;
      case TABLE -> false;
    };
  }
}
