package com.dbxray.domain.model.table;

/**
 * ビュー（マテリアライズドビューを含む）が参照するテーブル（ビューを含む）1件に関するrecordクラス<br>
 * DBが保持する依存関係から取得するため、ビューのSQLを解析しない。所属するテーブル（{@link #tableKey()}）は参照する側のビュー
 *
 * @param tableName 参照する側のビューの名前
 * @param tableType 参照する側のビューの区分（ビュー・マテリアライズドビュー）
 * @param referenceTableType 参照されるテーブルの区分
 */
public record ViewReferenceEntity(
    String schemaName,
    String tableName,
    TableType tableType,
    String referenceSchemaName,
    String referenceTableName,
    TableType referenceTableType)
    implements SchemaTableKeyed {

  /** 参照されるテーブルのテーブルキーを取得するメソッド */
  public TableKey referenceTableKey() {
    return TableKey.of(referenceSchemaName, referenceTableName);
  }
}
