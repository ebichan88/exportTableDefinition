package com.export_table_definition.domain.service.path;

import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.snapshot.SnapshotKind;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import java.nio.file.Path;
import java.util.Optional;

/**
 * テーブル定義および一覧出力用のパス生成戦略インタフェース <br>
 * 物理レイアウト（ディレクトリ構造・ファイル命名規則）を抽象化する。Markdownドキュメントのファイル名・配置の規則自体は、 ドキュメント間の相対リンクと共有するため{@link
 * DocumentLocations}に定める
 */
public interface OutputPathResolver {

  /**
   * 基本出力ディレクトリを解決する。<br>
   * 設定された出力先パスが未指定・空白の場合は、デフォルトの出力先にフォールバックする
   */
  Path resolveBaseOutputDir(String outputPath);

  /**
   * 出力先ベースディレクトリを、書き込み前にディレクトリごと削除してよいか判定する。<br>
   * ルート・ホームディレクトリ・カレントディレクトリ自体など、設定誤りで削除すると被害が甚大なディレクトリの場合は削除を認めない
   */
  boolean isRemovableOutputDir(Path baseOutputDir);

  /**
   * テーブル定義書の出力ディレクトリを返す。<br>
   * 例: {base}/{DB名}/{スキーマ名}/{テーブル種別}/
   */
  Path resolveTableDefinitionDirectory(OutputRoot root, TableEntity table);

  /**
   * テーブル定義書の出力ファイルパスを返す。<br>
   * 例: {base}/{DB名}/{スキーマ名}/{テーブル種別}/{物理テーブル名}.md
   */
  Path resolveTableDefinitionFile(OutputRoot root, TableEntity table);

  /**
   * 一覧（テーブル／ER図／関数・プロシージャ／シーケンス／ユーザー定義型／トリガー／観点）のパス。<br>
   * 例: {base}/{接頭辞}List_{DB名}.md
   */
  Path resolveListFile(OutputRoot root, ListDocumentType type);

  /**
   * スキーマ別ER図のパス。<br>
   * 例: {base}/erDiagram_{DB名}_{スキーマ名}.md
   */
  Path resolveErDiagramFile(OutputRoot root, String schemaName);

  /**
   * スキーマ別ER図をテーブルのまとまりごとに分割したページのパス。<br>
   * 例: {base}/erDiagram_{DB名}_{スキーマ名}_group{groupNo}.md
   *
   * @param groupNo グループ番号（1始まり）
   */
  Path resolveErDiagramGroupFile(OutputRoot root, String schemaName, int groupNo);

  /**
   * 観点ページのパス。<br>
   * 例: {base}/viewpoint_{DB名}_{観点の識別子}.md
   */
  Path resolveViewpointFile(OutputRoot root, Viewpoint viewpoint);

  /**
   * 行数の多い表を分割した場合の、分割ページのパス。<br>
   * 本体ページと同じディレクトリに置く。例: {base}/tableList_{DB名}.md の2ページ目は {base}/tableList_{DB名}_2.md
   *
   * @param pageIndex ページインデックス（1始まり）
   */
  Path resolvePageFile(Path file, int pageIndex);

  /**
   * スキーマ配下オブジェクト（関数/シーケンス/型）の出力ディレクトリを返す。<br>
   * 例: {base}/{DB名}/{スキーマ名}/{kind}/
   *
   * @param kind オブジェクトの区分（{@link ListDocumentType#FUNCTION}／{@link
   *     ListDocumentType#SEQUENCE}／{@link ListDocumentType#TYPE}）
   */
  Path resolveSchemaObjectDirectory(OutputRoot root, String schemaName, ListDocumentType kind);

  /**
   * スキーマ配下オブジェクト（関数/シーケンス/型）の出力ファイルパスを返す。<br>
   * 例: {base}/{DB名}/{スキーマ名}/{kind}/{name}.md
   *
   * @param kind オブジェクトの区分（{@link ListDocumentType#FUNCTION}／{@link
   *     ListDocumentType#SEQUENCE}／{@link ListDocumentType#TYPE}）
   * @param name ファイル名（拡張子を除く）
   */
  Path resolveSchemaObjectFile(
      OutputRoot root, String schemaName, ListDocumentType kind, String name);

  /**
   * スキーマのスナップショットの出力ディレクトリ（スナップショット全体のルート）を返す。<br>
   * Markdownのドキュメントとは混在させず、専用のディレクトリ配下にまとめる。例: {base}/snapshot/
   */
  Path resolveSnapshotDirectory(Path baseOutputDir);

  /**
   * スナップショットのうち、DB全体の情報の出力ファイルパスを返す。<br>
   * 例: {base}/snapshot/{DB名}/database.json
   */
  Path resolveSnapshotDatabaseFile(OutputRoot root);

  /**
   * スナップショットのうち、スキーマ配下のオブジェクト（テーブル/関数/シーケンス/型）の出力ファイルパスを返す。<br>
   * 種別ごとに1ファイル（1行1オブジェクトのJSON Lines）とする。例: {base}/snapshot/{DB名}/{スキーマ名}/tables.jsonl
   */
  Path resolveSnapshotFile(OutputRoot root, String schemaName, SnapshotKind kind);

  /**
   * スナップショットのファイルパスから、出力しているオブジェクトの種別を判定する。<br>
   * {@link #resolveSnapshotFile}の逆変換。スナップショット同士の比較で、ファイルごとの比較方法を決めるために用いる
   *
   * @return スキーマ配下のオブジェクトのファイルでない場合（{@code database.json}等）は空
   */
  Optional<SnapshotKind> resolveSnapshotKind(Path snapshotFile);
}
