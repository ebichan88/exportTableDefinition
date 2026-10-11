package com.dbxray.domain.model.target;

import com.dbxray.domain.model.database.Dbms;
import com.dbxray.domain.model.schemaobject.FunctionEntity;
import com.dbxray.domain.model.tableusage.FunctionTableUsage;

/**
 * 関数・プロシージャ1つ分の出力内容（定義書1ファイル・スナップショットと参考情報の1件分）<br>
 * 出力先は持たない（{@link TableDefinitionContent}と同じく、出力形式ごとの書き込み側が持つ）
 *
 * @param function 定義本体を含む関数・プロシージャ
 * @param tableUsage 利用しているテーブル。抽出を行わない実行では{@link FunctionTableUsage#notAnalyzed()}
 * @param dbms DBMS種別（スキーマが決まらない名前を、PostgreSQLは{@code search_path}、Oracleは実行者で決まる名前として示すため）
 */
public record FunctionDefinitionContent(
    FunctionEntity function, FunctionTableUsage tableUsage, Dbms dbms) {

  /** 利用しているテーブルの抽出を行わない実行の出力内容 */
  public static FunctionDefinitionContent withoutTableUsage(FunctionEntity function, Dbms dbms) {
    return new FunctionDefinitionContent(function, FunctionTableUsage.notAnalyzed(), dbms);
  }
}
