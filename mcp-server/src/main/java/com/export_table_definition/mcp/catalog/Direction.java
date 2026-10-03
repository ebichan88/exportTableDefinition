package com.export_table_definition.mcp.catalog;

/** 関連をたどる向き */
public enum Direction {
  /** 自テーブルが参照しているテーブル（参照先）へたどる */
  OUTGOING,
  /** 自テーブルを参照しているテーブル（参照元）へたどる */
  INCOMING,
  /** 両方へたどる */
  BOTH;

  boolean followsOutgoing() {
    return this != INCOMING;
  }

  boolean followsIncoming() {
    return this != OUTGOING;
  }
}
