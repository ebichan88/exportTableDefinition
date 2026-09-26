package com.export_table_definition.domain.model.schemaobject;

/**
 * 関数・プロシージャ情報に関するrecordクラス
 *
 * @param dbName データベース名
 * @param schemaName スキーマ名
 * @param functionName 関数・プロシージャ名
 * @param overloadIndex 同じスキーマ内の同名の関数・プロシージャ（オーバーロード）のうち何番目か（1始まり。作成順）
 * @param overloadCount 同じスキーマ内の同名の関数・プロシージャの数（オーバーロードが無い場合は1）
 * @param functionKind 種別（FUNCTION/PROCEDURE）
 * @param functionArguments 引数
 * @param functionResult 戻り値の型（プロシージャの場合は空文字）
 * @param languageName 実装言語
 * @param definition 定義本体（一覧取得時は空文字）
 */
public record FunctionEntity(
    String dbName,
    String schemaName,
    String functionName,
    int overloadIndex,
    int overloadCount,
    String functionKind,
    String functionArguments,
    String functionResult,
    String languageName,
    String definition) {

  /**
   * 同じスキーマに同名の関数・プロシージャが複数存在する（オーバーロードされている）か判定するメソッド
   *
   * @return 同名の関数・プロシージャが複数存在する場合はtrue
   */
  public boolean isOverloaded() {
    return overloadCount > 1;
  }

  /**
   * 関数・プロシージャのヘッダー表示名を取得するメソッド
   *
   * @return ヘッダー表示名（関数・プロシージャ名）
   */
  public String getHeaderName() {
    return functionName;
  }
}
