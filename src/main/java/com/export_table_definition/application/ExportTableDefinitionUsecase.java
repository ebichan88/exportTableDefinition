package com.export_table_definition.application;

import com.export_table_definition.domain.model.DiffResult;
import java.util.List;

/**
 * テーブル定義出力のユースケースを扱うインターフェース
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface ExportTableDefinitionUsecase {

  /**
   * テーブル定義出力のユースケースを扱うメソッド
   *
   * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
   * @param targetTableList テーブル定義出力対象のテーブルのリスト
   * @param outputPath テーブル定義出力の出力先のパス
   * @param chunkSize 詳細情報（カラム・インデックス・制約・外部キー）をまとめて取得するテーブル数の上限。 0以下の場合はスキーマ単位で分割せず取得する
   * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限。超過した場合はER図の代わりに 外部キーの一覧表を出力する。0以下の場合は上限なし
   * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名（{@link
   *     com.export_table_definition.domain.model.type.OutputObjectType#getName()}）のリスト。
   *     空の場合は全種別を出力対象とする
   * @param annotationPath 手動付帯情報（テーブル説明・テーブル備考・カラム備考）を記述したサイドカーYAMLのパス。 空・未指定の場合はマージを行わない
   * @param outputSnapshot trueの場合、Markdownに加えてスキーマのスナップショット（JSON Lines）を {@code
   *     outputPath}配下の{@code snapshot/}へ出力する
   * @param rmDist trueの場合、書き込みを開始する前に{@code outputPath}のベースディレクトリを 再帰的に削除する（{@code
   *     --rm-dist}）。削除されたテーブル等の残骸ファイルを残さずに再生成したい場合に指定する
   */
  public void exportTableDefinition(
      List<String> targetSchemaList,
      List<String> targetTableList,
      String outputPath,
      int chunkSize,
      int erDiagramMaxNodes,
      List<String> outputObjectList,
      String annotationPath,
      boolean outputSnapshot,
      boolean rmDist);

  /**
   * DBの現状から生成したドキュメントと、{@code outputPath}配下に既にコミット済みのドキュメントを比較し、 差分（＝ドキュメントの再生成・コミット忘れ）を検知するメソッド
   * <br>
   * DBからの取得は{@link #exportTableDefinition}と共通で、生成結果を一時ディレクトリへ出力して比較する。
   *
   * <ul>
   *   <li>{@code outputSnapshot}がtrueの場合: スキーマのスナップショットのみを生成し、 {@code
   *       outputPath}配下のスナップショットとオブジェクト（テーブル・関数等）単位で比較する。 Markdownの描画・ER図の生成は行わない
   *   <li>{@code outputSnapshot}がfalseの場合: Markdownのドキュメントを生成し、{@code outputPath}配下の既存ファイルと
   *       ファイル単位で比較する
   * </ul>
   *
   * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
   * @param targetTableList テーブル定義出力対象のテーブルのリスト
   * @param outputPath 比較対象となる、既にコミット済みのドキュメントが配置されたパス
   * @param chunkSize 詳細情報をまとめて取得するテーブル数の上限。0以下の場合はスキーマ単位で分割せず取得する
   * @param erDiagramMaxNodes スキーマ別ER図1枚に描画するノード数の上限。0以下の場合は上限なし
   * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名のリスト。空の場合は全種別を出力対象とする
   * @param annotationPath 手動付帯情報を記述したサイドカーYAMLのパス。空・未指定の場合はマージを行わない
   * @param outputSnapshot スキーマのスナップショットを出力する設定か（trueの場合はスナップショット同士を比較する）
   * @return 比較結果
   */
  public DiffResult checkDocumentDiff(
      List<String> targetSchemaList,
      List<String> targetTableList,
      String outputPath,
      int chunkSize,
      int erDiagramMaxNodes,
      List<String> outputObjectList,
      String annotationPath,
      boolean outputSnapshot);
}
