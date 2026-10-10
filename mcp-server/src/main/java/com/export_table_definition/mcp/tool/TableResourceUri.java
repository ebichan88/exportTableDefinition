package com.export_table_definition.mcp.tool;

import com.export_table_definition.mcp.catalog.ObjectKey;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * テーブルを指すリソースのURI（{@code exporttable://{DB名}/{スキーマ名}/{テーブル名}}）の組み立てと解釈<br>
 * 各区間はパーセントエンコードする。MCP SDKのURIテンプレートの変数は{@code /}を含められない（{@code ([^/]+)}に展開される）ため
 */
final class TableResourceUri {

  static final String TEMPLATE = "exporttable://{database}/{schema}/{table}";

  private static final String SCHEME = "exporttable://";
  private static final String UNRESERVED = "-._~";

  private TableResourceUri() {}

  /** テーブルのURIを組み立てるメソッド */
  static String of(ObjectKey key) {
    return SCHEME + encode(key.database()) + "/" + encode(key.schema()) + "/" + encode(key.name());
  }

  /**
   * URIからテーブルのキーを取り出すメソッド
   *
   * @return DB名・スキーマ名・テーブル名の3区間が揃っていない場合、または別のURIの場合は空
   */
  static Optional<ObjectKey> parse(String uri) {
    if (uri == null || !uri.startsWith(SCHEME)) {
      return Optional.empty();
    }
    final String[] segments = uri.substring(SCHEME.length()).split("/", -1);
    if (segments.length != 3) {
      return Optional.empty();
    }
    final String database = decode(segments[0]);
    final String schema = decode(segments[1]);
    final String name = decode(segments[2]);
    if (database.isEmpty() || schema.isEmpty() || name.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new ObjectKey(database, schema, name));
  }

  /** URIの1区間にするためのパーセントエンコード（UTF-8。英数字と{@code -._~}以外を変換する） */
  static String encode(String segment) {
    final StringBuilder encoded = new StringBuilder();
    for (final byte b : segment.getBytes(StandardCharsets.UTF_8)) {
      final char c = (char) (b & 0xff);
      if (isUnreserved(c)) {
        encoded.append(c);
      } else {
        encoded.append('%').append(String.format("%02X", b & 0xff));
      }
    }
    return encoded.toString();
  }

  /** パーセントエンコードされた1区間を戻すメソッド。{@code +}は空白ではなくそのまま扱う */
  static String decode(String segment) {
    try {
      return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return segment;
    }
  }

  private static boolean isUnreserved(char c) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || UNRESERVED.indexOf(c) >= 0;
  }
}
