package com.export_table_definition.domain.service.writer.template;

/**
 * Mermaid記法の出力に必要な文字列変換を扱う共通ユーティリティクラス<br>
 * テーブル単位のER図（{@link TableDefinitionTemplates}）とスキーマ単位のER図（{@link ErDiagramTemplates}）の
 * 両方から利用する
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
final class MermaidSupport {

    /**
     * コンストラクタ（インスタンス化不可）
     */
    private MermaidSupport() {
    }

    /**
     * Mermaid記法のエンティティ識別子を生成するメソッド<br>
     * スキーマ名を含めることで、同名テーブルが複数スキーマに存在する場合の識別子衝突を避ける
     *
     * @param schemaName        スキーマ名
     * @param physicalTableName 物理テーブル名
     * @return サニタイズ済みのエンティティ識別子
     */
    static String mermaidId(String schemaName, String physicalTableName) {
        return sanitizeIdentifier(schemaName + "_" + physicalTableName);
    }

    /**
     * Mermaid記法で識別子として利用できない文字をアンダースコアに置換するメソッド
     *
     * @param value 変換対象の文字列
     * @return サニタイズ済みの文字列
     */
    static String sanitizeIdentifier(String value) {
        return value.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /**
     * データ型からMermaid記法の属性型として利用できる文字列を生成するメソッド<br>
     * 桁数・精度を表す括弧部分を除去し、残った空白をアンダースコアに置換する
     *
     * @param columnType データ型
     * @return サニタイズ済みのデータ型文字列
     */
    static String sanitizeType(String columnType) {
        return columnType.replaceAll("\\(.*\\)", "").trim().replaceAll("[^A-Za-z0-9_]+", "_");
    }
}
