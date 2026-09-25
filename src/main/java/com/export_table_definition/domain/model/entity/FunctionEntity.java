package com.export_table_definition.domain.model.entity;

import org.apache.commons.lang3.StringUtils;

/**
 * 関数・プロシージャ情報に関するrecordクラス
 *
 * @param dbName データベース名
 * @param schemaName スキーマ名
 * @param functionName 関数・プロシージャ名
 * @param fileName 個別定義ファイル名（同名関数が複数存在する場合は連番を付与したもの）
 * @param functionKind 種別（FUNCTION/PROCEDURE）
 * @param functionArguments 引数
 * @param functionResult 戻り値の型（プロシージャの場合は空文字）
 * @param languageName 実装言語
 * @param definition 定義本体（一覧取得時は空文字）
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record FunctionEntity(
    String dbName,
    String schemaName,
    String functionName,
    String fileName,
    String functionKind,
    String functionArguments,
    String functionResult,
    String languageName,
    String definition) {

  /**
   * 論理名を持たない関数・プロシージャのヘッダー表示名を取得するメソッド
   *
   * @return ヘッダー表示名
   */
  public String getHeaderName() {
    return StringUtils.isBlank(functionName) ? fileName : functionName;
  }
}
