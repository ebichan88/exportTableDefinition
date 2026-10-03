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
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * スナップショットから読み込んだ全オブジェクトと、その検索・名前の解決・関連のたどりを担うクラス<br>
 * 被参照側の関連はスナップショットに保持されないため、組み立て時に全テーブルの関連から逆引きの索引を作る
 */
public final class SchemaCatalog {

  /** 名前が見つからないときに返す、似た名前の候補の上限 */
  private static final int MAX_SUGGESTIONS = 5;

  private static final String TABLE = "table";
  private static final String VIEW = "view";
  private static final String MATERIALIZED_VIEW = "materialized_view";

  private static final Comparator<ObjectKey> KEY_ORDER =
      Comparator.comparing(ObjectKey::database)
          .thenComparing(ObjectKey::schema)
          .thenComparing(ObjectKey::name);

  private static final Comparator<TableEntry> BY_KEY =
      Comparator.comparing(TableEntry::key, KEY_ORDER);

  private final List<DatabaseEntry> databases;
  private final List<TableEntry> tables;
  private final List<FunctionOverloads> functions;
  private final List<SequenceEntry> sequences;
  private final List<TypeEntry> types;
  private final Map<ObjectKey, TableEntry> tablesByKey;
  private final Map<ObjectKey, List<Relation>> outgoing;
  private final Map<ObjectKey, List<Relation>> incoming;

