package com.export_table_definition.application;

import com.export_table_definition.domain.model.snapshot.DiffResult;

/** DB vs ドキュメントの差分検知（{@code --check}モード）のユースケースを扱うインターフェース */
public interface CheckDocumentDiffUsecase {

  /**
   * DBの現状から生成したスキーマのスナップショットと、{@code outputPath}配下の{@code snapshot/}に既にコミット済みの
   * スナップショットを比較し、差分（＝ドキュメントの再生成・コミット忘れ）を検知するメソッド<br>
   * DBからの取得は{@link ExportTableDefinitionUsecase#exportTableDefinition}と共通で、スナップショットのみを生成して
   * 一時ディレクトリへ出力し、オブジェクト（テーブル・関数等）単位で比較する。Markdownの描画・ER図の生成は行わない
   *
   * @param request DB vs ドキュメントの差分検知の入力
   * @return 比較結果
   */
  public DiffResult checkDocumentDiff(CheckDiffRequest request);
}
