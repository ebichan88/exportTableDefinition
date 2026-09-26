package com.export_table_definition.infrastructure.db.repository;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import com.export_table_definition.domain.repository.TableDefinitionRepository;
import com.export_table_definition.infrastructure.db.MyBatisSqlSessionFactory;
import com.export_table_definition.infrastructure.db.repository.dto.BaseInfoDto;
import com.export_table_definition.infrastructure.db.repository.dto.ColumnDto;
import com.export_table_definition.infrastructure.db.repository.dto.ConstraintDto;
import com.export_table_definition.infrastructure.db.repository.dto.ForeignKeyDto;
import com.export_table_definition.infrastructure.db.repository.dto.FunctionDto;
import com.export_table_definition.infrastructure.db.repository.dto.IndexDto;
import com.export_table_definition.infrastructure.db.repository.dto.SequenceDto;
import com.export_table_definition.infrastructure.db.repository.dto.TableDto;
import com.export_table_definition.infrastructure.db.repository.dto.TriggerDto;
import com.export_table_definition.infrastructure.db.repository.dto.TypeDto;
import com.export_table_definition.infrastructure.db.type.DatabaseType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.ibatis.session.SqlSession;

/**
 * テーブル定義出力に関するリポジトリの基底クラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public abstract class AbstractTableDefinitionRepository implements TableDefinitionRepository {

  private final String baseSqlPath;

  /**
   * コンストラクタ
   *
   * @param databaseType データベースの種類
   */
  protected AbstractTableDefinitionRepository(DatabaseType databaseType) {
    this.baseSqlPath =
        "com.export_table_definition.domain.repository."
            + databaseType.getName()
            + ".TableDefinitionRepository.";
  }

  /** {@inheritDoc} */
  @Override
  public BaseInfoEntity selectBaseInfo() {
    try (SqlSession session = MyBatisSqlSessionFactory.openSession()) {
      final BaseInfoDto dto = session.selectOne(baseSqlPath + "selectBaseInfo");
      return dto.toEntity();
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<TableEntity> selectTableList(List<String> schemaList) {
    return selectTableDefinition(schemaList, List.of(), "selectAllTableInfo", TableDto::toEntity);
  }

  /** {@inheritDoc} */
  @Override
  public List<ColumnEntity> selectColumnList(List<String> schemaList, List<String> tableList) {
    return selectTableDefinition(schemaList, tableList, "selectAllColumnInfo", ColumnDto::toEntity);
  }

  /** {@inheritDoc} */
  @Override
  public List<IndexEntity> selectIndexList(List<String> schemaList, List<String> tableList) {
    return selectTableDefinition(schemaList, tableList, "selectAllIndexInfo", IndexDto::toEntity);
  }

  /** {@inheritDoc} */
  @Override
  public List<ConstraintEntity> selectConstraintList(
      List<String> schemaList, List<String> tableList) {
    return selectTableDefinition(
        schemaList, tableList, "selectAllConstraintInfo", ConstraintDto::toEntity);
  }

  /** {@inheritDoc} */
  @Override
  public List<ForeignKeyEntity> selectForeignKeyList(List<String> schemaList) {
    return selectTableDefinition(
        schemaList, List.of(), "selectAllForeignKeyInfo", ForeignKeyDto::toEntity);
  }

  /** {@inheritDoc} */
  @Override
  public List<TriggerEntity> selectTriggerList(List<String> schemaList) {
    return selectTableDefinition(
        schemaList, List.of(), "selectAllTriggerInfo", TriggerDto::toEntity);
  }

  /** {@inheritDoc} */
  @Override
  public List<FunctionEntity> selectFunctionList(List<String> schemaList) {
    return selectTableDefinition(
        schemaList, List.of(), "selectAllFunctionInfo", FunctionDto::toEntity);
  }

  /** {@inheritDoc} */
  @Override
  public List<FunctionEntity> selectFunctionDefList(List<String> schemaList) {
    return selectTableDefinition(
        schemaList, List.of(), "selectAllFunctionDefInfo", FunctionDto::toEntity);
  }

  /** {@inheritDoc} */
  @Override
  public List<SequenceEntity> selectSequenceList(List<String> schemaList) {
    return selectTableDefinition(
        schemaList, List.of(), "selectAllSequenceInfo", SequenceDto::toEntity);
  }

  /** {@inheritDoc} */
  @Override
  public List<TypeEntity> selectTypeList(List<String> schemaList) {
    return selectTableDefinition(schemaList, List.of(), "selectAllTypeInfo", TypeDto::toEntity);
  }

  private <D, E> List<E> selectTableDefinition(
      List<String> schemaList, List<String> tableList, String sqlId, Function<D, E> mapper) {
    final String sqlPath = baseSqlPath + sqlId;
    try (SqlSession session = MyBatisSqlSessionFactory.openSession()) {
      final List<D> dtoList =
          session.selectList(
              sqlPath,
              Map.ofEntries(
                  Map.entry("schemaList", schemaList), Map.entry("tableList", tableList)));
      return dtoList.stream().map(mapper).toList();
    } catch (Exception e) {
      throw new RuntimeException("Failed to select: " + sqlPath, e);
    }
  }
}