  private SchemaCatalog(
      List<DatabaseEntry> databases,
      List<TableEntry> tables,
      List<FunctionEntry> functions,
      List<SequenceEntry> sequences,
      List<TypeEntry> types) {
    this.databases = List.copyOf(databases);
    this.tables = List.copyOf(tables);
    this.functions =
        functions.stream()
            .collect(
                Collectors.groupingBy(FunctionEntry::key, LinkedHashMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> new FunctionOverloads(entry.getKey(), entry.getValue()))
            .toList();
    this.sequences = List.copyOf(sequences);
    this.types = List.copyOf(types);
    this.tablesByKey = new HashMap<>();
    this.outgoing = new HashMap<>();
    this.incoming = new HashMap<>();
    for (final TableEntry table : this.tables) {
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
   * スナップショットの内容から組み立てるメソッド
   *
   * @param databases 全DB。オブジェクトを持たないDBも含める
   * @param tables 全テーブル。キー（DB名・スキーマ名・テーブル名）が重複しないこと
   * @param functions 全関数・プロシージャ。オーバーロードはキーが重複する
   * @param sequences 全シーケンス
   * @param types 全ユーザー定義型
   */
  public static SchemaCatalog of(
      List<DatabaseEntry> databases,
      List<TableEntry> tables,
      List<FunctionEntry> functions,
      List<SequenceEntry> sequences,
      List<TypeEntry> types) {
    return new SchemaCatalog(databases, tables, functions, sequences, types);
  }

  /**
   * テーブルの一覧だけから組み立てるメソッド（DBMS種別は不明として扱う）
   *
   * @param tables 全テーブル。キー（DB名・スキーマ名・テーブル名）が重複しないこと
   */
  public static SchemaCatalog of(List<TableEntry> tables) {
    return of(
        tables.stream()
            .map(table -> table.key().database())
            .distinct()
            .map(name -> new DatabaseEntry(name, null))
            .toList(),
        tables,
        List.of(),
        List.of(),
        List.of());
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
   * スキーマごとのオブジェクトの数を返すメソッド
   *
   * @return DB名・スキーマ名の順。オブジェクトを1つも持たないスキーマは含まない
   */
  public List<SchemaSummary> schemas() {
    final Map<String, String> dbmsByDatabase = new HashMap<>();
    databases.forEach(database -> dbmsByDatabase.put(database.name(), database.dbms()));
    final Map<List<String>, SchemaCounter> counters =
        new TreeMap<>(
            Comparator.comparing((List<String> id) -> id.get(0)).thenComparing(id -> id.get(1)));
    for (final TableEntry table : tables) {
      final SchemaCounter counter = counterOf(counters, table.key());
      switch (table.type()) {
        case TABLE -> counter.tables++;
        case VIEW -> counter.views++;
        case MATERIALIZED_VIEW -> counter.materializedViews++;
        default -> {}
      }
    }
    functions.forEach(
        function -> counterOf(counters, function.key()).functions += function.overloads().size());
    sequences.forEach(sequence -> counterOf(counters, sequence.key()).sequences++);
    types.forEach(type -> counterOf(counters, type.key()).types++);
    return counters.entrySet().stream()
        .map(
            entry -> {
              final String database = entry.getKey().get(0);
              final SchemaCounter counter = entry.getValue();
              return new SchemaSummary(
                  database,
                  dbmsByDatabase.getOrDefault(database, ""),
                  entry.getKey().get(1),
                  counter.tables,
                  counter.views,
                  counter.materializedViews,
                  counter.functions,
                  counter.sequences,
                  counter.types);
            })
        .toList();
  }

  /**
   * テーブルを一覧にするメソッド
   *
   * @param type 区分（table/view/materialized_view）で絞り込む場合に指定する。空文字の場合は絞り込まない
   * @return DB名・スキーマ名・テーブル名の順
   */
  public List<TableEntry> listTables(SearchScope scope, String type) {
    return tables.stream()
        .filter(table -> scope.matches(table.key()))
        .filter(table -> type.isEmpty() || type.equals(table.type()))
        .sorted(BY_KEY)
        .toList();
  }

  /**
   * カラムを名前（物理名・論理名）で逆引きするメソッド
   *
   * @return 当てはまりの強い順（同じ強さの場合はDB名・スキーマ名・テーブル名・カラムの並び順）
   */
  public List<ColumnHit> findColumns(ColumnQuery query, SearchScope scope) {
    final List<ScoredColumn> found = new ArrayList<>();
    final List<TableEntry> inScope =
        tables.stream().filter(table -> scope.matches(table.key())).sorted(BY_KEY).toList();
    for (final TableEntry table : inScope) {
      for (final ColumnEntry column : table.columns()) {
        final int score = query.score(column);
        if (score > 0) {
          found.add(
              new ScoredColumn(score, new ColumnHit(table, column, referencesOf(table, column))));
        }
      }
    }
    return found.stream()
        .sorted(Comparator.comparingInt(ScoredColumn::score).reversed())
        .map(ScoredColumn::hit)
        .toList();
  }

  /**
   * 関数・プロシージャを一覧にするメソッド
   *
   * @return DB名・スキーマ名・関数名の順（オーバーロードはスナップショットの並び順）
   */
  public List<FunctionEntry> listFunctions(SearchScope scope, NameFilter filter) {
    return inOrder(functions, scope, filter).stream()
        .flatMap(function -> function.overloads().stream())
        .toList();
  }

  /**
   * シーケンスを一覧にするメソッド
   *
   * @return DB名・スキーマ名・シーケンス名の順
   */
  public List<SequenceEntry> listSequences(SearchScope scope, NameFilter filter) {
    return inOrder(sequences, scope, filter);
  }

  /**
   * ユーザー定義型を一覧にするメソッド
   *
   * @param category 種別（ENUM等）で絞り込む場合に指定する。空文字の場合は絞り込まない
   * @return DB名・スキーマ名・型名の順
   */
  public List<TypeEntry> listTypes(SearchScope scope, NameFilter filter, String category) {
    return inOrder(types, scope, filter).stream()
        .filter(type -> category.isEmpty() || category.equalsIgnoreCase(type.category()))
        .toList();
  }

  /**
   * テーブルのトリガーを一覧にするメソッド
   *
   * @return DB名・スキーマ名・テーブル名の順（同じテーブルのトリガーはスナップショットの並び順）
   */
  public List<TableTrigger> listTriggers(SearchScope scope) {
    return tables.stream()
        .filter(table -> scope.matches(table.key()))
        .sorted(BY_KEY)
        .flatMap(
            table -> table.triggers().stream().map(trigger -> new TableTrigger(table, trigger)))
        .toList();
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
  public Lookup<TableEntry> lookupTable(ObjectReference reference) {
    return lookup(tables, reference, this::tableSuggestionsFor);
  }

  /**
   * 名前で指定された関数・プロシージャを解決するメソッド（オーバーロードはまとめて1つとみなす）<br>
   * 名前は大文字小文字を区別せず完全一致で比べる。見つからない場合は、名前の一部に指定を含むものを候補として返す
   */
  public Lookup<FunctionOverloads> lookupFunction(ObjectReference reference) {
    return lookup(functions, reference, ref -> suggestionsFor(functions, ref));
  }

  /**
   * 名前で指定されたシーケンスを解決するメソッド
   *
   * @see #lookupFunction(ObjectReference) 名前の比べ方・候補の求め方
   */
  public Lookup<SequenceEntry> lookupSequence(ObjectReference reference) {
    return lookup(sequences, reference, ref -> suggestionsFor(sequences, ref));
  }

  /**
   * 名前で指定されたユーザー定義型を解決するメソッド
   *
   * @see #lookupFunction(ObjectReference) 名前の比べ方・候補の求め方
   */
  public Lookup<TypeEntry> lookupType(ObjectReference reference) {
    return lookup(types, reference, ref -> suggestionsFor(types, ref));
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
          final ObjectKey neighbor =
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
  private List<Relation> relationsOf(ObjectKey table, Direction direction) {
    final List<Relation> relations = new ArrayList<>();
    if (direction.followsOutgoing()) {
      relations.addAll(outgoing.getOrDefault(table, List.of()));
    }
    if (direction.followsIncoming()) {
      relations.addAll(incoming.getOrDefault(table, List.of()));
    }
    return relations;
  }

  /** カラムが外部キー・論理リレーションで参照している先を、関連の定義順に返す */
  private List<ColumnHit.ColumnReference> referencesOf(TableEntry table, ColumnEntry column) {
    final List<ColumnHit.ColumnReference> references = new ArrayList<>();
    for (final Relation relation : outgoing.getOrDefault(table.key(), List.of())) {
      final int position = relation.fromColumns().indexOf(column.name());
      if (position >= 0 && position < relation.toColumns().size()) {
        references.add(
            new ColumnHit.ColumnReference(
                relation.to(), relation.toColumns().get(position), relation.kind()));
      }
    }
    return references;
  }

  /**
   * 名前でオブジェクトを解決する
   *
   * @param suggestions 見つからなかった場合に、名前の似たオブジェクトを求める処理
   */
  private static <E extends SchemaObject> Lookup<E> lookup(
      List<E> objects, ObjectReference reference, Function<ObjectReference, List<E>> suggestions) {
    final List<E> matched =
        objects.stream()
            .filter(object -> reference.matches(object.key()))
            .sorted(Comparator.comparing(SchemaObject::key, KEY_ORDER))
            .toList();
    if (matched.size() == 1) {
      return new Lookup.Found<>(matched.get(0));
    }
    if (matched.size() > 1) {
      return new Lookup.Ambiguous<>(matched);
    }
    return new Lookup.NotFound<>(suggestions.apply(reference));
  }

  private static SchemaCounter counterOf(Map<List<String>, SchemaCounter> counters, ObjectKey key) {
    return counters.computeIfAbsent(
        List.of(key.database(), key.schema()), id -> new SchemaCounter());
  }

  private static <E extends SchemaObject> List<E> inOrder(
      List<E> objects, SearchScope scope, NameFilter filter) {
    return objects.stream()
        .filter(object -> scope.matches(object.key()) && filter.matches(object.key()))
        .sorted(Comparator.comparing(SchemaObject::key, KEY_ORDER))
        .toList();
  }

  /** 見つからなかった名前を一部に含むオブジェクトを、DB・スキーマの絞り込みを外して探す */
  private static <E extends SchemaObject> List<E> suggestionsFor(
      List<E> objects, ObjectReference reference) {
    if (reference.name().isBlank()) {
      return List.of();
    }
    return inOrder(objects, SearchScope.ALL, NameFilter.of(reference.name())).stream()
        .limit(MAX_SUGGESTIONS)
        .toList();
  }

  /** 見つからなかったテーブル名に似た名前のテーブルを、DB・スキーマの絞り込みを外して探す */
  private List<TableEntry> tableSuggestionsFor(ObjectReference reference) {
    if (reference.name().isBlank()) {
      return List.of();
    }
    return searchTables(SearchQuery.of(reference.name()), SearchScope.ALL, MAX_SUGGESTIONS)
        .hits()
        .stream()
        .map(TableHit::table)
        .toList();
  }

  /** 逆引きで当てはまったカラムと、当てはまりの強さ */
  private record ScoredColumn(int score, ColumnHit hit) {}

  /** スキーマごとのオブジェクトの数の集計 */
  private static final class SchemaCounter {
    private int tables;
    private int views;
    private int materializedViews;
    private int functions;
    private int sequences;
    private int types;
  }
}
