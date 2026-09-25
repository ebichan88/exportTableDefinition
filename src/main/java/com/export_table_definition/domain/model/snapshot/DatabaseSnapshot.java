package com.export_table_definition.domain.model.snapshot;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;

/**
 * スキーマのスナップショットのうち、DB全体の情報を表すrecordクラス<br>
 * 実行のたびに変わる生成日は、差分検知（{@code --check}）で常に差分として検出されてしまうため含めない
 *
 * @param formatVersion スナップショットの形式のバージョン（形式を互換性なく変更した場合に上げる）
 * @param name データベース名
 * @param dbms DBMS種別（PostgreSQL/Oracle）
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record DatabaseSnapshot(int formatVersion, String name, String dbms) {

  /** 現行のスナップショットの形式のバージョン */
  public static final int FORMAT_VERSION = 1;

  /**
   * データベースの基本情報からスナップショットを生成するメソッド
   *
   * @param baseInfo データベースの基本情報
   * @return DB全体の情報のスナップショット
   */
  public static DatabaseSnapshot of(BaseInfoEntity baseInfo) {
    return new DatabaseSnapshot(FORMAT_VERSION, baseInfo.dbName(), baseInfo.dbmsName());
  }
}
