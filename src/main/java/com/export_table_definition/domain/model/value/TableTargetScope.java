package com.export_table_definition.domain.model.value;

import com.export_table_definition.domain.model.entity.TableEntity;
import java.util.List;

/**
 * テーブル定義出力対象の範囲（スキーマ名リスト＋テーブル名パターン）を表す値オブジェクト<br>
 * 実行設定（{@code schema=}・{@code table=}）から1回だけ生成し、以降は{@link #matches(TableEntity)}で 各テーブルを判定する。{@link
 * TableTargetFilter}はテーブル名パターンのみを扱うため、スキーマ名リストとの 組み合わせ判定（どちらか一方のみ指定・両方指定・両方未指定）はこのクラスが担う
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class TableTargetScope {

  private final List<String> targetSchemaList;
  private final TableTargetFilter tableFilter;

  private TableTargetScope(List<String> targetSchemaList, TableTargetFilter tableFilter) {
    this.targetSchemaList = targetSchemaList;
    this.tableFilter = tableFilter;
  }

  /**
   * テーブル定義出力対象のスキーマ・テーブルのリストから{@link TableTargetScope}を生成する静的ファクトリメソッド
   *
   * @param targetSchemaList テーブル定義出力対象のスキーマのリスト（未指定の場合は空リストまたはnull）
   * @param targetTableList テーブル定義出力対象のテーブルのリスト（ワイルドカード・除外・スキーマ修飾を指定可。 未指定の場合は空リストまたはnull）
   * @return 生成したTableTargetScope
   */
  public static TableTargetScope of(List<String> targetSchemaList, List<String> targetTableList) {
    return new TableTargetScope(
        targetSchemaList == null ? List.of() : List.copyOf(targetSchemaList),
        TableTargetFilter.of(targetTableList));
  }

  /**
   * 出力対象がスキーマ・テーブルのいずれかで絞り込まれているか判定するメソッド<br>
   * 絞り込みによる除外なのか、DBとサイドカーの乖離（リネーム・削除）による除外なのかを 呼び出し側が区別するために用いる（絞り込み時は警告ログを抑制する等）
   *
   * @return スキーマ・テーブルのいずれかが1件以上指定されている場合はtrue
   */
  public boolean isFiltered() {
    return !targetSchemaList.isEmpty() || !tableFilter.isEmpty();
  }

  /**
   * 指定されたテーブルがこの出力対象の範囲に含まれるか判定するメソッド<br>
   *
   * <ul>
   *   <li>スキーマ名リスト・テーブル名リストの両方が空の場合、常にtrueを返す
   *   <li>スキーマ名リストのみ指定されている場合、スキーマ名が一致すればtrueを返す
   *   <li>テーブル名リストのみ指定されている場合、テーブル名パターンが一致すればtrueを返す
   *   <li>両方指定されている場合、スキーマ名・テーブル名パターンの両方が一致した場合のみtrueを返す
   * </ul>
   *
   * テーブル名リストはワイルドカード（{@code *}）・除外（先頭に{@code !}）・スキーマ修飾（{@code スキーマ名.テーブル名}）に 対応する。詳細は{@link
   * TableTargetFilter}を参照
   *
   * @param table 判定対象のテーブル
   * @return 出力対象の範囲に含まれる場合はtrue
   */
  public boolean matches(TableEntity table) {
    final boolean hasSchemaList = !targetSchemaList.isEmpty();
    final boolean hasTableList = !tableFilter.isEmpty();
    if (!hasSchemaList && !hasTableList) {
      return true;
    }
    if (!hasSchemaList) {
      return tableFilter.matches(table.schemaName(), table.physicalTableName());
    }
    if (!hasTableList) {
      return targetSchemaList.contains(table.schemaName());
    }
    return targetSchemaList.contains(table.schemaName())
        && tableFilter.matches(table.schemaName(), table.physicalTableName());
  }
}
