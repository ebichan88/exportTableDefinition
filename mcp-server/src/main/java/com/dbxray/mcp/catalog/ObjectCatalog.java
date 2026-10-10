package com.dbxray.mcp.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 1種類のオブジェクト（関数・シーケンス・ユーザー定義型）の一覧と名前の解決を担うクラス<br>
 * オブジェクト間の相互参照（関数を実行するトリガー等）はテーブルを走査するため、{@link TableCatalog}が担う
 *
 * @param <E> オブジェクトの種類
 */
public final class ObjectCatalog<E extends SchemaObject> {

  /** 名前が見つからないときに返す、似た名前の候補の上限 */
  static final int MAX_SUGGESTIONS = 5;

  private final List<E> objects;

  ObjectCatalog(List<E> objects) {
    this.objects = List.copyOf(objects);
  }

  /**
   * 全オブジェクトを返すメソッド
   *
   * @return 組み立て時に渡された順
   */
  public List<E> all() {
    return objects;
  }

  /**
   * オブジェクトを一覧にするメソッド
   *
   * @return DB名・スキーマ名・名前の順
   */
  public List<E> list(SearchScope scope, NameFilter filter) {
    return inOrder(objects, scope, filter);
  }

  /**
   * 名前で指定されたオブジェクトを解決するメソッド<br>
   * 名前は大文字小文字を区別せず完全一致で比べる。見つからない場合は、名前の一部に指定を含むものを、DB・スキーマの絞り込みを外して候補として返す
   */
  public Lookup<E> lookup(ObjectReference reference) {
    return lookup(objects, reference, this::suggestionsFor);
  }

  /**
   * 名前でオブジェクトを解決する
   *
   * @param suggestions 見つからなかった場合に、名前の似たオブジェクトを求める処理
   */
  static <E extends SchemaObject> Lookup<E> lookup(
      List<E> objects, ObjectReference reference, Function<ObjectReference, List<E>> suggestions) {
    final List<E> matched =
        objects.stream()
            .filter(object -> reference.matches(object.key()))
            .sorted(Comparator.comparing(SchemaObject::key, ObjectKey.ORDER))
            .toList();
    if (matched.size() == 1) {
      return new Lookup.Found<>(matched.get(0));
    }
    if (matched.size() > 1) {
      return new Lookup.Ambiguous<>(matched);
    }
    return new Lookup.NotFound<>(suggestions.apply(reference));
  }

  private static <E extends SchemaObject> List<E> inOrder(
      List<E> objects, SearchScope scope, NameFilter filter) {
    return objects.stream()
        .filter(object -> scope.matches(object.key()) && filter.matches(object.key()))
        .sorted(Comparator.comparing(SchemaObject::key, ObjectKey.ORDER))
        .toList();
  }

  private List<E> suggestionsFor(ObjectReference reference) {
    if (reference.name().isBlank()) {
      return List.of();
    }
    return inOrder(objects, SearchScope.ALL, NameFilter.of(reference.name())).stream()
        .limit(MAX_SUGGESTIONS)
        .toList();
  }
}
