package com.dbxray.mcp.catalog;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 1スキーマに含まれるオブジェクトの数
 *
 * @param dbms DBMS種別。未設定の場合は空文字
 * @param majorVersion DBMSのメジャーバージョン。不明な場合はnull
 * @param functions 関数・プロシージャの数（オーバーロードはそれぞれ数える）
 */
public record SchemaSummary(
    String database,
    String dbms,
    Integer majorVersion,
    String schema,
    int tables,
    int views,
    int materializedViews,
    int functions,
    int sequences,
    int types) {

  /**
   * スキーマごとにオブジェクトの数を集計するメソッド
   *
   * @return DB名・スキーマ名の順。オブジェクトを1つも持たないスキーマは含まない
   */
  static List<SchemaSummary> summarize(
      List<DatabaseEntry> databases,
      List<TableEntry> tables,
      List<FunctionOverloads> functions,
      List<SequenceEntry> sequences,
      List<TypeEntry> types) {
    final Map<String, DatabaseEntry> databaseByName = new HashMap<>();
    databases.forEach(database -> databaseByName.put(database.name(), database));
    final Map<SchemaId, Counter> counters =
        new TreeMap<>(Comparator.comparing(SchemaId::database).thenComparing(SchemaId::schema));
    for (final TableEntry table : tables) {
      final Counter counter = counterOf(counters, table.key());
      TableType.of(table.type())
          .ifPresent(
              type -> {
                switch (type) {
                  case TABLE -> counter.tables++;
                  case VIEW -> counter.views++;
                  case MATERIALIZED_VIEW -> counter.materializedViews++;
                }
              });
    }
    functions.forEach(
        function -> counterOf(counters, function.key()).functions += function.overloads().size());
    sequences.forEach(sequence -> counterOf(counters, sequence.key()).sequences++);
    types.forEach(type -> counterOf(counters, type.key()).types++);
    return counters.entrySet().stream()
        .map(
            entry -> {
              final String database = entry.getKey().database();
              final Counter counter = entry.getValue();
              final DatabaseEntry databaseEntry =
                  databaseByName.getOrDefault(database, new DatabaseEntry(database, "", null));
              return new SchemaSummary(
                  database,
                  databaseEntry.dbms(),
                  databaseEntry.majorVersion(),
                  entry.getKey().schema(),
                  counter.tables,
                  counter.views,
                  counter.materializedViews,
                  counter.functions,
                  counter.sequences,
                  counter.types);
            })
        .toList();
  }

  private static Counter counterOf(Map<SchemaId, Counter> counters, ObjectKey key) {
    return counters.computeIfAbsent(
        new SchemaId(key.database(), key.schema()), id -> new Counter());
  }

  /** 集計のキー */
  private record SchemaId(String database, String schema) {}

  /** 集計中のオブジェクトの数 */
  private static final class Counter {
    private int tables;
    private int views;
    private int materializedViews;
    private int functions;
    private int sequences;
    private int types;
  }
}
