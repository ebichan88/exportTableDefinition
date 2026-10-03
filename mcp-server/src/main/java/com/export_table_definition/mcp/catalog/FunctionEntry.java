package com.export_table_definition.mcp.catalog;

/**
 * スナップショットの{@code functions.jsonl}の1行（1関数・プロシージャ）
 *
 * @param key 同名の関数（オーバーロード）は同じキーになる
 * @param kind 種別（FUNCTION/PROCEDURE）。未設定の場合は空文字
 * @param arguments 引数（デフォルト値を含む）。引数が無い場合は空文字
 * @param result 戻り値の型。プロシージャの場合は空文字
 * @param language 記述言語（plpgsql/sql等）。未設定の場合は空文字
 * @param definition 定義本体。AIのコンテキストを圧迫するためツールの結果には出さず、名前の出現の判定にだけ使う
 * @param json スナップショットの1行そのもの
 */
public record FunctionEntry(
    ObjectKey key,
    String kind,
    String arguments,
    String result,
    String language,
    String definition,
    String json) {

  /** 未設定の項目（null）を空文字へ揃える */
  public FunctionEntry {
    kind = TextValues.orEmpty(kind);
    arguments = TextValues.orEmpty(arguments);
    result = TextValues.orEmpty(result);
    language = TextValues.orEmpty(language);
    definition = TextValues.orEmpty(definition);
  }
}
