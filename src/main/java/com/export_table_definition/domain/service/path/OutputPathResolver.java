package com.export_table_definition.domain.service.path;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.snapshot.SnapshotKind;
import java.nio.file.Path;

/**
 * テーブル定義および一覧出力用のパス生成戦略インタフェース <br>
 * 物理レイアウト（ディレクトリ構造・ファイル命名規則）を抽象化する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface OutputPathResolver {

  /**
   * 基本出力ディレクトリを解決する。 <br>
   * 設定された出力先パスが未指定・空白の場合は、デフォルトの出力先にフォールバックする
   *
   * @param outputPath 設定された出力先のパス（未指定可）
   * @return 基本出力ディレクトリのパス
   */
  Path resolveBaseOutputDir(String outputPath);

  /**
   * テーブル定義書の出力ディレクトリを返す。 <br>
   * 例: {base}/{DB名}/{スキーマ名}/{テーブル種別}/
   *
   * @param baseInfo 基本情報エンティティ
   * @param table テーブルエンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @return テーブル定義書の出力ディレクトリパス
   */
  Path resolveTableDefinitionDirectory(
      BaseInfoEntity baseInfo, TableEntity table, Path baseOutputDir);

  /**
   * テーブル定義書の出力ファイルパスを返す。 <br>
   * 例: {base}/{DB名}/{スキーマ名}/{テーブル種別}/{物理テーブル名}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param table テーブルエンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @return テーブル定義書の出力ファイルパス
   */
  Path resolveTableDefinitionFile(BaseInfoEntity baseInfo, TableEntity table, Path baseOutputDir);

  /**
   * テーブル一覧（単一ファイルモード）のパス。 <br>
   * 例: {base}/tableList_{DB名}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @return テーブル一覧ファイルのパス
   */
  Path resolveTableListFile(BaseInfoEntity baseInfo, Path baseOutputDir);

  /**
   * テーブル一覧（分割ページモード）のパス。 <br>
   * 例: {base}/tableList_{DB名}_{pageIndex}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param pageIndex ページインデックス（1始まり）
   * @return テーブル一覧ファイルのパス
   */
  Path resolveTableListFile(BaseInfoEntity baseInfo, Path baseOutputDir, int pageIndex);

  /**
   * オブジェクト一覧（トリガー/関数/シーケンス/型）のパス。 <br>
   * 例: {base}/{prefix}List_{DB名}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param prefix 一覧ファイル名の接頭辞（例: trigger, function, sequence, type）
   * @return オブジェクト一覧ファイルのパス
   */
  Path resolveObjectListFile(BaseInfoEntity baseInfo, Path baseOutputDir, String prefix);

  /**
   * オブジェクト一覧（分割ページモード）のパス。 <br>
   * 例: {base}/{prefix}List_{DB名}_{pageIndex}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param prefix 一覧ファイル名の接頭辞（例: erDiagram）
   * @param pageIndex ページインデックス（1始まり）
   * @return オブジェクト一覧ファイルのパス
   */
  Path resolveObjectListFile(
      BaseInfoEntity baseInfo, Path baseOutputDir, String prefix, int pageIndex);

  /**
   * スキーマ別ER図のパス。 <br>
   * 例: {base}/erDiagram_{DB名}_{スキーマ名}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param schemaName スキーマ名
   * @return スキーマ別ER図ファイルのパス
   */
  Path resolveErDiagramFile(BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName);

  /**
   * スキーマ別ER図の分割ページのパス。 <br>
   * 例: {base}/erDiagram_{DB名}_{スキーマ名}_{pageIndex}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param schemaName スキーマ名
   * @param pageIndex ページインデックス（1始まり）
   * @return スキーマ別ER図の分割ページファイルのパス
   */
  Path resolveErDiagramFile(
      BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, int pageIndex);

  /**
   * スキーマ別ER図をテーブルのまとまりごとに分割したページのパス。 <br>
   * 例: {base}/erDiagram_{DB名}_{スキーマ名}_group{groupNo}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param schemaName スキーマ名
   * @param groupNo グループ番号（1始まり）
   * @return グループ別ER図ファイルのパス
   */
  Path resolveErDiagramGroupFile(
      BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, int groupNo);

  /**
   * グループ別ER図の表をさらに分割したページのパス。 <br>
   * 例: {base}/erDiagram_{DB名}_{スキーマ名}_group{groupNo}_{pageIndex}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param schemaName スキーマ名
   * @param groupNo グループ番号（1始まり）
   * @param pageIndex ページインデックス（1始まり）
   * @return グループ別ER図の分割ページファイルのパス
   */
  Path resolveErDiagramGroupFile(
      BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, int groupNo, int pageIndex);

  /**
   * スキーマ配下オブジェクト（関数/シーケンス/型）の出力ディレクトリを返す。 <br>
   * 例: {base}/{DB名}/{スキーマ名}/{kind}/
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param schemaName スキーマ名
   * @param kind オブジェクト種別ディレクトリ名（例: function, sequence, type）
   * @return 出力ディレクトリパス
   */
  Path resolveSchemaObjectDirectory(
      BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, String kind);

  /**
   * スキーマ配下オブジェクト（関数/シーケンス/型）の出力ファイルパスを返す。 <br>
   * 例: {base}/{DB名}/{スキーマ名}/{kind}/{name}.md
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param schemaName スキーマ名
   * @param kind オブジェクト種別ディレクトリ名（例: function, sequence, type）
   * @param name ファイル名（拡張子を除く）
   * @return 出力ファイルパス
   */
  Path resolveSchemaObjectFile(
      BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, String kind, String name);

  /**
   * スキーマのスナップショットの出力ディレクトリ（スナップショット全体のルート）を返す。 <br>
   * Markdownのドキュメントとは混在させず、専用のディレクトリ配下にまとめる。 例: {base}/snapshot/
   *
   * @param baseOutputDir 基本出力ディレクトリ
   * @return スナップショットの出力ディレクトリパス
   */
  Path resolveSnapshotDirectory(Path baseOutputDir);

  /**
   * スナップショットのうち、DB全体の情報の出力ファイルパスを返す。 <br>
   * 例: {base}/snapshot/{DB名}/database.json
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @return DB全体の情報の出力ファイルパス
   */
  Path resolveSnapshotDatabaseFile(BaseInfoEntity baseInfo, Path baseOutputDir);

  /**
   * スナップショットのうち、スキーマ配下のオブジェクト（テーブル/関数/シーケンス/型）の出力ファイルパスを返す。 <br>
   * 種別ごとに1ファイル（1行1オブジェクトのJSON Lines）とする。 例: {base}/snapshot/{DB名}/{スキーマ名}/tables.jsonl
   *
   * @param baseInfo 基本情報エンティティ
   * @param baseOutputDir 基本出力ディレクトリ
   * @param schemaName スキーマ名
   * @param kind オブジェクトの種別
   * @return 出力ファイルパス
   */
  Path resolveSnapshotFile(
      BaseInfoEntity baseInfo, Path baseOutputDir, String schemaName, SnapshotKind kind);
}
