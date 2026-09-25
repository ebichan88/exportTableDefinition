package com.export_table_definition.domain.model.entity;

import com.export_table_definition.domain.model.type.TableType;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * テーブル情報に関するrecordクラス
 *
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
    String tableInfoList,
    String tableInfo,
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
    if (StringUtils.isBlank(logicalTableName)) {
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
   *   <li>テーブル名リストのみ指定されている場合、テーブル名が一致すればtrueを返します。
   *   <li>両方指定されている場合、スキーマ名・テーブル名の両方が一致した場合のみtrueを返します。
   * </ul>
   *
   * @param targetSchemaList スキーマ名のリスト
   * @param targetTableList テーブル名のリスト
   * @return テーブル定義書の書き込みが必要かどうか
   */
  public boolean needsWriteTableDefinition(
      List<String> targetSchemaList, List<String> targetTableList) {
    final boolean hasSchemaList = CollectionUtils.isNotEmpty(targetSchemaList);
    final boolean hasTableList = CollectionUtils.isNotEmpty(targetTableList);

    if (!hasSchemaList && !hasTableList) {
      return true;
    }
    if (!hasSchemaList) {
      return targetTableList.contains(physicalTableName);
    }
    if (!hasTableList) {
      return targetSchemaList.contains(schemaName);
    }
    return targetSchemaList.contains(schemaName) && targetTableList.contains(physicalTableName);
  }
}
