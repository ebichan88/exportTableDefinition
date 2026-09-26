package com.export_table_definition.domain.repository;

import com.export_table_definition.domain.model.database.DatabaseEntity;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.schemaobject.SequenceEntity;
import com.export_table_definition.domain.model.schemaobject.TypeEntity;
import com.export_table_definition.domain.model.table.TableDetail;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TriggerEntity;
import java.util.List;

/** テーブル定義出力に関するリポジトリインターフェース */
public interface TableDefinitionRepository {

  /** ドキュメントの生成日はDBではなく実行時に決まるため含まない */
  DatabaseEntity selectDatabase();

  /**
   * テーブル単位の絞り込みは呼び出し側（{@link
   * com.export_table_definition.domain.model.target.TableTargetScope}）がJava側で行うため、 スキーマ単位でのみ絞り込む
   */
  List<TableEntity> selectTableList(List<String> schemaList);

  /**
   * テーブル数に比例して重くなる情報のため、呼び出し側はスキーマ・チャンク単位でテーブルを渡す （同一スキーマのテーブルを渡すことを想定する）
   *
   * @return テーブルごとの詳細情報のリスト（{@code tables}と同じ順）
   */
  List<TableDetail> selectTableDetails(List<TableEntity> tables);

  /** テーブル単位の絞り込みは行わず、スキーマ全体を取得する（{@link #selectTableList}を参照） */
  List<ForeignKeyEntity> selectForeignKeyList(List<String> schemaList);

  /** テーブル単位の絞り込みは行わず、スキーマ全体を取得する（{@link #selectTableList}を参照） */
  List<TriggerEntity> selectTriggerList(List<String> schemaList);

  /** 定義本体を含まない軽量情報 */
  List<FunctionEntity> selectFunctionList(List<String> schemaList);

  /** {@link #selectFunctionList}と同じ項目（種別・引数・戻り値・言語）に加え、定義本体を含む */
  List<FunctionEntity> selectFunctionDefList(List<String> schemaList);

  List<SequenceEntity> selectSequenceList(List<String> schemaList);

  List<TypeEntity> selectTypeList(List<String> schemaList);
}
