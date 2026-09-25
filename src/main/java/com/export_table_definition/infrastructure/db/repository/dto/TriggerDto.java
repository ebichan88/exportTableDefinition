package com.export_table_definition.infrastructure.db.repository.dto;

import com.export_table_definition.domain.model.entity.TriggerEntity;

/**
 * トリガー情報に関してORMのデータの受け渡しに利用するDTOクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
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
   * DTOからEntityへの変換メソッド
   *
   * @return TriggerEntityのインスタンス
   */
  public TriggerEntity toEntity() {
    return new TriggerEntity(
        schemaName,
        tableName,
        triggerName,
        timing,
        events,
        orientation,
        functionName,
        triggerDefinition);
  }
}
