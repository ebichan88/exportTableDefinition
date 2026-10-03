package com.export_table_definition.mcp.tool;

import java.util.Arrays;
import java.util.LinkedHashSet;
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
   * 任意の真偽値の引数を読み取るメソッド（JSONの真偽値と、{@code true}・{@code false}の文字列を受け付ける）
   *
   * @return 引数の値。未指定（null）の場合は{@code defaultValue}
   * @throws InvalidToolArgumentException 真偽値として解釈できない場合
   */
  boolean optionalBoolean(String name, boolean defaultValue) {
    final Object value = values.get(name);
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof String text
        && (text.strip().equalsIgnoreCase("true") || text.strip().equalsIgnoreCase("false"))) {
      return Boolean.parseBoolean(text.strip());
    }
    throw new InvalidToolArgumentException(
        "引数" + name + "にはtrueまたはfalseを指定してください。 [value=" + value + "]");
  }

  /**
   * 任意の文字列のリストの引数を読み取るメソッド<br>
   * JSONの配列のほか、カンマ区切りの文字列も受け付ける（配列を文字列にして渡すMCPクライアントがあるため）
   *
   * @return 前後の空白を除き、空の要素と重複を除いた値（指定順）。未指定（null）の場合は空リスト
   * @throws InvalidToolArgumentException 配列・文字列でない場合、文字列でない要素を含む場合
   */
  List<String> optionalStringList(String name) {
    final Object value = values.get(name);
    if (value == null) {
      return List.of();
    }
    final List<?> elements;
    if (value instanceof List<?> list) {
      elements = list;
    } else if (value instanceof String text) {
      elements = Arrays.asList(text.split(","));
    } else {
      throw new InvalidToolArgumentException("引数" + name + "には文字列の配列を指定してください。");
    }
    final Set<String> result = new LinkedHashSet<>();
    for (final Object element : elements) {
      if (!(element instanceof String text)) {
        throw new InvalidToolArgumentException("引数" + name + "には文字列の配列を指定してください。");
      }
      if (!text.isBlank()) {
        result.add(text.strip());
      }
    }
    return List.copyOf(result);
  }

  /**
   * 任意の、決まった値のいずれかを取る文字列の引数を読み取るメソッド（大文字小文字を区別しない）
   *
   * @param choices 受け付ける値
   * @return {@code choices}のうち当てはまった値。未指定（null・空）の場合は空文字
   * @throws InvalidToolArgumentException {@code choices}のいずれにも当てはまらない場合
   */
  String optionalChoice(String name, List<String> choices) {
    final String value = optionalString(name);
    if (value.isEmpty()) {
      return value;
    }
    return choices.stream()
        .filter(choice -> choice.equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> invalidChoice(name, choices, value));
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
    throw invalidChoice(name, lowerNames(type), value);
  }

  /** 決まった値のいずれでもない引数の誤り */
  static InvalidToolArgumentException invalidChoice(
      String name, List<String> choices, String value) {
    return new InvalidToolArgumentException(
        "引数" + name + "には" + String.join(", ", choices) + "のいずれかを指定してください。 [value=" + value + "]");
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
