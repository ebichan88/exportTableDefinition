package com.dbxray.domain.model.insight;

import com.dbxray.domain.model.table.TableEntity;
import com.dbxray.domain.model.viewpoint.ViewpointContent;
import java.util.List;

/**
 * 観点（業務ドメイン別にテーブルをまとめる切り口）1件の参考情報<br>
 * {@link ViewpointContent}（ER図描画用の関連を含む）から、AIへ渡すのに必要な所属テーブルの一覧だけを取り出す
 *
 * @param tables 所属テーブル（出力対象のテーブルの並び順）
 */
public record ViewpointInsight(String id, String name, String description, List<Member> tables) {

  /** 所属テーブル1件（スキーマ名＋物理テーブル名） */
  public record Member(String schema, String name) {

    static Member of(TableEntity table) {
      return new Member(table.schemaName(), table.physicalTableName());
    }
  }

  /** 1観点分の出力内容から生成する */
  static ViewpointInsight of(ViewpointContent content) {
    return new ViewpointInsight(
        content.viewpoint().id(),
        content.viewpoint().name(),
        content.viewpoint().description(),
        content.tables().stream().map(Member::of).toList());
  }
}
