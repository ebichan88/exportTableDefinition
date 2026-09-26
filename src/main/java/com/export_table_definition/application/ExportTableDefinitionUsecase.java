package com.export_table_definition.application;

/**
 * テーブル定義出力（通常実行）のユースケースを扱うインターフェース
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface ExportTableDefinitionUsecase {

  /**
   * DBから取得したスキーマ情報を、Markdownのドキュメントとスキーマのスナップショットとして出力するメソッド
   *
   * @param request テーブル定義出力の入力
   */
  public void exportTableDefinition(ExportRequest request);
}
