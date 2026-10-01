package com.export_table_definition.application;

/** テーブル定義出力（通常実行）のユースケースを扱うインターフェース */
public interface ExportTableDefinitionUsecase {

  /** DBから取得したスキーマ情報を、Markdownのドキュメントとスキーマのスナップショットとして出力するメソッド */
  public void exportTableDefinition(ExportTableDefinitionRequest request);
}
