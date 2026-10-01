package com.export_table_definition.domain.model.schemaobject;

import java.util.List;
import java.util.stream.Stream;

/** 関数・プロシージャの一覧情報の集合を扱うクラス */
public final class Functions {

  private final List<FunctionEntity> list;

  private Functions(List<FunctionEntity> list) {
    this.list = List.copyOf(list);
  }

  /**
   * 関数・プロシージャ情報のリストからインスタンスを生成する静的ファクトリメソッド
   *
   * @param list 関数・プロシージャ情報のリスト（取得順）
   */
  public static Functions of(List<FunctionEntity> list) {
    return new Functions(list);
  }

  /**
   * 関数・プロシージャ情報のリストを取得するメソッド
   *
   * @return 関数・プロシージャ情報のリスト（取得順・変更不可）
   */
  public List<FunctionEntity> asList() {
    return list;
  }

  /** 関数・プロシージャが1件も存在しないか判定するメソッド */
  public boolean isEmpty() {
    return list.isEmpty();
  }

  /** 関数・プロシージャ情報のストリームを取得するメソッド */
  public Stream<FunctionEntity> stream() {
    return list.stream();
  }
}
