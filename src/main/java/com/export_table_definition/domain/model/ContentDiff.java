package com.export_table_definition.domain.model;

import java.util.List;

/**
 * 内容が一致しないオブジェクト（またはファイル）1件分の差分を表すrecord
 *
 * @param target 差分の対象を表す表示用の文字列（例: {@code table sample.employee}）
 * @param unifiedDiff 差分の内容（unified diff形式の行リスト）。生成側・コミット側それぞれの内容を差分表示用に整形した上で
 *     比較しているため、実際のファイルの行番号とは対応しない
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ContentDiff(String target, List<String> unifiedDiff) {}
