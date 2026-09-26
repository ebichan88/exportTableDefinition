package com.export_table_definition.domain.model.table;

/**
 * テーブル情報に関するrecordクラス
 *
 * @param dbName データベース名
 * @param schemaName スキーマ名
 * @param logicalTableName 論理テーブル名
 * @param physicalTableName 物理テーブル名
 * @param tableType 区分（table/view/materialized_view）
 * @param definition view/materialized viewの場合のソース定義（tableの場合は空文字）
 */
public record TableEntity(
    String dbName,
    String schemaName,
    String logicalTableName,
    String physicalTableName,
    TableType tableType,
    String definition) {

  /**
   * スキーマ.テーブル 形式の名称を取得するメソッド
   *
   * @return スキーマ.テーブル 形式の名称
   */
  public String getSchemaTableName() {
    return TableKey.of(this).qualifiedName();
  }

  /**
   * 論理テーブル名（物理テーブル名） 形式の名称を取得するメソッド
   *
   * @return 物理テーブル名（論理テーブル名） 形式の名称を返却。<br>
   *     論理テーブル名が存在しない場合は 物理テーブル名 形式の名称を返却
   */
  public String getHeaderTableName() {
    if (logicalTableName == null || logicalTableName.isBlank()) {
      return physicalTableName;
    }
    return physicalTableName + "（" + logicalTableName + "）";
  }

  /**
   * view または materialized viewであるか判定するメソッド
   *
   * @return view または materialized viewの場合はtrue。それ以外の場合はfalseを返却
   */
  public boolean isView() {
    return tableType.isView();
  }
}
