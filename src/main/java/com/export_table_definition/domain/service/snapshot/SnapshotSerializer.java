package com.export_table_definition.domain.service.snapshot;

import java.util.Map;

/**
 * スキーマのスナップショット（{@code domain.model.snapshot}配下のrecord）とJSON文字列の相互変換を行うインタフェース<br>
 * JSONライブラリへの依存をドメイン層へ持ち込まないため、変換処理はインフラ層で実装する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public interface SnapshotSerializer {

  /**
   * スナップショットのrecordを、改行を含まない1行のJSON文字列へ変換する<br>
   * 値が無い項目（null・空文字・空リスト）は出力しない。 同じ内容からは常に同じ文字列を生成すること（差分検知で比較するため）
   *
   * @param snapshot スナップショットのrecord
   * @return 1行のJSON文字列（末尾の改行は含まない）
   */
  String serialize(Object snapshot);

  /**
   * {@link #serialize}で出力した1行のJSON文字列を、項目名をキーとするマップへ変換する<br>
   * スナップショット同士の比較で、1行がどのオブジェクトのものかを識別するために用いる
   *
   * @param json 1行のJSON文字列
   * @return 項目名をキー、値を値とするマップ
   */
  Map<String, Object> deserialize(String json);
}
