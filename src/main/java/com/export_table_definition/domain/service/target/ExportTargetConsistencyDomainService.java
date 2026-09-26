package com.export_table_definition.domain.service.target;

import com.export_table_definition.domain.model.annotation.Annotations;
import com.export_table_definition.domain.model.collection.Columns;
import com.export_table_definition.domain.model.collection.ForeignKeys;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 出力対象のテーブルと、それを参照する情報（外部キー・サイドカーの論理リレーション／付帯情報）を突き合わせるドメインサービス<br>
 * 外部キーはスキーマ全体から、サイドカーは出力対象に関係なく読み込むため、出力対象の絞り込みで除外されたテーブルや、
 * リネーム・削除されたテーブル／カラムを参照していることがある。それらを出力から除外し、必要に応じて警告ログで気付けるようにする
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTargetConsistencyDomainService {

  private static final Logger logger =
      LogManager.getLogger(ExportTargetConsistencyDomainService.class);

  /**
   * 物理外部キーとサイドカー由来の論理リレーションのうち、参照元・参照先の双方が出力対象のテーブルであるものを 1つの外部キーの集合にまとめるメソッド<br>
   * 片側だけが出力対象のものを残すと、テーブル一覧・個別の定義書には現れないテーブルがER図にだけ箱として残ってしまうため除外する。
   * 論理リレーションも物理外部キーと同じ集合へ合流させることで、ER図のグループ分割（連結成分）やスキーマ跨ぎ関連の抽出にも自動的に反映される。 <br>
   * 警告ログの出し方は由来ごとに異なる。物理外部キーは、出力対象が絞り込まれている場合は意図した除外のため警告しない。
   * 論理リレーションは、除外の理由が絞り込みによるものかリネーム・削除による乖離かを区別できないため、一律で警告する
   *
   * @param physicalForeignKeys スキーマ全体から取得した外部キー制約のリスト
   * @param logicalRelations サイドカーで宣言された論理リレーションのリスト
   * @param tables 出力対象のテーブル情報のリスト
   * @param isFiltered 出力対象がスキーマ・テーブルで絞り込まれているか
   * @return 出力対象のテーブル同士の外部キーの集合（物理外部キー、論理リレーションの順）
   */
  public ForeignKeys resolveForeignKeys(
      List<ForeignKeyEntity> physicalForeignKeys,
      List<ForeignKeyEntity> logicalRelations,
      List<TableEntity> tables,
      boolean isFiltered) {
    final Set<TableKey> existingKeys = tableKeys(tables);
    final List<ForeignKeyEntity> resolvedRelations =
        resolveLogicalRelations(logicalRelations, existingKeys);
    final List<ForeignKeyEntity> resolvedForeignKeys =
        physicalForeignKeys.stream()
            .filter(fk -> isResolvablePhysicalForeignKey(fk, existingKeys, isFiltered))
            .toList();
    return ForeignKeys.of(
        Stream.concat(resolvedForeignKeys.stream(), resolvedRelations.stream()).toList());
  }

  /**
   * 実在しないテーブルに対する付帯情報（＝孤児注釈）を検出して警告するメソッド<br>
   * リネームや削除により、サイドカーの付帯情報が現在のスキーマと乖離した場合の気付きとする。 スキーマ・テーブルの出力対象が絞り込まれている場合は、対象外テーブルの付帯情報を
   * 誤って孤児と判定しないよう検出をスキップする
   *
   * @param annotations 読み込んだ付帯情報
   * @param tables 出力対象のテーブル情報のリスト
   * @param isFiltered 出力対象がスキーマ・テーブルで絞り込まれているか
   */
  public void warnOrphanTableAnnotations(
      Annotations annotations, List<TableEntity> tables, boolean isFiltered) {
    if (annotations.isEmpty()) {
      return;
    }
    if (isFiltered) {
      logger.info("Skipping orphan table annotation check because the output target is filtered.");
      return;
    }
    final Set<TableKey> existingKeys = tableKeys(tables);
    annotations.tableKeys().stream()
        .filter(key -> !existingKeys.contains(key))
        .forEach(
            key ->
                logger.warn(
                    "Annotation exists for a table that was not found (renamed or dropped?). [table={}]",
                    key.qualifiedName()));
  }

  /**
   * 実在しないカラムに対するカラム備考（＝孤児注釈）を検出して警告するメソッド<br>
   * 出力対象のテーブルに対してのみ、実在カラムと付帯情報のカラム名を突き合わせて検出する
   *
   * @param table 出力対象のテーブル情報
   * @param columns 当該テーブルを含むチャンクのカラム情報
   * @param annotations 対象範囲全体の手動付帯情報
   */
  public void warnOrphanColumnAnnotations(
      TableEntity table, Columns columns, Annotations annotations) {
    final Set<String> actualColumnNames =
        columns.of(table).stream()
            .map(ColumnEntity::physicalColumnName)
            .collect(Collectors.toSet());
    annotations
        .of(table)
        .orphanColumnNames(actualColumnNames)
        .forEach(
            columnName ->
                logger.warn(
                    "Column annotation exists for a column that was not found (renamed or dropped?). "
                        + "[table={}, column={}]",
                    table.getSchemaTableName(),
                    columnName));
  }

  /**
   * 論理リレーションのうち、参照元・参照先の双方が出力対象のテーブルであるものを抽出するメソッド<br>
   * 除外したものは、どちら側が解決できなかったかを警告ログに出力する
   *
   * @param logicalRelations サイドカーで宣言された論理リレーションのリスト
   * @param existingKeys 出力対象のテーブルキーの集合
   * @return 出力対象のテーブル同士の論理リレーションのリスト
   */
  private List<ForeignKeyEntity> resolveLogicalRelations(
      List<ForeignKeyEntity> logicalRelations, Set<TableKey> existingKeys) {
    if (logicalRelations.isEmpty()) {
      return List.of();
    }
    final List<ForeignKeyEntity> resolved =
        logicalRelations.stream()
            .filter(relation -> isResolvableLogicalRelation(relation, existingKeys))
            .toList();
    logger.info(
        "Merged logical relations declared in the sidecar. [relationCount={}]", resolved.size());
    return resolved;
  }

  /**
   * 物理外部キーの参照元・参照先が、いずれも出力対象のテーブルとして実在するか判定するメソッド<br>
   * 出力対象が絞り込まれていない場合のみ、除外する外部キーを警告ログに出力する
   *
   * @param foreignKey 判定対象の外部キー
   * @param existingKeys 出力対象のテーブルキーの集合
   * @param isFiltered 出力対象がスキーマ・テーブルで絞り込まれているか
   * @return 双方が実在する場合はtrue
   */
  private boolean isResolvablePhysicalForeignKey(
      ForeignKeyEntity foreignKey, Set<TableKey> existingKeys, boolean isFiltered) {
    if (existingKeys.contains(foreignKey.tableKey())
        && existingKeys.contains(foreignKey.referenceTableKey())) {
      return true;
    }
    if (!isFiltered) {
      logger.warn(
          "Skipping a foreign key because the referenced table was not found "
              + "(renamed or dropped?). [foreignKey={}, table={}, referenceTable={}]",
          foreignKey.foreignkeyName(),
          foreignKey.getSchemaTableName(),
          foreignKey.getReferenceSchemaTableName());
    }
    return false;
  }

  /**
   * 論理リレーションの参照元・参照先が、いずれも出力対象のテーブルとして実在するか判定するメソッド<br>
   * 実在しない場合は、どちら側が解決できなかったかを警告ログに出力する
   *
   * @param relation 判定対象の論理リレーション
   * @param existingKeys 出力対象のテーブルキーの集合
   * @return 双方が実在する場合はtrue
   */
  private boolean isResolvableLogicalRelation(
      ForeignKeyEntity relation, Set<TableKey> existingKeys) {
    final boolean childExists = existingKeys.contains(relation.tableKey());
    final boolean parentExists = existingKeys.contains(relation.referenceTableKey());
    if (childExists && parentExists) {
      return true;
    }
    logger.warn(
        "Skipping logical relation because the table was not found in the output target "
            + "(filtered, renamed or dropped?). [relation={}, table={}{}, parentTable={}{}]",
        relation.foreignkeyName(),
        relation.getSchemaTableName(),
        childExists ? "" : " (not found)",
        relation.getReferenceSchemaTableName(),
        parentExists ? "" : " (not found)");
    return false;
  }

  /**
   * 出力対象のテーブルキーの集合を作成するメソッド
   *
   * @param tables 出力対象のテーブル情報のリスト
   * @return テーブルキーの集合
   */
  private static Set<TableKey> tableKeys(List<TableEntity> tables) {
    return tables.stream().map(TableKey::of).collect(Collectors.toSet());
  }
}
