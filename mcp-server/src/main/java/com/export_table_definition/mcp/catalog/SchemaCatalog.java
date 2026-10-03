package com.export_table_definition.mcp.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * スナップショットから読み込んだ全テーブルと、その検索・名前の解決・関連のたどりを担うクラス<br>
 * 被参照側の関連はスナップショットに保持されないため、組み立て時に全テーブルの関連から逆引きの索引を作る
 */
public final class SchemaCatalog {

  /** 名前が見つからないときに返す、似た名前の候補の上限 */
  private static final int MAX_SUGGESTIONS = 5;

  private static final Comparator<TableEntry> BY_KEY =
      Comparator.comparing((TableEntry table) -> table.key().database())
          .thenComparing(table -> table.key().schema())
          .thenComparing(table -> table.key().name());

  private final List<TableEntry> tables;
  private final Map<TableKey, TableEntry> tablesByKey;
  private final Map<TableKey, List<Relation>> outgoing;
  private final Map<TableKey, List<Relation>> incoming;

  private SchemaCatalog(List<TableEntry> tables) {
    this.tables = tables;
    this.tablesByKey = new HashMap<>();
    this.outgoing = new HashMap<>();
    this.incoming = new HashMap<>();
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
  }

  /**
   * テーブルの一覧から組み立てるメソッド
   *
   * @param tables 全テーブル。キー（DB名・スキーマ名・テーブル名）が重複しないこと
   */
  public static SchemaCatalog of(List<TableEntry> tables) {
    return new SchemaCatalog(List.copyOf(tables));
  }

  /**
   * 全テーブルを返すメソッド
   *
   * @return 組み立て時に渡された順のテーブル
   */
  public List<TableEntry> tables() {
    return tables;
  }

  /**
   * テーブルを検索するメソッド
   *
   * @param limit 返す件数の上限（1以上）
   * @return 一致の強い順（同点の場合はDB名・スキーマ名・テーブル名の順）の検索結果
   */
  public SearchResult searchTables(SearchQuery query, SearchScope scope, int limit) {
    final List<TableHit> hits =
        tables.stream()
            .filter(table -> scope.matches(table.key()))
            .map(query::match)
            .flatMap(Optional::stream)
            .sorted(
                Comparator.comparingInt(TableHit::score)
                    .reversed()
                    .thenComparing(TableHit::table, BY_KEY))
            .toList();
    return new SearchResult(hits.size(), hits.stream().limit(limit).toList());
  }

  /**
   * 名前で指定されたテーブルを解決するメソッド<br>
   * テーブル名は大文字小文字を区別せず完全一致で比べる。見つからない場合は、テーブル名を検索語にした検索の上位を候補として返す
   */
  public TableLookup lookup(TableReference reference) {
    final List<TableEntry> matched =
        tables.stream().filter(table -> reference.matches(table.key())).sorted(BY_KEY).toList();
    if (matched.size() == 1) {
      return new TableLookup.Found(matched.get(0));
    }
    if (matched.size() > 1) {
      return new TableLookup.Ambiguous(matched);
    }
    return new TableLookup.NotFound(suggestionsFor(reference));
  }

  /**
   * テーブルから関連をたどるメソッド<br>
   * 幅優先でたどり、同じ関連は最初に見つけた段の1回だけ返す。スナップショットに含まれないテーブルの先はたどらない
   *
   * @param depth たどる段数（1以上）
   */
  public RelatedTables relatedTables(TableEntry start, int depth, Direction direction) {
    final Map<Relation, Integer> found = new LinkedHashMap<>();
    final Set<TableKey> visited = new LinkedHashSet<>(List.of(start.key()));
    final Set<TableKey> missing = new LinkedHashSet<>();
    List<TableKey> frontier = List.of(start.key());
    for (int level = 1; level <= depth && !frontier.isEmpty(); level++) {
      final List<TableKey> next = new ArrayList<>();
      for (final TableKey current : frontier) {
        for (final Relation relation : relationsOf(current, direction)) {
          found.putIfAbsent(relation, level);
          final TableKey neighbor =
              relation.from().equals(current) ? relation.to() : relation.from();
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

  /** 指定した向きの関連を、参照先へ向かうもの・参照元から来るものの順に返す */
  private List<Relation> relationsOf(TableKey table, Direction direction) {
    final List<Relation> relations = new ArrayList<>();
    if (direction.followsOutgoing()) {
      relations.addAll(outgoing.getOrDefault(table, List.of()));
    }
    if (direction.followsIncoming()) {
      relations.addAll(incoming.getOrDefault(table, List.of()));
    }
    return relations;
  }

  /** 見つからなかったテーブル名に似た名前のテーブルを、DB・スキーマの絞り込みを外して探す */
  private List<TableEntry> suggestionsFor(TableReference reference) {
    if (reference.table().isBlank()) {
      return List.of();
    }
    return searchTables(SearchQuery.of(reference.table()), SearchScope.ALL, MAX_SUGGESTIONS)
        .hits()
        .stream()
        .map(TableHit::table)
        .toList();
  }
}
