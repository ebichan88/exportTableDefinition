package com.export_table_definition.domain.model.entity;

import org.apache.commons.lang3.StringUtils;

/**
 * 関数・プロシージャ情報に関するrecordクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record FunctionEntity(
    String dbName,
    String schemaName,
    String functionName,
    String fileName,
    String functionListInfo,
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
