package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.table.TriggerEntity;

/** トリガー情報に関してORMのデータの受け渡しに利用するDTOクラス */
public record TriggerDto(
    String schemaName,
    String tableName,
    String triggerName,
    String timing,
    String events,
    String orientation,
    String functionName,
    String triggerDefinition) {

  /**
   * DTOからEntityへの変換メソッド<br>
   * SQLがスラッシュ区切りで連結して返す対象イベントは、ここでリストへ分解する
   *
   * @return TriggerEntityのインスタンス
   */
  public TriggerEntity toEntity() {
    return new TriggerEntity(
        schemaName,
        tableName,
        triggerName,
        DtoValues.text(timing),
        DtoValues.split(events, "/"),
        DtoValues.text(orientation),
        DtoValues.text(functionName),
        DtoValues.text(triggerDefinition));
  }
}
