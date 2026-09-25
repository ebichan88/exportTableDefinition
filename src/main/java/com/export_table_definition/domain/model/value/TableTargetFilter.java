package com.export_table_definition.domain.model.value;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code table=}に指定された絞り込みパターンを判定する値オブジェクト<br>
 * 以下の記法に対応する。
 *
 * <ul>
 *   <li>完全一致（従来どおり）: {@code employee}
 *   <li>ワイルドカード（{@code *}は0文字以上の任意文字列）: {@code *_bk}, {@code *_20240101}
 *   <li>除外（先頭に{@code !}）: {@code !flyway_schema_history}
 *   <li>スキーマ修飾（同名テーブルが複数スキーマに存在する場合に対象スキーマを限定）: {@code sample.employee}
 * </ul>
 *
 * 除外パターンは包含パターンより常に優先される。包含パターンが1件も指定されていない場合は、 除外パターンに一致しない限りすべてのテーブルが対象となる。
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class TableTargetFilter {

  private static final String EXCLUDE_PREFIX = "!";
  private static final String SCHEMA_TABLE_SEPARATOR = ".";
  private static final String WILDCARD = "*";

  private final List<Entry> includes;
  private final List<Entry> excludes;

  private TableTargetFilter(List<Entry> includes, List<Entry> excludes) {
    this.includes = includes;
    this.excludes = excludes;
  }

  /**
   * 生のパターン文字列のリストから{@link TableTargetFilter}を生成する静的ファクトリメソッド
   *
   * @param rawPatterns {@code table=}に指定されたパターン文字列のリスト
   * @return 生成したフィルター
   */
  public static TableTargetFilter of(List<String> rawPatterns) {
    if (rawPatterns == null || rawPatterns.isEmpty()) {
      return new TableTargetFilter(List.of(), List.of());
    }
    final List<Entry> includes = new ArrayList<>();
    final List<Entry> excludes = new ArrayList<>();
    for (final String rawPattern : rawPatterns) {
      if (rawPattern == null || rawPattern.isBlank()) {
        continue;
      }
      final boolean negate = rawPattern.startsWith(EXCLUDE_PREFIX);
      final String pattern = negate ? rawPattern.substring(EXCLUDE_PREFIX.length()) : rawPattern;
      (negate ? excludes : includes).add(Entry.parse(pattern));
    }
    return new TableTargetFilter(List.copyOf(includes), List.copyOf(excludes));
  }

  /**
   * パターンが1件も指定されていないか判定するメソッド
   *
   * @return 包含・除外いずれのパターンも指定されていない場合はtrue
   */
  public boolean isEmpty() {
    return includes.isEmpty() && excludes.isEmpty();
  }

  /**
   * 指定されたテーブルがこのフィルターの対象となるか判定するメソッド<br>
   * 除外パターンに一致する場合は常にfalse。包含パターンが1件もない場合、除外に一致しない限りtrue
   *
   * @param schemaName スキーマ名
   * @param physicalTableName 物理テーブル名
   * @return 対象となる場合はtrue
   */
  public boolean matches(String schemaName, String physicalTableName) {
    if (excludes.stream().anyMatch(entry -> entry.matches(schemaName, physicalTableName))) {
      return false;
    }
    if (includes.isEmpty()) {
      return true;
    }
    return includes.stream().anyMatch(entry -> entry.matches(schemaName, physicalTableName));
  }

  /**
   * 1件分のパターン（スキーマ修飾の有無 + テーブル名のワイルドカードパターン）
   *
   * @param schema スキーマ名。スキーマ修飾がない場合はnull（全スキーマが対象）
   * @param tablePattern テーブル名を判定する正規表現
   */
  private record Entry(String schema, Pattern tablePattern) {

    /**
     * パターン文字列を解析するメソッド
     *
     * @param pattern {@code !}を除いたパターン文字列
     * @return 解析結果
     */
    static Entry parse(String pattern) {
      final int separatorIndex = pattern.indexOf(SCHEMA_TABLE_SEPARATOR);
      final String schema =
          separatorIndex >= 0 ? pattern.substring(0, separatorIndex).trim() : null;
      final String tablePart =
          (separatorIndex >= 0 ? pattern.substring(separatorIndex + 1) : pattern).trim();
      return new Entry(schema, toPattern(tablePart));
    }

    /**
     * このエントリがテーブルに一致するか判定するメソッド
     *
     * @param schemaName スキーマ名
     * @param physicalTableName 物理テーブル名
     * @return 一致する場合はtrue
     */
    boolean matches(String schemaName, String physicalTableName) {
      if (schema != null && !schema.equals(schemaName)) {
        return false;
      }
      return tablePattern.matcher(physicalTableName).matches();
    }

    /**
     * {@code *}をワイルドカードとして扱う正規表現へ変換するメソッド
     *
     * @param glob {@code *}を含みうるテーブル名パターン
     * @return 変換した正規表現
     */
    private static Pattern toPattern(String glob) {
      final StringBuilder regex = new StringBuilder();
      for (int i = 0; i < glob.length(); i++) {
        final char c = glob.charAt(i);
        regex.append(c == WILDCARD.charAt(0) ? ".*" : Pattern.quote(String.valueOf(c)));
      }
      return Pattern.compile(regex.toString());
    }
  }
}
