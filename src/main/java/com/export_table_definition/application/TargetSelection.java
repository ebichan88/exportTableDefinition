package com.export_table_definition.application;

import com.export_table_definition.domain.model.target.OutputObjectType;
import com.export_table_definition.domain.model.target.TableTargetScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 出力対象の絞り込み条件をまとめたrecord<br>
 * {@link ExportRequest}・{@link CheckDiffRequest}の双方が持つスキーマ・テーブル・出力対象オブジェクト種別・
 * サイドカーYAMLのパスの4条件をまとめ、エントリーポイント→コントローラー→ユースケースの3層を 分解・再構築せずそのまま通過させる。<br>
 * 設定値は生の文字列のままユースケースへ渡さず、{@link #of}で入口（エントリーポイント）において型へ変換する。
 * 未知の出力対象オブジェクト種別などの設定誤りは、DBへの問い合わせや出力先の削除（{@code --rm-dist}）より前に検知される
 *
 * @param targetScope テーブル定義出力対象の範囲（スキーマ名リスト＋テーブル名パターン）
 * @param outputObjectTypes 出力対象とするPostgreSQL固有オブジェクト種別の集合（未指定の場合は全種別）
 * @param sidecarPath サイドカーYAML（手動付帯情報・論理リレーション・観点）のパス（プロパティ{@code annotationPath}の値）。
 *     空・未指定の場合はマージを行わない
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TargetSelection(
    TableTargetScope targetScope, Set<OutputObjectType> outputObjectTypes, String sidecarPath) {

  /**
   * 設定ファイルから読み込んだ値から出力対象の絞り込み条件を生成する静的ファクトリメソッド<br>
   * 各リストの要素は前後の空白を除去して解釈する
   *
   * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
   * @param targetTableList テーブル定義出力対象のテーブルのリスト（ワイルドカード・除外・スキーマ修飾を指定可）
   * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名（{@link OutputObjectType#getName()}）のリスト。
   *     空の場合は全種別を出力対象とする
   * @param sidecarPath サイドカーYAMLのパス（未指定可）
   * @return 出力対象の絞り込み条件
   * @throws IllegalArgumentException テーブル名パターンの書き誤りや、未知の出力対象オブジェクト種別名が含まれる場合。
   *     両方に誤りがある場合は、1件ずつではなくまとめて（1行に1件）示す
   */
  public static TargetSelection of(
      List<String> targetSchemaList,
      List<String> targetTableList,
      List<String> outputObjectList,
      String sidecarPath) {
    final List<String> errors = new ArrayList<>();
    final TableTargetScope targetScope =
        parse(() -> TableTargetScope.of(targetSchemaList, targetTableList), errors);
    final Set<OutputObjectType> outputObjectTypes =
        parse(() -> OutputObjectType.parse(outputObjectList), errors);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join(System.lineSeparator(), errors));
    }
    return new TargetSelection(targetScope, outputObjectTypes, sidecarPath);
  }

  /**
   * 設定値を解釈するメソッド<br>
   * 解釈できない場合は、他の設定値の誤りとまとめて報告できるよう、例外を投げずに誤りを記録する
   *
   * @param <T> 解釈結果の型
   * @param parser 設定値を解釈する処理（解釈できない場合は{@link IllegalArgumentException}を投げる）
   * @param errors 誤りの記録先
   * @return 解釈結果。解釈できない場合はnull（誤りを記録した呼び出し元が例外を投げるため、外へは渡らない）
   */
  private static <T> T parse(Supplier<T> parser, List<String> errors) {
    try {
      return parser.get();
    } catch (IllegalArgumentException e) {
      errors.add(e.getMessage());
      return null;
    }
  }
}
