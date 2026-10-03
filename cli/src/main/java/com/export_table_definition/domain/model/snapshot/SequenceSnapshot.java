package com.export_table_definition.domain.model.snapshot;

import static com.export_table_definition.domain.model.snapshot.SnapshotValues.text;

import com.export_table_definition.domain.model.schemaobject.SequenceEntity;

/**
 * スキーマのスナップショットのうち、1シーケンス分の情報を表すrecordクラス<br>
 * 数値項目はDBの型（bigint等）の範囲を損なわないよう、カタログから取得した文字列のまま保持する
 *
 * @param cycle 最大値（最小値）到達時に循環するか
 * @param ownedBy 所有カラム（テーブル.カラム形式）
 */
public record SequenceSnapshot(
    String schema,
    String name,
    String incrementBy,
    String minValue,
    String maxValue,
    String cacheSize,
    String startValue,
    boolean cycle,
    String ownedBy) {

  /**
   * シーケンス情報からスナップショットを生成するメソッド
   *
   * @return 1シーケンス分のスナップショット
   */
  public static SequenceSnapshot of(SequenceEntity sequence) {
    return new SequenceSnapshot(
        sequence.schemaName(),
        sequence.sequenceName(),
        text(sequence.incrementBy()),
        text(sequence.minValue()),
        text(sequence.maxValue()),
        text(sequence.cacheSize()),
        text(sequence.startValue()),
        sequence.cycle(),
        text(sequence.ownedBy()));
  }
}
