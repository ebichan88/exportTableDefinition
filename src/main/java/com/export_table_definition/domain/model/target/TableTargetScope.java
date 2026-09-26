package com.export_table_definition.domain.model.target;

import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableTargetFilter;
import java.util.List;
import java.util.Objects;

/**
 * テーブル定義出力対象の範囲（スキーマ名リスト＋テーブル名パターン）を表す値オブジェクト<br>
 * 実行設定（{@code schema=}・{@code table=}）から1回だけ生成し、以降は{@link #matches(TableEntity)}で 各テーブルを判定する。{@link
 * TableTargetFilter}はテーブル名パターンのみを扱うため、スキーマ名リストとの 組み合わせ判定（どちらか一方のみ指定・両方指定・両方未指定）はこのクラスが担う
 */
public final class TableTargetScope {

  private final List<String> targetSchemaList;
  private final TableTargetFilter tableFilter;

  private TableTargetScope(List<String> targetSchemaList, TableTargetFilter tableFilter) {
    this.targetSchemaList = targetSchemaList;
    this.tableFilter = tableFilter;
  }

  /**
   * テーブル定義出力対象のスキーマ・テーブルのリストから{@link TableTargetScope}を生成する静的ファクトリメソッド<br>
   * スキーマ名は前後の空白を除去し、空要素を除く
   *
   * @param targetSchemaList テーブル定義出力対象のスキーマのリスト（未指定の場合は空リストまたはnull）
   * @param targetTableList テーブル定義出力対象のテーブルのリスト（ワイルドカード・除外・スキーマ修飾を指定可。 未指定の場合は空リストまたはnull）
   * @return 生成したTableTargetScope
   * @throws IllegalArgumentException テーブル名またはスキーマ名の部分が空のテーブル名パターンが含まれる場合（{@link
   *     TableTargetFilter#of}）
   */
  public static TableTargetScope of(List<String> targetSchemaList, List<String> targetTableList) {
    return new TableTargetScope(
        targetSchemaList == null
            ? List.of()
            : targetSchemaList.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(schemaName -> !schemaName.isEmpty())
                .toList(),
        TableTargetFilter.of(targetTableList));
  }

  /**
   * 出力対象として指定されたスキーマ名のリストを取得するメソッド<br>
   * DBからの取得をスキーマ単位でSQL側で絞り込むために用いる（テーブル単位の絞り込みは{@link #matches}で行う）
   *
   * @return スキーマ名のリスト。未指定の場合は空リスト（全スキーマが対象）
   */
  public List<String> schemaNames() {
    return targetSchemaList;
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
