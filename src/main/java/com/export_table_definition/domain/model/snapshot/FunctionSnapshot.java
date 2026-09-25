package com.export_table_definition.domain.model.snapshot;

import static com.export_table_definition.domain.model.snapshot.SnapshotValues.text;

import com.export_table_definition.domain.model.entity.FunctionEntity;

/**
 * スキーマのスナップショットのうち、1関数・プロシージャ分の情報を表すrecordクラス<br>
 * 同名の関数（オーバーロード）は引数で区別する。個別定義ファイル名（連番付き）は Markdownの出力都合で付与しているものなので保持しない
 *
 * @param schema スキーマ名
 * @param name 関数・プロシージャ名
 * @param kind 種別（FUNCTION/PROCEDURE）
 * @param arguments 引数
 * @param result 戻り値の型（プロシージャの場合はnull）
 * @param language 実装言語
 * @param definition 定義本体
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record FunctionSnapshot(
    String schema,
    String name,
    String kind,
    String arguments,
    String result,
    String language,
    String definition) {

  /**
   * 関数・プロシージャ情報（定義本体を含む）からスナップショットを生成するメソッド
   *
   * @param function 関数・プロシージャ情報（定義本体を含む）
   * @return 1関数・プロシージャ分のスナップショット
   */
  public static FunctionSnapshot of(FunctionEntity function) {
    return new FunctionSnapshot(
        function.schemaName(),
        function.functionName(),
        text(function.functionKind()),
        text(function.functionArguments()),
        text(function.functionResult()),
        text(function.languageName()),
        text(function.definition()));
  }
}
