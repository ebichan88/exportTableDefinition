package com.export_table_definition.domain.model.entity;

import com.export_table_definition.domain.model.type.Cardinality;
import com.export_table_definition.domain.model.type.RelationType;

/**
 * 外部キー情報に関するrecordクラス<br>
 * DBに実在する外部キー制約（{@link RelationType#PHYSICAL}）と、サイドカーYAMLで宣言された 論理リレーション（{@link
 * RelationType#LOGICAL}）の双方を表す。 両者はER図では同じグラフ上に描画されるが、掲載セクションと線種によって区別される
 *
 * @param schemaName 参照元（子）スキーマ名
 * @param tableName 参照元（子）テーブル名
 * @param foreignkeyName 外部キー名（論理リレーションの場合は関連名）
 * @param columnNames 参照元（子）の列名をカンマ区切りで連結した文字列
 * @param referenceSchemaName 参照先（親）スキーマ名
 * @param referenceTableName 参照先（親）テーブル名
 * @param referenceColumnNames 参照先（親）の列名をカンマ区切りで連結した文字列
 * @param cardinality 多重度
 * @param relationType 関連の由来（物理／論理）
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public record ForeignKeyEntity(
    String schemaName,
    String tableName,
    String foreignkeyName,
    String columnNames,
    String referenceSchemaName,
    String referenceTableName,
    String referenceColumnNames,
    Cardinality cardinality,
    RelationType relationType)
    implements SchemaTableKeyed {

  /**
   * 多重度を指定しない場合のコンストラクタ<br>
   * 多重度を判定できない場合は、外部キーの関連として最も一般的な1対多とみなす
   *
   * @param schemaName 参照元（子）スキーマ名
   * @param tableName 参照元（子）テーブル名
   * @param foreignkeyName 外部キー名
   * @param columnNames 参照元（子）の列名をカンマ区切りで連結した文字列
   * @param referenceSchemaName 参照先（親）スキーマ名
   * @param referenceTableName 参照先（親）テーブル名
   * @param referenceColumnNames 参照先（親）の列名をカンマ区切りで連結した文字列
   */
  public ForeignKeyEntity(
      String schemaName,
      String tableName,
      String foreignkeyName,
      String columnNames,
      String referenceSchemaName,
      String referenceTableName,
      String referenceColumnNames) {
    this(
        schemaName,
        tableName,
        foreignkeyName,
        columnNames,
        referenceSchemaName,
        referenceTableName,
        referenceColumnNames,
        Cardinality.ONE_TO_MANY,
        RelationType.PHYSICAL);
  }

  /**
   * サイドカーYAML由来の論理リレーションを生成する静的ファクトリメソッド<br>
   * DBに外部キー制約が存在しないため、多重度は機械的に判定できない。 呼び出し側でYAMLの明示指定を解決した上で渡すこと
   *
   * @param schemaName 参照元（子）スキーマ名
   * @param tableName 参照元（子）テーブル名
   * @param relationName 関連名
   * @param columnNames 参照元（子）の列名をカンマ区切りで連結した文字列
   * @param referenceSchemaName 参照先（親）スキーマ名
   * @param referenceTableName 参照先（親）テーブル名
   * @param referenceColumnNames 参照先（親）の列名をカンマ区切りで連結した文字列
   * @param cardinality 多重度
   * @return 論理リレーションを表すForeignKeyEntity
   */
  public static ForeignKeyEntity logical(
      String schemaName,
      String tableName,
      String relationName,
      String columnNames,
      String referenceSchemaName,
      String referenceTableName,
      String referenceColumnNames,
      Cardinality cardinality) {
    return new ForeignKeyEntity(
        schemaName,
        tableName,
        relationName,
        columnNames,
        referenceSchemaName,
        referenceTableName,
        referenceColumnNames,
        cardinality,
        RelationType.LOGICAL);
  }

  /**
   * 参照先の スキーマ.テーブル 形式の名称を取得するメソッド
   *
   * @return 参照先の スキーマ.テーブル 形式の名称
   */
  public String getReferenceSchemaTableName() {
    return referenceSchemaName + "." + referenceTableName;
  }

  /**
   * サイドカーYAML由来の論理リレーションか判定するメソッド
   *
   * @return 論理リレーションの場合はtrue
   */
  public boolean isLogical() {
    return relationType == RelationType.LOGICAL;
  }
}
