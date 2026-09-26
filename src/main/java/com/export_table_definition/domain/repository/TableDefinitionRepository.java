package com.export_table_definition.domain.repository;

import com.export_table_definition.domain.model.TableDetail;
import com.export_table_definition.domain.model.entity.DatabaseEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.FunctionEntity;
import com.export_table_definition.domain.model.entity.SequenceEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.entity.TypeEntity;
import java.util.List;

/**
 * テーブル定義出力に関するリポジトリインターフェース
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface TableDefinitionRepository {

  /**
   * データベースの情報（データベース名・DBMS種別）を取得するメソッド<br>
   * ドキュメントの生成日はDBではなく実行時に決まるため含まない
   *
   * @return データベースの情報
   */
  DatabaseEntity selectDatabase();

  /**
   * データベースのテーブル情報を取得するメソッド<br>
   * テーブル単位の絞り込みは呼び出し側（{@link
   * com.export_table_definition.domain.model.value.TableTargetScope}）がJava側で行うため、 スキーマ単位でのみ絞り込む
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @return データベースのテーブル情報
   */
  List<TableEntity> selectTableList(List<String> schemaList);

  /**
   * 指定したテーブルの詳細情報（カラム・インデックス・制約）を取得するメソッド<br>
   * テーブル数に比例して重くなる情報のため、呼び出し側はスキーマ・チャンク単位でテーブルを渡す （同一スキーマのテーブルを渡すことを想定する）
   *
   * @param tables 詳細情報を取得するテーブルのリスト
   * @return テーブルごとの詳細情報のリスト（{@code tables}と同じ順）
   */
  List<TableDetail> selectTableDetails(List<TableEntity> tables);

  /**
   * データベースの外部キー情報を取得するメソッド<br>
   * テーブル単位の絞り込みは行わず、スキーマ全体を取得する（{@link #selectTableList}を参照）
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @return データベースの外部キー情報
   */
  List<ForeignKeyEntity> selectForeignKeyList(List<String> schemaList);

  /**
   * データベースのトリガー情報を取得するメソッド<br>
   * テーブル単位の絞り込みは行わず、スキーマ全体を取得する（{@link #selectTableList}を参照）
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @return データベースのトリガー情報
   */
  List<TriggerEntity> selectTriggerList(List<String> schemaList);

  /**
   * データベースの関数・プロシージャの一覧情報（定義本体を含まない軽量情報）を取得するメソッド
   *
   * @param schemaList テーブル定義出力対象のスキーマのリスト
   * @return データベースの関数・プロシージャの一覧情報
   */
  List<FunctionEntity> selectFunctionList(List<String> schemaList);

  /**
   * データベースの関数・プロシージャの定義本体を含む情報を取得するメソッド<br>
   * {@link #selectFunctionList}と同じ項目（種別・引数・戻り値・言語）に加え、定義本体を含む
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
}
