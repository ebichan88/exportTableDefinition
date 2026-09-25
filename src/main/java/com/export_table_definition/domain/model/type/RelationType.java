package com.export_table_definition.domain.model.type;

/**
 * テーブル間の関連の由来を表す列挙型<br>
 * DBに実在する外部キー制約による関連（物理）と、DBには制約が存在せずサイドカーYAMLで
 * 宣言された関連（論理）を区別する。<br>
 * アプリケーション側で参照整合性を担保している等の理由で外部キー制約を張らないDBでは、
 * カタログから読み取れる関連だけではER図がほとんど空になるため、論理の関連を補って描画する。
 * ただし読み手が「DBに制約がある」と誤読しないよう、掲載セクションとER図の線種の双方で区別する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public enum RelationType {
    /** DBに実在する外部キー制約による関連。Mermaidでは実線で描画する */
    PHYSICAL("--", "物理"),
    /** サイドカーYAMLで宣言された論理的な関連。Mermaidでは破線（非識別関連）で描画する */
    LOGICAL("..", "論理");

    /** Mermaidの関連線の線種（実線／破線） */
    private final String lineNotation;
    /** 一覧表などに掲載する日本語のラベル */
    private final String label;

    /**
     * コンストラクタ
     *
     * @param lineNotation Mermaidの関連線の線種
     * @param label        日本語のラベル
     */
    RelationType(String lineNotation, String label) {
        this.lineNotation = lineNotation;
        this.label = label;
    }

    /**
     * Mermaidの関連線の線種を返却するメソッド<br>
     * 多重度の端点表記と組み合わせて用いる（例: {@code "||" + "--" + "o{"}）
     *
     * @return Mermaidの関連線の線種
     */
    public String getLineNotation() {
        return lineNotation;
    }

    /**
     * 日本語のラベルを返却するメソッド
     *
     * @return 日本語のラベル
     */
    public String getLabel() {
        return label;
    }
}
