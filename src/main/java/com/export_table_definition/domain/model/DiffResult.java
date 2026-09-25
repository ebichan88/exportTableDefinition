package com.export_table_definition.domain.model;

import java.nio.file.Path;
import java.util.List;

/**
 * DBから生成したドキュメントと、既にコミット済みのドキュメントの差分結果を表すrecord<br>
 * いずれのパスも比較対象ディレクトリからの相対パス
 *
 * @param onlyInGenerated  生成側にのみ存在するファイル（コミット漏れの可能性）
 * @param onlyInCommitted  コミット側にのみ存在するファイル（削除されたテーブル等の残骸ファイルの可能性）
 * @param contentDiffer    両方に存在するが内容が一致しないファイル
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record DiffResult(List<Path> onlyInGenerated, List<Path> onlyInCommitted, List<Path> contentDiffer) {

    /**
     * 差分の有無を返すメソッド
     *
     * @return いずれかの区分に1件でもファイルが存在する場合true
     */
    public boolean hasDifference() {
        return !onlyInGenerated.isEmpty() || !onlyInCommitted.isEmpty() || !contentDiffer.isEmpty();
    }
}
