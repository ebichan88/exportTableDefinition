package com.export_table_definition.domain.model.snapshot;

import static com.export_table_definition.domain.model.snapshot.SnapshotValues.split;
import static com.export_table_definition.domain.model.snapshot.SnapshotValues.text;

import com.export_table_definition.domain.model.TableDefinitionContent;
import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import com.export_table_definition.domain.model.type.Cardinality;
import java.util.List;

/**
 * スキーマのスナップショットのうち、1テーブル分の情報を表すrecordクラス<br>
 * テーブル定義書1ファイル分と同じ情報（カラム・インデックス・制約・外部キー・論理リレーション・トリガー・ 手動付帯情報）を、Markdownの表示形式ではなく個別の項目として保持する。
 * 被参照側の外部キーは、参照元テーブルの{@code foreignKeys}から導出できるため保持しない
 *
 * @param schema スキーマ名
 * @param name 物理テーブル名
 * @param logicalName 論理テーブル名
 * @param type 区分（table/view/materialized_view）
 * @param description テーブル説明（サイドカーYAML由来）
 * @param remarks テーブル備考（サイドカーYAML由来）
 * @param definition view/materialized viewのソース定義
 * @param columns カラム情報のリスト
 * @param indexes インデックス情報のリスト
 * @param constraints 制約情報のリスト
 * @param foreignKeys DBに実在する外部キー制約のリスト
 * @param logicalRelations サイドカーYAMLで宣言された論理リレーションのリスト
 * @param triggers トリガー情報のリスト
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
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

  /**
   * 1テーブル分の定義書出力に必要な情報からスナップショットを生成するメソッド
   *
   * @param content 1テーブル分の定義書出力に必要な情報
   * @return 1テーブル分のスナップショット
   */
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
   * カラム情報
   *
   * @param name 物理カラム名
   * @param logicalName 論理カラム名
   * @param type データ型
   * @param precisionScale 桁数/精度
   * @param primaryKey 主キーを構成するカラムであるか
   * @param notNull NOT NULL制約を持つか
   * @param defaultValue デフォルト値
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

  /**
   * インデックス情報
   *
   * @param name インデックス名
   * @param method インデックスの種別（アクセスメソッド）
   * @param unique 一意インデックスであるか
   * @param primary 主キーのインデックスであるか
   * @param definition インデックスの定義
   * @param remarks 備考
   */
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
   * 制約情報
   *
   * @param name 制約名
   * @param type 制約種別（CHECK/FOREIGN KEY/PRIMARY KEY/UNIQUE）
   * @param definition 制約定義
   * @param remarks 備考
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
   * @param columns 参照元（自テーブル）のカラムのリスト
   * @param referenceSchema 参照先スキーマ名
   * @param referenceTable 参照先テーブル名
   * @param referenceColumns 参照先のカラムのリスト
   * @param cardinality 多重度
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
          split(foreignKey.columnNames(), ","),
          foreignKey.referenceSchemaName(),
          foreignKey.referenceTableName(),
          split(foreignKey.referenceColumnNames(), ","),
          foreignKey.cardinality());
    }
  }

  /**
   * トリガー情報
   *
   * @param name トリガー名
   * @param timing 実行タイミング（BEFORE/AFTER/INSTEAD OF）
   * @param events 対象イベント（INSERT/UPDATE/DELETE/TRUNCATE）のリスト
   * @param orientation 実行単位（ROW/STATEMENT）
   * @param function 実行される関数名（スキーマ修飾）
   * @param definition トリガー定義
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
          split(trigger.events(), "/"),
          text(trigger.orientation()),
          text(trigger.functionName()),
          text(trigger.triggerDefinition()));
    }
  }
}
