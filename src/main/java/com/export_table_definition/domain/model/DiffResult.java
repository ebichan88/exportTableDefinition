package com.export_table_definition.domain.model;

import java.util.List;

/**
 * DBから生成したドキュメント（またはスナップショット）と、既にコミット済みのものとの差分結果を表すrecord<br>
 * 各要素は差分の対象を表す表示用の文字列で、Markdownのドキュメント同士の比較では比較対象ディレクトリからの相対パス、 スナップショット同士の比較ではオブジェクト（例: {@code
 * table sample.employee}）となる
 *
 * @param onlyInGenerated 生成側にのみ存在するもの（コミット漏れの可能性）
 * @param onlyInCommitted コミット側にのみ存在するもの（削除されたテーブル等の残骸の可能性）
 * @param contentDiffer 両方に存在するが内容が一致しないもの
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record DiffResult(
    List<String> onlyInGenerated, List<String> onlyInCommitted, List<String> contentDiffer) {

  /**
   * 差分の有無を返すメソッド
   *
   * @return いずれかの区分に1件でも差分が存在する場合true
   */
  public boolean hasDifference() {
    return !onlyInGenerated.isEmpty() || !onlyInCommitted.isEmpty() || !contentDiffer.isEmpty();
  }
}
