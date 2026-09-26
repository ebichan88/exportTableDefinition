package com.export_table_definition.domain.service.export;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.schemaobject.FunctionEntity;
import com.export_table_definition.domain.model.target.ExportTargets;
import com.export_table_definition.domain.model.target.TableDefinitionContent;
import java.util.List;

/**
 * DBから取得したスキーマ情報を、1つの出力形式（Markdownのドキュメント／スナップショット）で書き出すインタフェース<br>
 * 取得処理（一括取得・スキーマ単位・チャンク単位）は出力形式に依らず共通のため、ユースケースは取得した順に各メソッドを呼び出し、
 * どの情報をどう書き出すかは実装に委ねる。出力先のディレクトリは実装の生成時に決まる
 */
public interface ExportSink {

  /**
   * 一括取得した情報のみで出力できるもの（一覧・ER図・シーケンス/型の個別定義等）を書き出すメソッド<br>
   * テーブルの詳細情報・関数の定義本体を取得する前に1度だけ呼ばれる
   *
   * @param targets 一括取得した出力対象の情報
   */
  void writeOverview(ExportTargets targets);

  /**
   * スキーマ単位で取得した関数・プロシージャ（定義本体を含む）を書き出すメソッド
   *
   * @param schemaName スキーマ名
   * @param functions 当該スキーマの関数・プロシージャ情報（定義本体を含む）のリスト
   * @param baseInfo データベースの基本情報
   */
  void writeFunctionDefinitions(
      String schemaName, List<FunctionEntity> functions, BaseInfoEntity baseInfo);

  /**
   * スキーマ内のテーブル定義を書き出し始める前に呼ばれるメソッド<br>
   * 以降、当該スキーマのテーブルについて{@link #writeTableDefinition}がチャンク単位で繰り返し呼ばれる
   *
   * @param schemaName スキーマ名
   * @param baseInfo データベースの基本情報
   */
  default void beginSchemaTables(String schemaName, BaseInfoEntity baseInfo) {}

  /**
   * 1テーブル分の定義を書き出すメソッド
   *
   * @param content 1テーブル分の定義書出力に必要な情報
   */
  void writeTableDefinition(TableDefinitionContent content);
}
