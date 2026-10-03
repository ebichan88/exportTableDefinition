package com.export_table_definition.mcp.tool;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** ツールに渡された引数の読み取りと検証 */
final class ToolArguments {

  private final Map<String, Object> values;

  /**
   * @param values ツールに渡された引数。nullの場合は引数なしとみなす
   * @param allowedNames ツールが受け付ける引数名
   * @throws InvalidToolArgumentException 受け付けない引数名が含まれる場合
   */
  ToolArguments(Map<String, Object> values, Set<String> allowedNames) {
    this.values = values == null ? Map.of() : values;
    for (final String name : this.values.keySet()) {
      if (!allowedNames.contains(name)) {
        throw new InvalidToolArgumentException(
            "未知の引数です: " + name + "。使える引数: " + String.join(", ", new TreeSet<>(allowedNames)));
      }
    }
  }

  /**
   * 必須の文字列の引数を読み取るメソッド
   *
   * @return 前後の空白を除いた値
   * @throws InvalidToolArgumentException 未指定・空・文字列でない場合
   */
  String requiredString(String name) {
    final String value = optionalString(name);
    if (value.isEmpty()) {
      throw new InvalidToolArgumentException("引数" + name + "を指定してください。");
    }
    return value;
  }

  /**
   * 任意の文字列の引数を読み取るメソッド
   *
   * @return 前後の空白を除いた値。未指定（null）の場合は空文字
   * @throws InvalidToolArgumentException 文字列でない場合
   */
  String optionalString(String name) {
    final Object value = values.get(name);
    if (value == null) {
      return "";
    }
    if (!(value instanceof String text)) {
      throw new InvalidToolArgumentException("引数" + name + "には文字列を指定してください。");
    }
    return text.strip();
  }

  /**
   * 任意の整数の引数を読み取るメソッド
   *
   * @return 引数の値。未指定（null）の場合は{@code defaultValue}
   * @throws InvalidToolArgumentException 整数として解釈できない場合、{@code min}〜{@code max}の範囲外の場合
   */
  int optionalInt(String name, int defaultValue, int min, int max) {
    final Object value = values.get(name);
    if (value == null) {
      return defaultValue;
    }
    final Integer parsed = toInteger(value);
    if (parsed == null || parsed < min || parsed > max) {
      throw new InvalidToolArgumentException(
          "引数" + name + "には" + min + "〜" + max + "の整数を指定してください。 [value=" + value + "]");
    }
    return parsed;
  }

  /**
   * 任意の列挙値の引数を読み取るメソッド（列挙定数名を小文字にした値で受け付ける）
   *
   * @return 引数の値。未指定（null）の場合は{@code defaultValue}
   * @throws InvalidToolArgumentException 列挙定数のいずれにも当てはまらない場合
   */
  <E extends Enum<E>> E optionalEnum(String name, Class<E> type, E defaultValue) {
    final String value = optionalString(name);
    if (value.isEmpty()) {
      return defaultValue;
    }
    for (final E constant : type.getEnumConstants()) {
      if (lowerName(constant).equals(value.toLowerCase(Locale.ROOT))) {
        return constant;
      }
    }
    throw new InvalidToolArgumentException(
        "引数"
            + name
            + "には"
            + String.join(", ", lowerNames(type))
            + "のいずれかを指定してください。 [value="
            + value
            + "]");
  }

  /** ツールの入力スキーマ（enum）にも使う、列挙定数名を小文字にした値の一覧 */
  static <E extends Enum<E>> List<String> lowerNames(Class<E> type) {
    return Arrays.stream(type.getEnumConstants()).map(ToolArguments::lowerName).toList();
  }

  private static String lowerName(Enum<?> constant) {
    return constant.name().toLowerCase(Locale.ROOT);
  }

  /** JSONの数値（Integer・Long・小数部が0のDouble）と、数字だけの文字列を整数として読む。読めない場合はnull */
  private static Integer toInteger(Object value) {
    if (value instanceof Number number) {
      final double asDouble = number.doubleValue();
      if (asDouble == Math.rint(asDouble) && Math.abs(asDouble) <= Integer.MAX_VALUE) {
        return (int) asDouble;
      }
      return null;
    }
    if (value instanceof String text) {
      try {
        return Integer.valueOf(text.strip());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }
}
