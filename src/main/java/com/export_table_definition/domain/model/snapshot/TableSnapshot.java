package com.export_table_definition.domain.model.snapshot;

import static com.export_table_definition.domain.model.snapshot.SnapshotValues.text;

import com.export_table_definition.domain.model.relation.Cardinality;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.sidecar.TableAnnotation;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.ConstraintEntity;
import com.export_table_definition.domain.model.table.IndexEntity;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.model.target.TableDefinitionContent;
import java.util.List;

/**
 * スキーマのスナップショットのうち、1テーブル分の情報を表すrecordクラス<br>
 * テーブル定義書1ファイル分と同じ情報（カラム・インデックス・制約・外部キー・論理リレーション・トリガー・ 手動付帯情報）を、Markdownの表示形式ではなく個別の項目として保持する。
 * 被参照側の外部キーは、参照元テーブルの{@code foreignKeys}から導出できるため保持しない
 *
 * @param type 区分（table/view/materialized_view）
 * @param description テーブル説明（サイドカーYAML由来）
 * @param remarks テーブル備考（サイドカーYAML由来）
 * @param definition view/materialized viewのソース定義
 * @param foreignKeys DBに実在する外部キー制約のリスト
 * @param logicalRelations サイドカーYAMLで宣言された論理リレーションのリスト
 */
public record TableSnapshot(
    String schema,
    String name,
    String logicalName,
    String type,
    String description,
    String remarks,
    String definition,
    List<Column> columns,
    List<Index> indexes,
    List<Constraint> constraints,
    List<Relation> foreignKeys,
    List<Relation> logicalRelations,
    List<Trigger> triggers) {

  public static TableSnapshot of(TableDefinitionContent content) {
    final TableEntity table = content.table();
    final TableAnnotation annotation = content.annotation();
    return new TableSnapshot(
        table.schemaName(),
        table.physicalTableName(),
        text(table.logicalTableName()),
        table.tableType().getName(),
        text(annotation.description()),
        text(annotation.remarks()),
        text(table.definition()),
        content.columns().stream().map(column -> Column.of(column, annotation)).toList(),
        content.indexes().stream().map(Index::of).toList(),
        content.constraints().stream().map(Constraint::of).toList(),
        content.foreignKeys().stream().map(Relation::of).toList(),
        content.logicalRelations().stream().map(Relation::of).toList(),
        content.triggers().stream().map(Trigger::of).toList());
  }

  /**
   * @param remarks カラム備考（サイドカーYAML由来）
   */
  public record Column(
      String name,
      String logicalName,
      String type,
      String precisionScale,
      boolean primaryKey,
      boolean notNull,
      String defaultValue,
      String remarks) {

    static Column of(ColumnEntity column, TableAnnotation annotation) {
      return new Column(
          column.physicalColumnName(),
          text(column.logicalColumnName()),
          column.columnType(),
          text(column.precisionScale()),
          column.primaryKey(),
          column.notNull(),
          text(column.defaultValue()),
          text(annotation.columnRemark(column.physicalColumnName())));
    }
  }

  public record Index(
      String name,
      String method,
      boolean unique,
      boolean primary,
      String definition,
      String remarks) {

    static Index of(IndexEntity index) {
      return new Index(
          index.indexName(),
          text(index.indexMethod()),
          index.isUnique(),
          index.isPrimary(),
          text(index.indexDefinition()),
          text(index.remarks()));
    }
  }

  /**
   * @param type 制約種別（CHECK/FOREIGN KEY/PRIMARY KEY/UNIQUE）
   */
  public record Constraint(String name, String type, String definition, String remarks) {

    static Constraint of(ConstraintEntity constraint) {
      return new Constraint(
          constraint.constraintName(),
          text(constraint.constraintType()),
          text(constraint.constraintDefinition()),
          text(constraint.remarks()));
    }
  }

  /**
   * 外部キー・論理リレーション情報（自テーブル → 参照先）
   *
   * @param name 外部キー名（論理リレーションの場合は関連名）
   */
  public record Relation(
      String name,
      List<String> columns,
      String referenceSchema,
      String referenceTable,
      List<String> referenceColumns,
      Cardinality cardinality) {

    static Relation of(ForeignKeyEntity foreignKey) {
      return new Relation(
          foreignKey.foreignkeyName(),
          foreignKey.columnNames(),
          foreignKey.referenceSchemaName(),
          foreignKey.referenceTableName(),
          foreignKey.referenceColumnNames(),
          foreignKey.cardinality());
    }
  }

  /**
   * @param timing 実行タイミング（BEFORE/AFTER/INSTEAD OF）
   * @param events 対象イベント（INSERT/UPDATE/DELETE/TRUNCATE）のリスト
   * @param orientation 実行単位（ROW/STATEMENT）
   * @param function 実行される関数名（スキーマ修飾）
   */
  public record Trigger(
      String name,
      String timing,
      List<String> events,
      String orientation,
      String function,
      String definition) {

    static Trigger of(TriggerEntity trigger) {
      return new Trigger(
          trigger.triggerName(),
          text(trigger.timing()),
          trigger.events(),
          text(trigger.orientation()),
          text(trigger.functionName()),
          text(trigger.triggerDefinition()));
    }
  }
}
