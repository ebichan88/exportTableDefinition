package com.export_table_definition.domain.service.target;

import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.relation.ForeignKeys;
import com.export_table_definition.domain.model.sidecar.Annotations;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.TableDetail;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.Tables;
import com.export_table_definition.domain.model.target.ConsistencyFinding;
import com.export_table_definition.domain.model.target.ConsistencyFinding.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 出力対象のテーブルと、それを参照する情報（外部キー・サイドカーの論理リレーション／付帯情報）を突き合わせるドメインサービス<br>
 * 外部キーはスキーマ全体から、サイドカーは出力対象に関係なく読み込むため、出力対象の絞り込みで除外されたテーブルや、
 * リネーム・削除されたテーブル／カラムを参照していることがある。それらを出力から除外し、利用者が気付けるよう指摘（{@link
 * ConsistencyFinding}）として返す。指摘をどこへ出力するか（ログ等）は呼び出し側が決める
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ExportTargetConsistencyDomainService {

  /**
   * 外部キーの突き合わせ結果
   *
   * @param foreignKeys 出力対象のテーブル同士の外部キーの集合（物理外部キー、論理リレーションの順）
   * @param findings 突き合わせで見つかった指摘のリスト
   */
  public record ResolvedForeignKeys(ForeignKeys foreignKeys, List<ConsistencyFinding> findings) {

    /** コンパクトコンストラクタ（指摘のリストは変更不可な複製として保持する） */
    public ResolvedForeignKeys {
      findings = List.copyOf(findings);
    }
  }

  /**
   * 物理外部キーとサイドカー由来の論理リレーションのうち、参照元・参照先の双方が出力対象のテーブルであるものを 1つの外部キーの集合にまとめるメソッド<br>
   * 片側だけが出力対象のものを残すと、テーブル一覧・個別の定義書には現れないテーブルがER図にだけ箱として残ってしまうため除外する。
   * 論理リレーションも物理外部キーと同じ集合へ合流させることで、ER図のグループ分割（連結成分）やスキーマ跨ぎ関連の抽出にも自動的に反映される。 <br>
   * 除外したものを指摘とするかは由来ごとに異なる。物理外部キーは、出力対象が絞り込まれている場合は意図した除外のため指摘しない。
   * 論理リレーションは、除外の理由が絞り込みによるものかリネーム・削除による乖離かを区別できないため、一律で指摘する
   *
   * @param physicalForeignKeys スキーマ全体から取得した外部キー制約のリスト
   * @param logicalRelations サイドカーで宣言された論理リレーションのリスト
   * @param tables 出力対象のテーブル
   * @param isFiltered 出力対象がスキーマ・テーブルで絞り込まれているか
   * @return 出力対象のテーブル同士の外部キーの集合と、除外等に関する指摘
   */
  public ResolvedForeignKeys resolveForeignKeys(
      List<ForeignKeyEntity> physicalForeignKeys,
      List<ForeignKeyEntity> logicalRelations,
      Tables tables,
      boolean isFiltered) {
    final List<ConsistencyFinding> findings = new ArrayList<>();
    final List<ForeignKeyEntity> resolvedRelations =
        resolveLogicalRelations(logicalRelations, tables, findings);
    final List<ForeignKeyEntity> resolvedForeignKeys =
        physicalForeignKeys.stream()
            .filter(fk -> isResolvablePhysicalForeignKey(fk, tables, isFiltered, findings))
            .toList();
    return new ResolvedForeignKeys(
        ForeignKeys.of(
            Stream.concat(resolvedForeignKeys.stream(), resolvedRelations.stream()).toList()),
        findings);
  }

  /**
   * 実在しないテーブルに対する付帯情報（＝孤児付帯情報）を検出するメソッド<br>
   * リネームや削除により、サイドカーの付帯情報が現在のスキーマと乖離した場合の気付きとする。 スキーマ・テーブルの出力対象が絞り込まれている場合は、対象外テーブルの付帯情報を
   * 誤って孤児と判定しないよう検出を行わない（検出を行わなかったこと自体を指摘として返す）
   *
   * @param annotations 読み込んだ付帯情報
   * @param tables 出力対象のテーブル
   * @param isFiltered 出力対象がスキーマ・テーブルで絞り込まれているか
   * @return 指摘のリスト
   */
  public List<ConsistencyFinding> findOrphanTableAnnotations(
      Annotations annotations, Tables tables, boolean isFiltered) {
    if (annotations.isEmpty()) {
      return List.of();
    }
    if (isFiltered) {
      return List.of(
          new ConsistencyFinding(
              Kind.ORPHAN_TABLE_ANNOTATION_CHECK_SKIPPED,
              "Skipping orphan table annotation check because the output target is filtered."));
    }
    return annotations.tableKeys().stream()
        .filter(key -> !tables.contains(key))
        .map(
            key ->
                new ConsistencyFinding(
                    Kind.ORPHAN_TABLE_ANNOTATION,
                    "Annotation exists for a table that was not found (renamed or dropped?). [table="
                        + key.qualifiedName()
                        + "]"))
        .toList();
  }

  /**
   * 実在しないカラムに対するカラム備考（＝孤児付帯情報）を検出するメソッド<br>
   * 出力対象のテーブルに対してのみ、実在カラムと付帯情報のカラム名を突き合わせて検出する
   *
   * @param detail 出力対象のテーブルの詳細情報
   * @param annotations 対象範囲全体の手動付帯情報
   * @return 指摘のリスト
   */
  public List<ConsistencyFinding> findOrphanColumnAnnotations(
      TableDetail detail, Annotations annotations) {
    final TableEntity table = detail.table();
    final Set<String> actualColumnNames =
        detail.columns().stream().map(ColumnEntity::physicalColumnName).collect(Collectors.toSet());
    return annotations.of(table).orphanColumnNames(actualColumnNames).stream()
        .map(
            columnName ->
                new ConsistencyFinding(
                    Kind.ORPHAN_COLUMN_ANNOTATION,
                    "Column annotation exists for a column that was not found (renamed or dropped?). "
                        + "[table="
                        + table.getSchemaTableName()
                        + ", column="
                        + columnName
                        + "]"))
        .toList();
  }

  /**
   * 論理リレーションのうち、参照元・参照先の双方が出力対象のテーブルであるものを抽出するメソッド<br>
   * 除外したものは、どちら側が解決できなかったかを指摘に加える。合流させた件数も指摘（報告）として加える
   *
   * @param logicalRelations サイドカーで宣言された論理リレーションのリスト
   * @param tables 出力対象のテーブル
   * @param findings 指摘の追加先
   * @return 出力対象のテーブル同士の論理リレーションのリスト
   */
  private List<ForeignKeyEntity> resolveLogicalRelations(
      List<ForeignKeyEntity> logicalRelations, Tables tables, List<ConsistencyFinding> findings) {
    if (logicalRelations.isEmpty()) {
      return List.of();
    }
    final List<ForeignKeyEntity> resolved =
        logicalRelations.stream()
            .filter(relation -> isResolvableLogicalRelation(relation, tables, findings))
            .toList();
    findings.add(
        new ConsistencyFinding(
            Kind.LOGICAL_RELATIONS_MERGED,
            "Merged logical relations declared in the sidecar. [relationCount="
                + resolved.size()
                + "]"));
    return resolved;
  }

  /**
   * 物理外部キーの参照元・参照先が、いずれも出力対象のテーブルとして実在するか判定するメソッド<br>
   * 出力対象が絞り込まれていない場合のみ、除外する外部キーを指摘に加える
   *
   * @param foreignKey 判定対象の外部キー
   * @param tables 出力対象のテーブル
   * @param isFiltered 出力対象がスキーマ・テーブルで絞り込まれているか
   * @param findings 指摘の追加先
   * @return 双方が実在する場合はtrue
   */
  private boolean isResolvablePhysicalForeignKey(
      ForeignKeyEntity foreignKey,
      Tables tables,
      boolean isFiltered,
      List<ConsistencyFinding> findings) {
    if (tables.contains(foreignKey.tableKey()) && tables.contains(foreignKey.referenceTableKey())) {
      return true;
    }
    if (!isFiltered) {
      findings.add(
          new ConsistencyFinding(
              Kind.UNRESOLVED_FOREIGN_KEY,
              "Skipping a foreign key because the referenced table was not found "
                  + "(renamed or dropped?). [foreignKey="
                  + foreignKey.foreignkeyName()
                  + ", table="
                  + foreignKey.getSchemaTableName()
                  + ", referenceTable="
                  + foreignKey.getReferenceSchemaTableName()
                  + "]"));
    }
    return false;
  }

  /**
   * 論理リレーションの参照元・参照先が、いずれも出力対象のテーブルとして実在するか判定するメソッド<br>
   * 実在しない場合は、どちら側が解決できなかったかを指摘に加える
   *
   * @param relation 判定対象の論理リレーション
   * @param tables 出力対象のテーブル
   * @param findings 指摘の追加先
   * @return 双方が実在する場合はtrue
   */
  private boolean isResolvableLogicalRelation(
      ForeignKeyEntity relation, Tables tables, List<ConsistencyFinding> findings) {
    final boolean childExists = tables.contains(relation.tableKey());
    final boolean parentExists = tables.contains(relation.referenceTableKey());
    if (childExists && parentExists) {
      return true;
    }
    findings.add(
        new ConsistencyFinding(
            Kind.UNRESOLVED_LOGICAL_RELATION,
            "Skipping logical relation because the table was not found in the output target "
                + "(filtered, renamed or dropped?). [relation="
                + relation.foreignkeyName()
                + ", table="
                + relation.getSchemaTableName()
                + (childExists ? "" : " (not found)")
                + ", parentTable="
                + relation.getReferenceSchemaTableName()
                + (parentExists ? "" : " (not found)")
                + "]"));
    return false;
  }
}
