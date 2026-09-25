package com.export_table_definition.domain.repository;

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
import java.util.List;
import java.util.function.Function;

/**
 * テーブル定義出力に関するリポジトリインターフェース
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface TableDefinitionRepository {

  /**
   * データベースの基本情報を取得するメソッド
   *
   * @return データベースの基本情報
   */
  BaseInfoEntity selectBaseInfo();

  /**
   * データベースのテーブル情報を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @param tableList テーブル定義出力対象のテーブルのリスト
   * @return データベースのテーブル情報
   */
  List<TableEntity> selectTableList(List<String> schemaList, List<String> tableList);

  /**
   * データベースのカラム情報を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @param tableList テーブル定義出力対象のテーブルのリスト
   * @return データベースのカラム情報
   */
  List<ColumnEntity> selectColumnList(List<String> schemaList, List<String> tableList);

  /**
   * データベースのインデックス情報を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @param tableList テーブル定義出力対象のテーブルのリスト
   * @return データベースのインデックス情報
   */
  List<IndexEntity> selectIndexList(List<String> schemaList, List<String> tableList);

  /**
   * データベースの制約情報を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @param tableList テーブル定義出力対象のテーブルのリスト
   * @return データベースの制約情報
   */
  List<ConstraintEntity> selectConstraintList(List<String> schemaList, List<String> tableList);

  /**
   * データベースの外部キー情報を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @param tableList テーブル定義出力対象のテーブルのリスト
   * @return データベースの外部キー情報
   */
  List<ForeignKeyEntity> selectForeignKeyList(List<String> schemaList, List<String> tableList);

  /**
   * データベースのトリガー情報を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @param tableList テーブル定義出力対象のテーブルのリスト
   * @return データベースのトリガー情報
   */
  List<TriggerEntity> selectTriggerList(List<String> schemaList, List<String> tableList);

  /**
   * データベースの関数・プロシージャの一覧情報（定義本体を含まない軽量情報）を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @return データベースの関数・プロシージャの一覧情報
   */
  List<FunctionEntity> selectFunctionList(List<String> schemaList);

  /**
   * データベースの関数・プロシージャの定義本体を含む情報を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @return データベースの関数・プロシージャの定義情報
   */
  List<FunctionEntity> selectFunctionDefList(List<String> schemaList);

  /**
   * データベースのシーケンス情報を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @return データベースのシーケンス情報
   */
  List<SequenceEntity> selectSequenceList(List<String> schemaList);

  /**
   * データベースのユーザー定義型（ENUM等）情報を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @return データベースのユーザー定義型情報
   */
  List<TypeEntity> selectTypeList(List<String> schemaList);

  /**
   * DTOのListをEntityのListに変換する共通メソッド
   *
   * @param <D> DTOクラスの型
   * @param <E> Entityクラスの型
   * @param dtoList DTOのList
   * @param mapper DTOからEntityへの変換関数
   * @return EntityのList
   */
  default <D, E> List<E> makeEntityList(List<D> dtoList, Function<D, E> mapper) {
    return dtoList.stream().map(mapper).toList();
  }
}
