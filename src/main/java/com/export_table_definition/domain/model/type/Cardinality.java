package com.export_table_definition.domain.model.type;

/**
 * 外部キーによる関連の多重度を表す列挙型<br>
 * 多重度は外部キーそのものではなく、参照元（子）テーブルの外部キー列に付与された制約から機械的に決まる。
 * <ul>
 * <li>親側（1件の子に対する親の件数）：外部キー列がすべてNOT NULLなら「ちょうど1」、
 * NULLを許容するなら「0または1」</li>
 * <li>子側（1件の親に対する子の件数）：外部キー列が一意制約・一意索引で覆われているなら「0または1」、
 * 覆われていないなら「0以上」</li>
 * </ul>
 * 「親1件につき子が1件以上存在すること」はテーブル定義では表現できないため、子側が「1以上」となることはない
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public enum Cardinality {
    /** 1対多。外部キー列はNOT NULLで一意ではない */
    ONE_TO_MANY("||--o{", "1対多"),
    /** 0..1対多。外部キー列はNULLを許容し一意ではない */
    OPTIONAL_ONE_TO_MANY("|o--o{", "0..1対多"),
    /** 1対1。外部キー列はNOT NULLかつ一意 */
    ONE_TO_ONE("||--o|", "1対1"),
    /** 0..1対1。外部キー列はNULLを許容し一意 */
    OPTIONAL_ONE_TO_ONE("|o--o|", "0..1対1");

    /** Mermaidの関連線表記 */
    private final String notation;
    /** 一覧表などに掲載する日本語のラベル */
    private final String label;

    /**
     * コンストラクタ
     *
     * @param notation Mermaidの関連線表記
     * @param label    日本語のラベル
     */
    Cardinality(String notation, String label) {
        this.notation = notation;
        this.label = label;
    }

    /**
     * Mermaidの関連線表記を返却するメソッド<br>
     * 「参照先（親） {@code 表記} 参照元（子）」の並びで用いる
     *
     * @return Mermaidの関連線表記
     */
    public String getNotation() {
        return notation;
    }

    /**
     * 日本語のラベルを返却するメソッド
     *
     * @return 日本語のラベル
     */
    public String getLabel() {
        return label;
    }

    /**
     * 参照元（子）テーブルの外部キー列に付与された制約から多重度を判定するメソッド
     *
     * @param unique    外部キー列が一意制約・一意索引で覆われている場合はtrue
     * @param mandatory 外部キー列がすべてNOT NULLの場合はtrue
     * @return 多重度
     */
    public static Cardinality of(boolean unique, boolean mandatory) {
        if (unique) {
            return mandatory ? ONE_TO_ONE : OPTIONAL_ONE_TO_ONE;
        }
        return mandatory ? ONE_TO_MANY : OPTIONAL_ONE_TO_MANY;
    }
}
