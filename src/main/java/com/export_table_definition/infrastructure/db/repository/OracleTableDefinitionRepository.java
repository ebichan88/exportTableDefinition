package com.export_table_definition.infrastructure.db.repository;

import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import com.export_table_definition.infrastructure.db.type.DatabaseType;
import java.util.List;

/**
 * [oracle]テーブル定義出力に関するリポジトリクラス
 *
 * <p>OracleのLong型をJDBCDriverでは扱えないため、Oracleにおいてデフォルト、制約、View/materialized_viewのソースは表示不可
 * 参考リンク：https://support.oracle.com/knowledge/Middleware/832903_1.html
 *
 * <p>トリガー・関数/プロシージャ・シーケンス・ユーザー定義型（ENUM等）の出力はPostgreSQL専用のため、 Oracleでは空リストを返却し出力しない。
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class OracleTableDefinitionRepository extends AbstractTableDefinitionRepository {

  /** コンストラクタ */
  public OracleTableDefinitionRepository() {
    super(DatabaseType.ORACLE);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Oracleではトリガー出力に非対応のため空リストを返却する。
   */
  @Override
  public List<TriggerEntity> selectTriggerList(List<String> schemaList) {
    return List.of();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Oracleでは関数・プロシージャ出力に非対応のため空リストを返却する。
   */
  @Override
  public List<FunctionEntity> selectFunctionList(List<String> schemaList) {
    return List.of();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Oracleでは関数・プロシージャ出力に非対応のため空リストを返却する。
   */
  @Override
  public List<FunctionEntity> selectFunctionDefList(List<String> schemaList) {
    return List.of();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Oracleではシーケンス出力に非対応のため空リストを返却する。
   */
  @Override
  public List<SequenceEntity> selectSequenceList(List<String> schemaList) {
    return List.of();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Oracleではユーザー定義型出力に非対応のため空リストを返却する。
   */
  @Override
  public List<TypeEntity> selectTypeList(List<String> schemaList) {
    return List.of();
  }
}
