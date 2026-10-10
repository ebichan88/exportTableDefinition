package com.dbxray.mcp.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * テーブルを頂点、外部キー・論理リレーションを辺とする関連のグラフ<br>
 * 被参照側の関連はスナップショットに保持されないため、組み立て時に全テーブルの関連から逆引きの索引を作る
 */
public final class RelationGraph {

  private final Map<ObjectKey, TableEntry> tablesByKey = new HashMap<>();
  private final Map<ObjectKey, List<Relation>> outgoing = new HashMap<>();
  private final Map<ObjectKey, List<Relation>> incoming = new HashMap<>();
  private final Map<ObjectKey, RelationCounts> counts = new HashMap<>();

  RelationGraph(List<TableEntry> tables) {
    for (final TableEntry table : tables) {
      tablesByKey.put(table.key(), table);
      final List<Relation> relations =
          Stream.concat(
                  table.foreignKeys().stream()
                      .map(entry -> Relation.of(table.key(), entry, RelationKind.FOREIGN_KEY)),
                  table.logicalRelations().stream()
                      .map(entry -> Relation.of(table.key(), entry, RelationKind.LOGICAL_RELATION)))
              .toList();
      outgoing.put(table.key(), relations);
      for (final Relation relation : relations) {
        incoming.computeIfAbsent(relation.to(), key -> new ArrayList<>()).add(relation);
      }
    }
    for (final ObjectKey table : tablesByKey.keySet()) {
      counts.put(
          table,
          new RelationCounts(
              neighbors(table, Direction.INCOMING).size(),
              neighbors(table, Direction.OUTGOING).size(),
              impactOf(table)));
    }
  }

  /** テーブルの関連の数を返すメソッド */
  public RelationCounts counts(TableEntry table) {
    return counts(table.key());
  }

  RelationCounts counts(ObjectKey table) {
    return counts.getOrDefault(table, new RelationCounts(0, 0, 0));
  }

  /** 向きを問わず関連でつながる、自テーブル以外のスナップショットに含まれるテーブル（重複なし） */
  Set<ObjectKey> adjacentTables(ObjectKey table) {
    return neighbors(table, Direction.BOTH);
  }

  /** テーブルが持つ（参照先へ向かう）関連を、外部キー・論理リレーションの順に返す */
  List<Relation> outgoing(ObjectKey table) {
    return outgoing.getOrDefault(table, List.of());
  }

  /**
   * テーブルから関連をたどるメソッド<br>
   * 幅優先でたどり、同じ関連は最初に見つけた段の1回だけ返す。スナップショットに含まれないテーブルの先はたどらない
   *
   * @param depth たどる段数（1以上）
   */
  public RelatedTables relatedTables(TableEntry start, int depth, Direction direction) {
    final Map<Relation, Integer> found = new LinkedHashMap<>();
    final Set<ObjectKey> visited = new LinkedHashSet<>(List.of(start.key()));
    final Set<ObjectKey> missing = new LinkedHashSet<>();
    List<ObjectKey> frontier = List.of(start.key());
    for (int level = 1; level <= depth && !frontier.isEmpty(); level++) {
      final List<ObjectKey> next = new ArrayList<>();
      for (final ObjectKey current : frontier) {
        for (final Relation relation : relationsOf(current, direction)) {
          found.putIfAbsent(relation, level);
          final ObjectKey neighbor = relation.otherSide(current);
          if (!tablesByKey.containsKey(neighbor)) {
            missing.add(neighbor);
          } else if (visited.add(neighbor)) {
            next.add(neighbor);
          }
        }
      }
      frontier = next;
    }
    return new RelatedTables(
        start,
        found.entrySet().stream()
            .map(entry -> new RelatedTables.RelationAtDepth(entry.getKey(), entry.getValue()))
            .toList(),
        visited.stream().map(tablesByKey::get).toList(),
        List.copyOf(missing));
  }

  /**
   * 指定したテーブル同士の関連を求めるメソッド（観点のER図に描く範囲）<br>
   * 指定したテーブル同士の外部キー・論理リレーションだけを返す（指定外のテーブルとの関連は含めない）
   *
   * @param tables 観点の所属テーブル等（宣言順）
   * @return テーブルは指定の順、関連はテーブルごとに外部キー・論理リレーションの順。スナップショットに無いテーブルは{@code missingTables}に分ける
   */
  public DiagramScope among(List<ObjectKey> tables) {
    final Set<ObjectKey> members = new LinkedHashSet<>(tables);
    final List<TableEntry> found =
        members.stream().filter(tablesByKey::containsKey).map(tablesByKey::get).toList();
    final List<Relation> relations =
        found.stream()
            .flatMap(table -> outgoing(table.key()).stream())
            .filter(relation -> members.contains(relation.to()))
            .filter(relation -> tablesByKey.containsKey(relation.to()))
            .toList();
    final List<ObjectKey> missing =
        members.stream().filter(key -> !tablesByKey.containsKey(key)).toList();
    return new DiagramScope(found, relations, missing);
  }

