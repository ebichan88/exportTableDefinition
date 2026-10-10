package com.dbxray.mcp.catalog;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SQLの識別子・式（カラムの型・デフォルト値・トリガーの関数）に現れる、オブジェクトの名前の判定 */
final class SqlNames {

  /** {@code nextval('sample.orders_id_seq'::regclass)}のシーケンス名。PostgreSQLがカラムのデフォルト値を表示する形 */
  private static final Pattern NEXTVAL =
      Pattern.compile("nextval\\(\\s*'([^']+)'", Pattern.CASE_INSENSITIVE);

  /** 配列型（{@code sample.status[]}）の要素の型を取り出すために除く、末尾の{@code []} */
  private static final Pattern ARRAY_SUFFIX = Pattern.compile("(\\[\\d*])+$");

  private SqlNames() {}

  /**
   * カラムのデフォルト値が採番に使うシーケンスを求めるメソッド
   *
   * @param table カラムを持つテーブル。シーケンス名がスキーマ修飾されていない場合は、このテーブルと同じスキーマとみなす
   * @return デフォルト値が{@code nextval('…')}でない場合は空
   */
  static Optional<ObjectKey> sequenceOfDefault(String defaultValue, ObjectKey table) {
    final Matcher matcher = NEXTVAL.matcher(defaultValue);
    return matcher.find() ? Optional.of(qualify(matcher.group(1), table)) : Optional.empty();
  }

  /**
   * カラムの型（{@code sample.status}・{@code sample.status[]}等）が指すオブジェクトを求めるメソッド
   *
   * @param table カラムを持つテーブル。型名がスキーマ修飾されていない場合は、このテーブルと同じスキーマとみなす
   */
  static ObjectKey typeOfColumn(String columnType, ObjectKey table) {
    return qualify(ARRAY_SUFFIX.matcher(columnType.strip()).replaceFirst(""), table);
  }

  /**
   * トリガーが実行する関数を求めるメソッド
   *
   * @param function {@code スキーマ名.関数名}（引数の括弧が付いていてもよい）
   * @param table トリガーを持つテーブル。関数名がスキーマ修飾されていない場合は、このテーブルと同じスキーマとみなす
   */
  static ObjectKey functionOfTrigger(String function, ObjectKey table) {
    final int parenthesis = function.indexOf('(');
    return qualify(parenthesis < 0 ? function : function.substring(0, parenthesis), table);
  }

  /** 2つのキーが同じオブジェクトを指すか（大文字小文字を区別しない）判定するメソッド */
  static boolean sameObject(ObjectKey left, ObjectKey right) {
    return left.database().equalsIgnoreCase(right.database())
        && left.schema().equalsIgnoreCase(right.schema())
        && left.name().equalsIgnoreCase(right.name());
  }

  /** {@code スキーマ名.名前}・{@code 名前}（二重引用符で囲まれていてもよい）を、DB名・スキーマ名を補ったキーにする */
  private static ObjectKey qualify(String identifier, ObjectKey context) {
    final String unquoted = identifier.replace("\"", "").strip();
    final int dot = unquoted.indexOf('.');
    return dot < 0
        ? new ObjectKey(context.database(), context.schema(), unquoted)
        : new ObjectKey(
            context.database(), unquoted.substring(0, dot), unquoted.substring(dot + 1));
  }
}
