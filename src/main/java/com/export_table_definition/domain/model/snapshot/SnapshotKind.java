package com.export_table_definition.domain.model.snapshot;

/**
 * スナップショットのうち、スキーマ単位のJSON Linesファイルに出力するオブジェクトの種別を表す列挙型<br>
 * 種別ごとに1ファイルとし、1行に1オブジェクトを出力する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public enum SnapshotKind {
  /** テーブル（view・materialized viewを含む） */
  TABLE("tables", "table"),
  /** 関数・プロシージャ */
  FUNCTION("functions", "function"),
  /** シーケンス */
  SEQUENCE("sequences", "sequence"),
  /** ユーザー定義型 */
  TYPE("types", "type");

  /** 出力ファイル名（拡張子を除く） */
  private final String fileName;

  /** 差分の報告等に用いる種別名 */
  private final String label;

  /**
   * コンストラクタ
   *
   * @param fileName 出力ファイル名（拡張子を除く）
   * @param label 差分の報告等に用いる種別名
   */
  SnapshotKind(String fileName, String label) {
    this.fileName = fileName;
    this.label = label;
  }

  /**
   * 出力ファイル名（拡張子を除く）を返却するメソッド
   *
   * @return 出力ファイル名（拡張子を除く）
   */
  public String getFileName() {
    return fileName;
  }

  /**
   * 差分の報告等に用いる種別名を返却するメソッド
   *
   * @return 種別名
   */
  public String getLabel() {
    return label;
  }
}