  /**
   * 2つのテーブルをつなぐ最短の経路を探すメソッド<br>
   * 外部キー・論理リレーションを向きを問わずたどる。スナップショットに含まれないテーブルは経由しない
   *
   * @param maxLength 経路の関連の数の上限（1以上）。これより長い経路は探さない
   * @param limit 返す経路の数の上限（1以上）
   */
  public JoinPaths joinPaths(TableEntry from, TableEntry to, int maxLength, int limit) {
    final ObjectKey goal = to.key();
    final Map<ObjectKey, Integer> distances = new HashMap<>(Map.of(from.key(), 0));
    final Map<ObjectKey, List<Step>> previousSteps = new HashMap<>();
    List<ObjectKey> frontier = List.of(from.key());
    for (int level = 1;
        level <= maxLength && !frontier.isEmpty() && !distances.containsKey(goal);
        level++) {
      final List<ObjectKey> next = new ArrayList<>();
      for (final ObjectKey current : frontier) {
        for (final Relation relation : relationsOf(current, Direction.BOTH)) {
          final ObjectKey neighbor = relation.otherSide(current);
          if (neighbor.equals(current) || !tablesByKey.containsKey(neighbor)) {
            continue;
          }
          final Integer distance = distances.get(neighbor);
          if (distance == null) {
            distances.put(neighbor, level);
            next.add(neighbor);
          }
          if (distance == null || distance == level) {
            previousSteps
                .computeIfAbsent(neighbor, key -> new ArrayList<>())
                .add(new Step(current, relation));
          }
        }
      }
      frontier = next;
    }
    if (!distances.containsKey(goal) || goal.equals(from.key())) {
      return new JoinPaths(List.of(), false);
    }
    final List<JoinPath> paths = new ArrayList<>();
    collectPaths(goal, from.key(), previousSteps, new ArrayList<>(), paths, limit + 1);
    return new JoinPaths(paths.stream().limit(limit).toList(), paths.size() > limit);
  }

  /**
   * 終点から始点へ、最短経路の手前の段をたどって経路を集める
   *
   * @param reversedSteps 終点からここまでにたどった段（終点側が先頭）
   * @param max 集める経路の上限（最短経路の数は組み合わせで急に増えるため打ち切る）
   */
  private static void collectPaths(
      ObjectKey current,
      ObjectKey start,
      Map<ObjectKey, List<Step>> previousSteps,
      List<Step> reversedSteps,
      List<JoinPath> paths,
      int max) {
    if (paths.size() >= max) {
      return;
    }
    if (current.equals(start)) {
      final List<ObjectKey> tables = new ArrayList<>(List.of(start));
      final List<Relation> relations = new ArrayList<>();
      for (int i = reversedSteps.size() - 1; i >= 0; i--) {
        final Step step = reversedSteps.get(i);
        relations.add(step.relation());
        tables.add(step.relation().otherSide(step.previous()));
      }
      paths.add(new JoinPath(tables, relations));
      return;
    }
    for (final Step step : previousSteps.getOrDefault(current, List.of())) {
      reversedSteps.add(step);
      collectPaths(step.previous(), start, previousSteps, reversedSteps, paths, max);
      reversedSteps.remove(reversedSteps.size() - 1);
    }
  }

  /** 参照元を{@link RelationCounts#IMPACT_DEPTH}段までたどって届くテーブルを数える */
  private int impactOf(ObjectKey table) {
    final Set<ObjectKey> reached = new HashSet<>(Set.of(table));
    List<ObjectKey> frontier = List.of(table);
    for (int level = 1; level <= RelationCounts.IMPACT_DEPTH && !frontier.isEmpty(); level++) {
      final List<ObjectKey> next = new ArrayList<>();
      for (final ObjectKey current : frontier) {
        for (final ObjectKey neighbor : neighbors(current, Direction.INCOMING)) {
          if (reached.add(neighbor)) {
            next.add(neighbor);
          }
        }
      }
      frontier = next;
    }
    return reached.size() - 1;
  }

  /** 関連でつながる、自テーブル以外のスナップショットに含まれるテーブル（重複なし） */
  private Set<ObjectKey> neighbors(ObjectKey table, Direction direction) {
    final Set<ObjectKey> neighbors = new LinkedHashSet<>();
    for (final Relation relation : relationsOf(table, direction)) {
      final ObjectKey neighbor = relation.otherSide(table);
      if (!neighbor.equals(table) && tablesByKey.containsKey(neighbor)) {
        neighbors.add(neighbor);
      }
    }
    return neighbors;
  }

  /** 指定した向きの関連を、参照先へ向かうもの・参照元から来るものの順に返す */
  private List<Relation> relationsOf(ObjectKey table, Direction direction) {
    final List<Relation> relations = new ArrayList<>();
    if (direction.followsOutgoing()) {
      relations.addAll(outgoing(table));
    }
    if (direction.followsIncoming()) {
      relations.addAll(incoming.getOrDefault(table, List.of()));
    }
    return relations;
  }

  /** 最短経路上で、あるテーブルへ1つ手前のテーブルから進む段 */
  private record Step(ObjectKey previous, Relation relation) {}
}
