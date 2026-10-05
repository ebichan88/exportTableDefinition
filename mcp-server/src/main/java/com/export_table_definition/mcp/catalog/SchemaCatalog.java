package com.export_table_definition.mcp.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

/** スナップショットから読み込んだ全オブジェクトと、その検索・名前の解決・関連のたどりを担うクラス */
public final class SchemaCatalog {

  /** 名前が見つからないときに返す、似た名前の候補の上限 */
  private static final int MAX_SUGGESTIONS = 5;

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
  private final List<ViewpointEntry> viewpoints;
  private final RelationGraph relations;

  private SchemaCatalog(
      List<DatabaseEntry> databases,
      List<TableEntry> tables,
      List<FunctionOverloads> functions,
      List<SequenceEntry> sequences,
      List<TypeEntry> types,
      List<ViewpointEntry> viewpoints) {
    this.databases = List.copyOf(databases);
    this.tables = List.copyOf(tables);
    this.functions = List.copyOf(functions);
    this.sequences = List.copyOf(sequences);
    this.types = List.copyOf(types);
    this.viewpoints = List.copyOf(viewpoints);
    this.relations = new RelationGraph(this.tables);
  }

  /**
   * スナップショットの内容から組み立てるメソッド（観点の参考情報を含む）
   *
   * @param databases 全DB。オブジェクトを持たないDBも含める
   * @param tables 全テーブル。キー（DB名・スキーマ名・テーブル名）が重複しないこと
   * @param functions 全関数・プロシージャ。オーバーロードはキーが重複する
   * @param sequences 全シーケンス
   * @param types 全ユーザー定義型
   * @param viewpoints 観点の参考情報（宣言順）
   */
  public static SchemaCatalog of(
      List<DatabaseEntry> databases,
      List<TableEntry> tables,
      List<FunctionEntry> functions,
      List<SequenceEntry> sequences,
      List<TypeEntry> types,
      List<ViewpointEntry> viewpoints) {
    final List<FunctionOverloads> overloads =
        functions.stream()
            .collect(
                Collectors.groupingBy(FunctionEntry::key, LinkedHashMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> new FunctionOverloads(entry.getKey(), entry.getValue()))
            .toList();
    return new SchemaCatalog(databases, tables, overloads, sequences, types, viewpoints);
  }

  /**
   * スナップショットの内容から組み立てるメソッド（観点の参考情報を持たない）
   *
   * @see #of(List, List, List, List, List, List)
   */
  public static SchemaCatalog of(
      List<DatabaseEntry> databases,
      List<TableEntry> tables,
      List<FunctionEntry> functions,
      List<SequenceEntry> sequences,
      List<TypeEntry> types) {
    return of(databases, tables, functions, sequences, types, List.of());
  }

  /**
   * 観点の参考情報を追加した新しいインスタンスを返すメソッド<br>
   * スナップショットとは別の読み込み元（参考情報）の内容を、組み立て後に合成するために用いる
   */
  public SchemaCatalog withViewpoints(List<ViewpointEntry> viewpoints) {
    return new SchemaCatalog(databases, tables, functions, sequences, types, viewpoints);
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
    final Map<SchemaId, SchemaCounter> counters =
        new TreeMap<>(Comparator.comparing(SchemaId::database).thenComparing(SchemaId::schema));
    for (final TableEntry table : tables) {
      final SchemaCounter counter = counterOf(counters, table.key());
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
              final SchemaCounter counter = entry.getValue();
              return new SchemaSummary(
                  database,
                  dbmsByDatabase.getOrDefault(database, ""),
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

  /**
   * テーブルを一覧にするメソッド
   *
   * @return DB名・スキーマ名・テーブル名の順
   */
  public List<TableEntry> listTables(TableFilter filter) {
    return tables.stream().filter(filter::matches).sorted(BY_KEY).toList();
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
   * テーブルを検索するメソッド<br>
   * 絞り込みは、総数・{@code limit}に正しく反映されるよう検索・並べ替えの前に適用する
   *
   * @param limit 返す件数の上限（1以上）
   * @return 一致の強い順（同点の場合はDB名・スキーマ名・テーブル名の順）の検索結果
   */
  public SearchResult searchTables(SearchQuery query, TableFilter filter, int limit) {
    final List<TableHit> hits =
        tables.stream()
            .filter(filter::matches)
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
   * 観点を一覧にするメソッド<br>
   * 観点はスキーマを持たないため、{@code scope}の{@code schema}は無視する
   *
   * @return 宣言順
   */
  public List<ViewpointEntry> listViewpoints(SearchScope scope) {
    return viewpoints.stream()
        .filter(viewpoint -> scope.matchesDatabase(viewpoint.database()))
        .toList();
  }

  /**
   * 識別子で観点を解決するメソッド（識別子は大文字小文字を区別しない）<br>
   * 観点はスキーマを持たないため、{@code scope}の{@code schema}は無視する
   */
  public Optional<ViewpointEntry> findViewpoint(SearchScope scope, String id) {
    return viewpoints.stream()
        .filter(viewpoint -> scope.matchesDatabase(viewpoint.database()))
        .filter(viewpoint -> viewpoint.id().equalsIgnoreCase(id))
        .findFirst();
  }

  /**
   * テーブルが所属する観点を求めるメソッド<br>
   * 1つのテーブルが複数の観点に所属することがある
   *
   * @return 宣言順。所属する観点が無い場合は空のリスト
   */
  public List<ViewpointEntry> viewpointsOf(TableEntry table) {
    return viewpoints.stream().filter(viewpoint -> viewpoint.contains(table.key())).toList();
  }

  /**
   * 名前で指定されたテーブルを解決するメソッド<br>
   * テーブル名は大文字小文字を区別せず完全一致で比べる。見つからない場合は、テーブル名を検索語にした検索の上位を候補として返す
   */
  public Lookup<TableEntry> lookupTable(ObjectReference reference) {
    return lookup(tables, reference, this::tableSuggestionsFor);
  }

  /**
   * 関数を実行するトリガーを求めるメソッド
   *
   * @return DB名・スキーマ名・テーブル名の順
   */
  public List<TableTrigger> triggersCalling(FunctionOverloads function) {
    return listTriggers(SearchScope.ALL).stream()
        .filter(
            found ->
                SqlNames.sameObject(
                    SqlNames.functionOfTrigger(found.trigger().function(), found.table().key()),
                    function.key()))
        .toList();
  }

  /**
   * シーケンスを採番（デフォルト値の{@code nextval}）に使うカラムを求めるメソッド
   *
   * @return DB名・スキーマ名・テーブル名・カラムの並び順
   */
  public List<TableColumn> columnsUsingSequence(SequenceEntry sequence) {
    return columnsWhere(
        (table, column) ->
            SqlNames.sequenceOfDefault(column.defaultValue(), table.key())
                .filter(key -> SqlNames.sameObject(key, sequence.key()))
                .isPresent());
  }

  /**
   * ユーザー定義型をカラムの型（配列を含む）に使うカラムを求めるメソッド
   *
   * @return DB名・スキーマ名・テーブル名・カラムの並び順
   */
  public List<TableColumn> columnsUsingType(TypeEntry type) {
    return columnsWhere(
        (table, column) ->
            !column.type().isEmpty()
                && SqlNames.sameObject(
                    SqlNames.typeOfColumn(column.type(), table.key()), type.key()));
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
    return relations.relatedTables(start, depth, direction);
  }

  /**
   * 2つのテーブルをつなぐ最短の経路を探すメソッド<br>
   * 外部キー・論理リレーションを向きを問わずたどる。スナップショットに含まれないテーブルは経由しない
   *
   * @param maxLength 経路の関連の数の上限（1以上）。これより長い経路は探さない
   * @param limit 返す経路の数の上限（1以上）
   */
  public JoinPaths joinPaths(TableEntry from, TableEntry to, int maxLength, int limit) {
    return relations.joinPaths(from, to, maxLength, limit);
  }

  /** カラムが外部キー・論理リレーションで参照している先を、関連の定義順に返す */
  private List<ColumnHit.ColumnReference> referencesOf(TableEntry table, ColumnEntry column) {
    final List<ColumnHit.ColumnReference> references = new ArrayList<>();
    for (final Relation relation : relations.outgoing(table.key())) {
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

  private static SchemaCounter counterOf(Map<SchemaId, SchemaCounter> counters, ObjectKey key) {
    return counters.computeIfAbsent(
        new SchemaId(key.database(), key.schema()), id -> new SchemaCounter());
  }

  private List<TableColumn> columnsWhere(BiPredicate<TableEntry, ColumnEntry> condition) {
    return tables.stream()
        .sorted(BY_KEY)
        .flatMap(
            table ->
                table.columns().stream()
                    .filter(column -> condition.test(table, column))
                    .map(column -> new TableColumn(table, column)))
        .toList();
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
    return searchTables(SearchQuery.of(reference.name()), TableFilter.ALL, MAX_SUGGESTIONS)
        .hits()
        .stream()
        .map(TableHit::table)
        .toList();
  }

  /** スキーマごとの集計のキー */
  private record SchemaId(String database, String schema) {}

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
