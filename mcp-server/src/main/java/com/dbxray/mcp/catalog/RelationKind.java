package com.dbxray.mcp.catalog;

/** 関連の由来 */
public enum RelationKind {
  /** DBに実在する外部キー制約 */
  FOREIGN_KEY,
  /** サイドカーYAMLで宣言された論理リレーション */
  LOGICAL_RELATION
}
