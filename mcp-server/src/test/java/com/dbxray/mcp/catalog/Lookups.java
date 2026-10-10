package com.dbxray.mcp.catalog;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

/** テストで名前の解決結果（{@link Lookup}）を取り出す補助 */
public final class Lookups {

  private Lookups() {}

  /** 1つに定まったオブジェクト。定まらなかった場合はテストを失敗にする */
  public static <E extends SchemaObject> E found(Lookup<E> lookup) {
    if (lookup instanceof Lookup.Found<E> found) {
      return found.value();
    }
    return fail("1つに定まりませんでした: " + lookup);
  }

  /** 同名で1つに定まらなかった候補 */
  public static <E extends SchemaObject> List<E> candidates(Lookup<E> lookup) {
    if (lookup instanceof Lookup.Ambiguous<E> ambiguous) {
      return ambiguous.candidates();
    }
    return fail("同名の候補になりませんでした: " + lookup);
  }

  /** 見つからなかった場合の、名前の似た候補 */
  public static <E extends SchemaObject> List<E> suggestions(Lookup<E> lookup) {
    if (lookup instanceof Lookup.NotFound<E> notFound) {
      return notFound.suggestions();
    }
    return fail("見つからない結果になりませんでした: " + lookup);
  }
}
