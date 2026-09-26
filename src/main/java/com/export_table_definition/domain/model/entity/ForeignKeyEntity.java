package com.export_table_definition.domain.model.entity;

import com.export_table_definition.domain.model.type.Cardinality;
import com.export_table_definition.domain.model.type.RelationType;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.List;

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

  /** サイドカーYAMLで関連名が省略された場合に自動生成する名称の接尾辞（実在する外部キー制約名と紛れないようにする） */
  private static final String LOGICAL_RELATION_NAME_SUFFIX = "_lrel";

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
   * サイドカーYAMLで宣言された論理リレーションの関連名を解決する静的メソッド<br>
   * 名称が明示指定されている場合はそれをそのまま用いる。省略された場合は「参照元（子）テーブル名_列名..._lrel」形式で
   * 自動生成する。実在する外部キー制約名（DBの慣例："_fkey"等）とは異なる接尾辞を用いることで、由来の異なる名前が紛れないようにする
   *
   * @param rawName YAMLで指定された関連名（未指定の場合はnull・空白可）
   * @param childTableName 参照元（子）テーブル名
   * @param childColumnNames 参照元（子）の列名のリスト
   * @return 解決した関連名
   */
  public static String resolveLogicalRelationName(
      String rawName, String childTableName, List<String> childColumnNames) {
    if (rawName != null && !rawName.isBlank()) {
      return rawName.trim();
    }
    return childTableName + "_" + String.join("_", childColumnNames) + LOGICAL_RELATION_NAME_SUFFIX;
  }

  /**
   * 参照先の スキーマ.テーブル 形式の名称を取得するメソッド
   *
   * @return 参照先の スキーマ.テーブル 形式の名称
   */
  public String getReferenceSchemaTableName() {
    return referenceTableKey().qualifiedName();
  }

  /**
   * 参照先（親）テーブルのテーブルキーを取得するメソッド
   *
   * @return 参照先のテーブルキー
   */
  public TableKey referenceTableKey() {
    return TableKey.of(referenceSchemaName, referenceTableName);
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
