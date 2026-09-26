package com.export_table_definition.domain.model.target;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 出力対象を絞り込めるPostgreSQL固有オブジェクトの種別を表す列挙型
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public enum OutputObjectType {
  /** トリガー */
  TRIGGER("trigger"),
  /** 関数・プロシージャ */
  FUNCTION("function"),
  /** シーケンス */
  SEQUENCE("sequence"),
  /** ユーザー定義型 */
  TYPE("type");

  private final String name;

  /**
   * コンストラクタ
   *
   * @param name 出力対象オブジェクト種別名
   */
  OutputObjectType(String name) {
    this.name = name;
  }

  /**
   * 出力対象オブジェクト種別名を返却する。
   *
   * @return 出力対象オブジェクト種別名を返却する。
   */
  public String getName() {
    return name;
  }

  /**
   * 出力対象オブジェクト種別に紐づくEnumを返却する。
   *
   * @param name Enum逆引きに用いる値
   * @return OutputObjectTypeを返却する。
   * @throws IllegalArgumentException 対象のEnumが存在しない場合にthrowする。
   */
  public static OutputObjectType findByName(String name) {
    return Arrays.stream(OutputObjectType.values())
        .filter(e -> Objects.equals(name, e.getName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown output object type: " + name));
  }

  /**
   * 設定ファイルから読み込んだ出力対象オブジェクト種別名のリストから、出力を有効化する種別の集合を解決する。<br>
   * 種別名は前後の空白を除去して解釈し、空要素は無視する。指定が空の場合は、すべての種別を出力対象とする。
   *
   * @param rawList 出力対象オブジェクト種別名のリスト（{@link #getName()}の値。空の場合は全種別を対象とみなす）
   * @return 出力を有効化する種別の集合（変更不可）
   * @throws IllegalArgumentException 未知の種別名が含まれる場合にthrowする。
   */
  public static Set<OutputObjectType> parse(List<String> rawList) {
    final List<String> names =
        rawList.stream().map(String::strip).filter(name -> !name.isEmpty()).toList();
    if (names.isEmpty()) {
      return Collections.unmodifiableSet(EnumSet.allOf(OutputObjectType.class));
    }
    final EnumSet<OutputObjectType> result = EnumSet.noneOf(OutputObjectType.class);
    names.stream().map(OutputObjectType::findByName).forEach(result::add);
    return Collections.unmodifiableSet(result);
  }
}
