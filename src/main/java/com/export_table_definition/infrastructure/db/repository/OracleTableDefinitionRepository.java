package com.export_table_definition.infrastructure.db.repository;

import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.infrastructure.db.type.DatabaseType;
import jakarta.inject.Inject;
import java.util.List;
import org.apache.ibatis.session.SqlSessionFactory;

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

  /**
   * コンストラクタ
   *
   * @param sqlSessionFactory 接続先DBのSqlSessionFactory
   */
  @Inject
  public OracleTableDefinitionRepository(SqlSessionFactory sqlSessionFactory) {
    super(DatabaseType.ORACLE, sqlSessionFactory);
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
