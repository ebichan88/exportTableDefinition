package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;

import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.table.TableKey;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mermaid記法の出力に必要な文字列変換を扱う共通ユーティリティクラス<br>
 * テーブル単位のER図（{@link TableDefinitionTemplates}）とスキーマ単位のER図（{@link ErDiagramTemplates}）の 両方から利用する
 */
final class MermaidSupport {

  private MermaidSupport() {}

  /**
   * Mermaid記法のエンティティ識別子を生成するメソッド<br>
   * スキーマ名を含めることで、同名テーブルが複数スキーマに存在する場合の識別子衝突を避ける
   *
   * @return サニタイズ済みのエンティティ識別子
   */
  static String mermaidId(String schemaName, String physicalTableName) {
    return sanitizeIdentifier(schemaName + "_" + physicalTableName);
  }

  /**
   * 図に描画するノードの表示ラベルを決めるメソッド<br>
   * 識別子（スキーマ名込み）とは別に、Mermaidのエンティティ別名構文で表示名を差し替える。 通常はテーブル名のみを表示するが、同じ図内に同名テーブルが複数スキーマにまたがって存在する
   * 場合は見分けが付かなくなるため、その場合だけ「スキーマ名.テーブル名」の完全修飾名にする
   *
   * @param nodes 図に描画するノードのテーブルキー（重複無し）
   * @return テーブルキーごとの表示ラベル
   */
  static Map<TableKey, String> assignLabels(Collection<TableKey> nodes) {
    final Map<String, Long> tableNameCounts =
        nodes.stream().collect(Collectors.groupingBy(TableKey::table, Collectors.counting()));
    final Map<TableKey, String> labels = new LinkedHashMap<>();
    nodes.forEach(
        key ->
            labels.put(
                key, tableNameCounts.get(key.table()) > 1 ? key.qualifiedName() : key.table()));
    return labels;
  }

  /**
   * エンティティ別名の宣言1行分を生成するメソッド<br>
   * 識別子ごとに図内で1回宣言すれば、以降その識別子が登場する箇所（属性ブロック・関係線の両方）に別名が適用される
   */
  static String aliasLine(String id, String label) {
    return "    " + id + "[\"" + label + "\"]" + LINE_SEPARATOR;
  }

  /**
   * 関係線1本分の行を生成するメソッド<br>
   * 参照先（親）→ 参照元（子）の向きで描画し、線種（実線／破線）と多重度は外部キーの由来・多重度から組み立てる
   *
   * @param parentId 参照先（親）のエンティティ識別子
   * @param fk 外部キーまたは論理リレーション
   * @param childId 参照元（子）のエンティティ識別子
   * @return 関係線1本分の行（末尾の改行を含む）
   */
  static String relationLine(String parentId, ForeignKeyEntity fk, String childId) {
    return "    "
        + parentId
        + ' '
        + fk.cardinality().getNotation(fk.relationType())
        + ' '
        + childId
        + " : \""
        + fk.foreignkeyName()
        + '"'
        + LINE_SEPARATOR;
  }

  /** Mermaid記法で識別子として利用できない文字をアンダースコアに置換するメソッド */
  static String sanitizeIdentifier(String value) {
    return value.replaceAll("[^A-Za-z0-9_]", "_");
  }

  /**
   * データ型からMermaid記法の属性型として利用できる文字列を生成するメソッド<br>
   * 桁数・精度を表す括弧部分を除去し、残った空白をアンダースコアに置換する
   *
   * @return サニタイズ済みのデータ型文字列
   */
  static String sanitizeType(String columnType) {
    return columnType.replaceAll("\\(.*\\)", "").trim().replaceAll("[^A-Za-z0-9_]+", "_");
  }
}
