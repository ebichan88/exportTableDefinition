package com.export_table_definition.mcp.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@link SqlNames}のテスト */
class SqlNamesTest {

  private static final ObjectKey TABLE = new ObjectKey("db", "sales", "orders");

  @Test
  @DisplayName("デフォルト値のnextvalからシーケンスを求め、スキーマ修飾が無ければテーブルのスキーマとみなす")
  void findsSequenceOfDefault() {
    assertEquals(
        Optional.of(new ObjectKey("db", "public", "orders_id_seq")),
        SqlNames.sequenceOfDefault("nextval('public.orders_id_seq'::regclass)", TABLE));
    assertEquals(
        Optional.of(new ObjectKey("db", "sales", "Orders_Seq")),
        SqlNames.sequenceOfDefault("NEXTVAL('\"Orders_Seq\"'::regclass)", TABLE));
    assertEquals(Optional.empty(), SqlNames.sequenceOfDefault("now()", TABLE));
  }

  @Test
  @DisplayName("カラムの型から、配列の[]を除いて型を求める")
  void findsTypeOfColumn() {
    assertEquals(
        new ObjectKey("db", "sales", "status"), SqlNames.typeOfColumn("sales.status[]", TABLE));
    assertEquals(new ObjectKey("db", "sales", "status"), SqlNames.typeOfColumn("status", TABLE));
  }

  @Test
  @DisplayName("トリガーの関数名から、引数の括弧を除いて関数を求める")
  void findsFunctionOfTrigger() {
    assertEquals(
        new ObjectKey("db", "audit", "log_change"),
        SqlNames.functionOfTrigger("audit.log_change()", TABLE));
  }

  @Test
  @DisplayName("同じオブジェクトかどうかは大文字小文字を区別せずに判定する")
  void comparesIgnoringCase() {
    assertTrue(SqlNames.sameObject(TABLE, new ObjectKey("DB", "Sales", "ORDERS")));
    assertFalse(SqlNames.sameObject(TABLE, new ObjectKey("db", "hr", "orders")));
  }

  @Test
  @DisplayName("名前は1つの語として現れる場合だけ当てはまる（識別子の一部・日本語の一部は除く）")
  void matchesWholeWords() {
    assertTrue(SqlNames.wordPattern("employee").matcher("insert into sample.Employee(").find());
    assertTrue(SqlNames.wordPattern("employee").matcher("from \"employee\" e").find());
    assertFalse(SqlNames.wordPattern("employee").matcher("employee_id = v_employee").find());
    assertFalse(SqlNames.wordPattern("社員").matcher("from 社員履歴").find());
  }
}
