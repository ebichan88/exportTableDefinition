package com.export_table_definition.domain.model.viewpoint;

import com.export_table_definition.domain.model.table.TableEntity;
import java.util.List;

/**
 * 観点（{@link Viewpoint}）の集合を扱うクラス<br>
 * サイドカーYAMLで宣言された順に保持する（観点一覧・テーブル定義書の「所属する観点」の掲載順）
 */
public final class Viewpoints {

  private static final Viewpoints EMPTY = new Viewpoints(List.of());

  private final List<Viewpoint> list;

  private Viewpoints(List<Viewpoint> list) {
    this.list = List.copyOf(list);
  }

  /**
   * 観点のリストからインスタンスを生成する静的ファクトリメソッド
   *
   * @param list 観点のリスト（宣言順）
   * @return Viewpointsインスタンス
   */
  public static Viewpoints of(List<Viewpoint> list) {
    return new Viewpoints(list);
  }

  /**
   * 観点を持たない空のインスタンスを取得する静的ファクトリメソッド<br>
   * サイドカー機能が無効（未設定）の場合や、観点を宣言していない場合に利用する
   *
   * @return 空のViewpointsインスタンス
   */
  public static Viewpoints empty() {
    return EMPTY;
  }

  /**
   * 観点のリストを取得するメソッド
   *
   * @return 観点のリスト（宣言順）
   */
  public List<Viewpoint> asList() {
    return list;
  }

  /**
   * 観点が1件も無いか判定するメソッド
   *
   * @return 観点が無い場合はtrue
   */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /**
   * 指定されたテーブルが所属する観点を取得するメソッド<br>
   * テーブル定義書から観点ページへ戻る導線（「所属する観点」）に用いる
   *
   * @param table テーブル
   * @return 当該テーブルが所属する観点のリスト（宣言順）。所属する観点が無い場合は空のリスト
   */
  public List<Viewpoint> of(TableEntity table) {
    return list.stream().filter(viewpoint -> viewpoint.contains(table)).toList();
  }
}
