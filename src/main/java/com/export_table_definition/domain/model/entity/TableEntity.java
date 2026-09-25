package com.export_table_definition.domain.model.entity;

import com.export_table_definition.domain.model.type.TableType;
import com.export_table_definition.domain.model.value.TableTargetFilter;
import java.util.List;

/**
 * テーブル情報に関するrecordクラス
 *
 * @param dbName データベース名
 * @param schemaName スキーマ名
 * @param logicalTableName 論理テーブル名
 * @param physicalTableName 物理テーブル名
 * @param tableType 区分（table/view/materialized_view）
 * @param remarks テーブル一覧セクションの備考欄（現状は常に空白。手動付帯情報とは無関係）
 * @param definition view/materialized viewの場合のソース定義（tableの場合は空文字）
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TableEntity(
    String dbName,
    String schemaName,
    String logicalTableName,
    String physicalTableName,
    String tableType,
    String remarks,
    String definition) {

  /**
   * スキーマ.テーブル 形式の名称を取得するメソッド
   *
   * @return スキーマ.テーブル 形式の名称
   */
  public String getSchemaTableName() {
    return schemaName + "." + physicalTableName;
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
   * @return view または materialized viewの場合はture。それ以外の場合はfalseを返却
   */
  public boolean isView() {
    return TableType.isViewType(this.tableType);
  }

  /**
   * テーブル定義書の作成を行うか判定するメソッド<br>
   *
   * <ul>
   *   <li>スキーマ名リスト・テーブル名リストの両方が空またはnullの場合、常にtrueを返します。
   *   <li>スキーマ名リストのみ指定されている場合、スキーマ名が一致すればtrueを返します。
   *   <li>テーブル名リストのみ指定されている場合、テーブル名パターンが一致すればtrueを返します。
   *   <li>両方指定されている場合、スキーマ名・テーブル名パターンの両方が一致した場合のみtrueを返します。
   * </ul>
   *
   * テーブル名リストはワイルドカード（{@code *}）・除外（先頭に{@code !}）・スキーマ修飾（{@code スキーマ名.テーブル名}）に対応する。詳細は{@link
   * TableTargetFilter}を参照
   *
   * @param targetSchemaList スキーマ名のリスト
   * @param targetTableList テーブル名パターンのリスト
   * @return テーブル定義書の書き込みが必要かどうか
   */
  public boolean needsWriteTableDefinition(
      List<String> targetSchemaList, List<String> targetTableList) {
    final boolean hasSchemaList = targetSchemaList != null && !targetSchemaList.isEmpty();
    final TableTargetFilter tableFilter = TableTargetFilter.of(targetTableList);
    final boolean hasTableList = !tableFilter.isEmpty();

    if (!hasSchemaList && !hasTableList) {
      return true;
    }
    if (!hasSchemaList) {
      return tableFilter.matches(schemaName, physicalTableName);
    }
    if (!hasTableList) {
      return targetSchemaList.contains(schemaName);
    }
    return targetSchemaList.contains(schemaName)
        && tableFilter.matches(schemaName, physicalTableName);
  }
}
