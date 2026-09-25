package com.export_table_definition.domain.model.collection;

import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.value.TableKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部キーの繋がりからテーブルのまとまり（連結成分）を求めるクラス<br>
 * スキーマ単位のER図が1枚に収まらない場合に、外部キーで繋がったテーブルのまとまりごとに 図を分割し、1枚に収まる範囲でまとめ直すために利用する。
 * 入力がコレクション全体ではなくスキーマ単位の部分集合となるため、 {@link ForeignKeys}のメソッドではなく独立したクラスとしている
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
   * @return 連結成分ごとのまとまりのリスト
   */
  public static List<ForeignKeyGroup> connectedComponents(List<ForeignKeyEntity> foreignKeys) {
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
    final Comparator<ForeignKeyGroup> ordering =
        Comparator.comparingInt(ForeignKeyGroup::nodeCount)
            .reversed()
            .thenComparing(ForeignKeyGroups::firstKeyText);
    return componentsByRoot.values().stream().map(ForeignKeyGroup::of).sorted(ordering).toList();
  }

  /**
   * 連結成分を、1枚の図に収まる範囲でグループにまとめ直すメソッド<br>
   * 成分ごとに1ファイルとすると、2テーブルだけの極小の図が大量に生成されてしまうため、 ノード数の上限に収まる限り複数の成分を同じ図にまとめる（貪欲法）。
   * まとめられた成分同士は線で繋がっていないため、1枚に並んでも関連を誤読するおそれはない。 単独で上限を超える成分はそれ単独のグループとなり、当該グループは外部キー一覧にフォールバックする
   *
   * @param components 連結成分ごとのまとまりのリスト（ノード数の降順）
   * @param maxNodes 1つの図に描画するノード数の上限
   * @return グループごとにまとめ直したまとまりのリスト
   */
  public static List<ForeignKeyGroup> pack(List<ForeignKeyGroup> components, int maxNodes) {
    final List<List<ForeignKeyEntity>> groups = new ArrayList<>();
    final List<Integer> groupNodeCounts = new ArrayList<>();
    components.forEach(
        component -> {
          final int componentNodes = component.nodeCount();
          for (int i = 0; i < groups.size(); i++) {
            if (groupNodeCounts.get(i) + componentNodes <= maxNodes) {
              groups.get(i).addAll(component.foreignKeys());
              groupNodeCounts.set(i, groupNodeCounts.get(i) + componentNodes);
              return;
            }
          }
          groups.add(new ArrayList<>(component.foreignKeys()));
          groupNodeCounts.add(componentNodes);
        });
    return groups.stream().map(ForeignKeyGroup::of).toList();
  }

  /**
   * 連結成分の先頭テーブルを表す文字列を求めるメソッド<br>
   * ノード数が同数の場合の並び順を一意に定めるために利用する
   *
   * @param component 連結成分
   * @return 名称の辞書順で先頭となるテーブルの スキーマ.テーブル 形式の名称
   */
  private static String firstKeyText(ForeignKeyGroup component) {
    return component.nodes().stream()
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
