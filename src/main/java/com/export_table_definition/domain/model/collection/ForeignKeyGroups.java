package com.export_table_definition.domain.model.collection;

import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 外部キーの繋がりからテーブルのまとまり（連結成分）を求めるクラス<br>
 * スキーマ単位のER図が1枚に収まらない場合に、外部キーで繋がったテーブルのまとまりごとに 図を分割するために利用する。 入力がコレクション全体ではなくスキーマ単位の部分集合となるため、
 * {@link ForeignKeys}のメソッドではなく独立したクラスとしている
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public final class ForeignKeyGroups {

  /** コンストラクタ（インスタンス化不可） */
  private ForeignKeyGroups() {}

  /**
   * 外部キーを連結成分ごとに仕分けるメソッド<br>
   * 外部キーの両端のテーブルを同じまとまりとして併合するため、 {@code a → b}と{@code b → c}は1つの連結成分になる。
   * 自己参照・スキーマ跨ぎの外部キーも他と同様に扱う（スキーマを跨ぐ成分は複数スキーマにまたがる）<br>
   * 出力順は、規模の大きいまとまりから確認できるようノード数の降順とし、 同数の場合は再実行しても同じ結果になるよう先頭テーブルキーの昇順とする
   *
   * @param foreignKeys 外部キー情報のリスト
   * @return 連結成分ごとに仕分けた外部キーのリスト
   */
  public static List<List<ForeignKeyEntity>> connectedComponents(
      List<ForeignKeyEntity> foreignKeys) {
    if (foreignKeys.isEmpty()) {
      return List.of();
    }
    final Map<TableKey, TableKey> parents = new HashMap<>();
    foreignKeys.forEach(fk -> union(parents, fk.tableKey(), fk.referenceTableKey()));
    // 代表テーブルをキーとして外部キーを仕分ける
    final Map<TableKey, List<ForeignKeyEntity>> componentsByRoot = new LinkedHashMap<>();
    foreignKeys.forEach(
        fk ->
            componentsByRoot
                .computeIfAbsent(find(parents, fk.tableKey()), k -> new ArrayList<>())
                .add(fk));
    final Comparator<List<ForeignKeyEntity>> ordering =
        Comparator.comparingInt(ForeignKeyGroups::nodeCount)
            .reversed()
            .thenComparing(ForeignKeyGroups::firstKeyText);
    return componentsByRoot.values().stream().sorted(ordering).map(List::copyOf).toList();
  }

  /**
   * 連結成分に含まれるテーブル（ノード）の数を数えるメソッド
   *
   * @param foreignKeys 連結成分に属する外部キーのリスト
   * @return テーブル数
   */
  public static int nodeCount(List<ForeignKeyEntity> foreignKeys) {
    return nodeKeys(foreignKeys).size();
  }

  /**
   * 連結成分の中で最も多くの外部キーが接続するテーブルを求めるメソッド<br>
   * どのまとまりなのかを識別する手がかりとして一覧に掲載する
   *
   * @param foreignKeys 連結成分に属する外部キーのリスト
   * @return 最も多くの外部キーが接続するテーブル。同数の場合はスキーマ名・テーブル名順で先頭のもの
   */
  public static TableKey mainTable(List<ForeignKeyEntity> foreignKeys) {
    final Map<TableKey, Integer> degrees = new HashMap<>();
    foreignKeys.forEach(
        fk -> {
          degrees.merge(fk.tableKey(), 1, Integer::sum);
          degrees.merge(fk.referenceTableKey(), 1, Integer::sum);
        });
    return degrees.entrySet().stream()
        .sorted(
            Map.Entry.<TableKey, Integer>comparingByValue()
                .reversed()
                .thenComparing(entry -> entry.getKey().schema())
                .thenComparing(entry -> entry.getKey().table()))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  /**
   * 連結成分に含まれるテーブルキーを収集するメソッド
   *
   * @param foreignKeys 外部キーのリスト
   * @return テーブルキーの集合
   */
  private static Set<TableKey> nodeKeys(List<ForeignKeyEntity> foreignKeys) {
    final Set<TableKey> keys = new HashSet<>();
    foreignKeys.forEach(
        fk -> {
          keys.add(fk.tableKey());
          keys.add(fk.referenceTableKey());
        });
    return keys;
  }

  /**
   * 連結成分の先頭テーブルを表す文字列を求めるメソッド<br>
   * ノード数が同数の場合の並び順を一意に定めるために利用する
   *
   * @param foreignKeys 連結成分に属する外部キーのリスト
   * @return スキーマ名・テーブル名順で先頭となるテーブルの スキーマ.テーブル 形式の名称
   */
  private static String firstKeyText(List<ForeignKeyEntity> foreignKeys) {
    return nodeKeys(foreignKeys).stream()
        .map(key -> key.schema() + "." + key.table())
        .min(Comparator.naturalOrder())
        .orElseThrow();
  }

  /**
   * 2つのテーブルを同じまとまりとして併合するメソッド（Union-Find）
   *
   * @param parents 各テーブルの代表テーブルを保持するマップ
   * @param left 併合するテーブル
   * @param right 併合するテーブル
   */
  private static void union(Map<TableKey, TableKey> parents, TableKey left, TableKey right) {
    final TableKey leftRoot = find(parents, left);
    final TableKey rightRoot = find(parents, right);
    if (!leftRoot.equals(rightRoot)) {
      parents.put(leftRoot, rightRoot);
    }
  }

  /**
   * テーブルが属するまとまりの代表テーブルを求めるメソッド（Union-Find）<br>
   * 探索の過程で経路を圧縮し、繰り返し呼び出しても深い探索にならないようにする
   *
   * @param parents 各テーブルの代表テーブルを保持するマップ
   * @param key 対象のテーブル
   * @return 代表テーブル
   */
  private static TableKey find(Map<TableKey, TableKey> parents, TableKey key) {
    TableKey root = key;
    while (!root.equals(parents.computeIfAbsent(root, k -> k))) {
      root = parents.get(root);
    }
    // 経路圧縮
    TableKey current = key;
    while (!current.equals(root)) {
      final TableKey next = parents.get(current);
      parents.put(current, root);
      current = next;
    }
    return root;
  }
}
