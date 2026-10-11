package com.dbxray.domain.model.metrics;

import com.dbxray.domain.model.relation.ForeignKeyEntity;
import com.dbxray.domain.model.schemaobject.FunctionEntity;
import com.dbxray.domain.model.schemaobject.SequenceEntity;
import com.dbxray.domain.model.schemaobject.TypeEntity;
import com.dbxray.domain.model.table.ColumnEntity;
import com.dbxray.domain.model.table.TableDetail;
import com.dbxray.domain.model.table.TableEntity;
import com.dbxray.domain.model.table.TableKey;
import com.dbxray.domain.model.target.ExportTargets;
import com.dbxray.domain.model.target.OutputObjectType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 出力対象の集計（スキーマごとの{@link SchemaMetrics}）を扱うクラス<br>
 * カラム以外は一括取得した情報（{@link ExportTargets}）から数え、カラムはスキーマ・チャンク単位で取得するたびに{@link Builder}へ足す。
 * 集計用のSQLは使わない（テーブルの絞り込みはSQLではなく取得後に行うため、SQLで数えると出力したドキュメントと数が合わなくなる）
 */
public final class DatabaseMetrics {

  private final List<SchemaMetrics> schemas;
  private final Set<OutputObjectType> objectTypes;

  private DatabaseMetrics(List<SchemaMetrics> schemas, Set<OutputObjectType> objectTypes) {
    this.schemas = List.copyOf(schemas);
    this.objectTypes = Set.copyOf(objectTypes);
  }

  /**
   * 一括取得した情報から集計を始めるメソッド
   *
   * @param targets 一括取得した出力対象の情報
   * @return カラム以外を数え終えた{@link Builder}
   */
  public static Builder builder(ExportTargets targets) {
    return new Builder(targets);
  }

  /**
   * スキーマごとの集計を取得するメソッド
   *
   * @return スキーマ名の順。出力対象のオブジェクトを1つも持たないスキーマは含まない
   */
  public List<SchemaMetrics> schemas() {
    return schemas;
  }

  /**
   * 全スキーマの合計を取得するメソッド
   *
   * @return スキーマ名が空文字の集計
   */
  public SchemaMetrics total() {
    return schemas.stream().reduce(Counter.ZERO, SchemaMetrics::plus);
  }

  /**
   * 集計の対象が1つも無いか判定するメソッド
   *
   * @return 出力対象のオブジェクトが1つも無い場合はtrue
   */
  public boolean isEmpty() {
    return schemas.isEmpty();
  }

  /**
   * 指定した種別を取得したか判定するメソッド<br>
   * {@code target.objects}で外した種別は取得自体をしないため、数は0になるが0件とは限らない。表示する列の出し分けに用いる
   *
   * @return 取得した種別の場合はtrue
   */
  public boolean isCounted(OutputObjectType type) {
    return objectTypes.contains(type);
  }

  /** {@link DatabaseMetrics}を組み立てるクラス */
  public static final class Builder {

    private final Map<String, Counter> counters = new TreeMap<>();
    private final Set<OutputObjectType> objectTypes;

    private Builder(ExportTargets targets) {
      this.objectTypes = targets.objectTypes();
      final Set<TableKey> relatedTables = new HashSet<>();
      targets.foreignKeys().stream()
          .forEach(
              relation -> {
                relatedTables.add(relation.tableKey());
                relatedTables.add(relation.referenceTableKey());
                countRelation(relation);
              });
      for (final TableEntity table : targets.tables().asList()) {
        final Counter counter = counterOf(table.schemaName());
        switch (table.tableType()) {
          case TABLE -> counter.tables++;
          case VIEW -> counter.views++;
          case MATERIALIZED_VIEW -> counter.materializedViews++;
        }
        counter.partitionedTables += table.isPartitioned() ? 1 : 0;
        counter.triggers += targets.triggers().belongingTo(table).size();
        counter.tablesWithLogicalName += hasText(table.logicalTableName()) ? 1 : 0;
        counter.unrelatedTables += relatedTables.contains(TableKey.of(table)) ? 0 : 1;
        counter.viewpointTables += targets.viewpoints().containing(table).isEmpty() ? 0 : 1;
        counter.annotatedTables += targets.annotations().belongingTo(table).isEmpty() ? 0 : 1;
      }
      for (final FunctionEntity function : targets.functions().asList()) {
        final Counter counter = counterOf(function.schemaName());
        if (function.isProcedure()) {
          counter.procedures++;
        } else {
          counter.functions++;
        }
      }
      for (final SequenceEntity sequence : targets.sequences().asList()) {
        counterOf(sequence.schemaName()).sequences++;
      }
      for (final TypeEntity type : targets.types().asList()) {
        counterOf(type.schemaName()).types++;
      }
    }

    private void countRelation(ForeignKeyEntity relation) {
      final Counter counter = counterOf(relation.schemaName());
      if (relation.isLogical()) {
        counter.logicalRelations++;
      } else {
        counter.foreignKeys++;
      }
    }

    /**
     * 1テーブル分のカラムを数えるメソッド<br>
     * カラムはスキーマ・チャンク単位で取得して破棄するため、取得するたびに呼ぶ。同じテーブルを2回渡すと2回数える
     *
     * @param detail 出力対象の1テーブル分の詳細情報
     */
    public void collectColumns(TableDetail detail) {
      final Counter counter = counterOf(detail.table().schemaName());
      for (final ColumnEntity column : detail.columns()) {
        counter.columns++;
        counter.columnsWithLogicalName += hasText(column.logicalColumnName()) ? 1 : 0;
      }
    }

    /** 集計を確定するメソッド */
    public DatabaseMetrics build() {
      return new DatabaseMetrics(
          counters.entrySet().stream()
              .map(entry -> entry.getValue().toMetrics(entry.getKey()))
              .toList(),
          objectTypes);
    }

    private Counter counterOf(String schemaName) {
      return counters.computeIfAbsent(schemaName, schema -> new Counter());
    }

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }

  /** 集計中の1スキーマ分の数 */
  private static final class Counter {

    /** 合計を求める際の初期値 */
    private static final SchemaMetrics ZERO = new Counter().toMetrics("");

    private int tables;
    private int partitionedTables;
    private int views;
    private int materializedViews;
    private int columns;
    private int functions;
    private int procedures;
    private int sequences;
    private int types;
    private int triggers;
    private int tablesWithLogicalName;
    private int columnsWithLogicalName;
    private int foreignKeys;
    private int logicalRelations;
    private int unrelatedTables;
    private int viewpointTables;
    private int annotatedTables;

    private SchemaMetrics toMetrics(String schemaName) {
      return new SchemaMetrics(
          schemaName,
          tables,
          partitionedTables,
          views,
          materializedViews,
          columns,
          functions,
          procedures,
          sequences,
          types,
          triggers,
          tablesWithLogicalName,
          columnsWithLogicalName,
          foreignKeys,
          logicalRelations,
          unrelatedTables,
          viewpointTables,
          annotatedTables);
    }
  }
}
