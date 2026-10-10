package com.dbxray.mcp.tool;

import com.dbxray.mcp.catalog.ObjectKey;
import java.nio.charset.StandardCharsets;

/**
 * テーブルを指すリソースのURI（{@code dbxray://{DB名}/{スキーマ名}/{テーブル名}}）の組み立て<br>
 * 各区間はパーセントエンコードする。DB名・スキーマ名・テーブル名はDB由来の信頼できない文字列で、{@code /}や空白、日本語を含みうるため
 */
final class TableResourceUri {

  private static final String SCHEME = "dbxray://";
  private static final String UNRESERVED = "-._~";

  private TableResourceUri() {}

  /** テーブルのURIを組み立てるメソッド */
  static String of(ObjectKey key) {
    return SCHEME + encode(key.database()) + "/" + encode(key.schema()) + "/" + encode(key.name());
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

  private static boolean isUnreserved(char c) {
    return (c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || UNRESERVED.indexOf(c) >= 0;
  }
}
