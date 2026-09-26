package com.export_table_definition.application;

import com.export_table_definition.domain.model.type.OutputObjectType;
import com.export_table_definition.domain.model.value.TableTargetScope;
import java.util.List;
import java.util.Set;

/**
 * 出力対象の絞り込み条件をまとめたrecord<br>
 * {@link ExportRequest}・{@link CheckDiffRequest}の双方が持つスキーマ・テーブル・出力対象オブジェクト種別・
 * 手動付帯情報パスの4条件をまとめ、エントリーポイント→コントローラー→ユースケースの3層を 分解・再構築せずそのまま通過させる。<br>
 * 設定値は生の文字列のままユースケースへ渡さず、{@link #of}で入口（エントリーポイント）において型へ変換する。
 * 未知の出力対象オブジェクト種別などの設定誤りは、DBへの問い合わせや出力先の削除（{@code --rm-dist}）より前に検知される
 *
 * @param targetScope テーブル定義出力対象の範囲（スキーマ名リスト＋テーブル名パターン）
 * @param outputObjectTypes 出力対象とするPostgreSQL固有オブジェクト種別の集合（未指定の場合は全種別）
 * @param annotationPath 手動付帯情報（テーブル説明・テーブル備考・カラム備考）を記述したサイドカーYAMLのパス。 空・未指定の場合はマージを行わない
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record TargetSelection(
    TableTargetScope targetScope, Set<OutputObjectType> outputObjectTypes, String annotationPath) {

  /**
   * 設定ファイルから読み込んだ値から出力対象の絞り込み条件を生成する静的ファクトリメソッド<br>
   * 各リストの要素は前後の空白を除去して解釈する
   *
   * @param targetSchemaList テーブル定義出力対象のスキーマのリスト
   * @param targetTableList テーブル定義出力対象のテーブルのリスト（ワイルドカード・除外・スキーマ修飾を指定可）
   * @param outputObjectList 出力対象とするPostgreSQL固有オブジェクト種別名（{@link OutputObjectType#getName()}）のリスト。
   *     空の場合は全種別を出力対象とする
   * @param annotationPath 手動付帯情報を記述したサイドカーYAMLのパス（未指定可）
   * @return 出力対象の絞り込み条件
   * @throws IllegalArgumentException 未知の出力対象オブジェクト種別名が含まれる場合
   */
  public static TargetSelection of(
      List<String> targetSchemaList,
      List<String> targetTableList,
      List<String> outputObjectList,
      String annotationPath) {
    return new TargetSelection(
        TableTargetScope.of(targetSchemaList, targetTableList),
        OutputObjectType.parse(outputObjectList),
        annotationPath);
  }
}
