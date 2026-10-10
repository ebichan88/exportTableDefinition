package com.dbxray.mcp.catalog;

import java.util.List;

/**
 * 同じ名前の関数・プロシージャ（オーバーロード）の集まり。名前で指定されたときの解決の単位
 *
 * @param overloads スナップショットの並び順。1つ以上
 */
public record FunctionOverloads(ObjectKey key, List<FunctionEntry> overloads)
    implements SchemaObject {

  /** 複製して変更できないようにする */
  public FunctionOverloads {
    overloads = List.copyOf(overloads);
  }
}
