package com.export_table_definition.domain.service.writer.template;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.value.TableKey;

/**
 * スキーマ単位のER図（全体ER図）書き込みに利用するMarkdownのテンプレートを扱うクラス<br>
 * テーブル単位のER図（{@link TableDefinitionTemplates#erDiagram}）とは異なり、
 * すべてのテーブルを属性なしの箱として描画し、外部キーによる関連のみを表現する。
 * 必要な情報はテーブル一覧と外部キー一覧のみのため、テーブル詳細のチャンク分割取得の影響を受けない
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ErDiagramTemplates {
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final String LINE_SEPARATOR_DOUBLE = LINE_SEPARATOR + LINE_SEPARATOR;
    private static final String HORIZON = "___";

    /**
     * ER図ファイルヘッダー
     *
     * @param title    ページのタイトル
     * @param baseInfo データベース基本情報
     * @return ヘッダー文字列
     */
    public static String fileHeader(String title, BaseInfoEntity baseInfo) {
        return "# " + String.format("%s（DB名：%s）", title, baseInfo.dbName()) + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * スキーマ別ER図ページのファイルヘッダー
     *
     * @param schemaName スキーマ名
     * @param baseInfo   データベース基本情報
     * @return ヘッダー文字列
     */
    public static String schemaFileHeader(String schemaName, BaseInfoEntity baseInfo) {
        return "# " + String.format("ER図（DB名：%s / スキーマ名：%s）", baseInfo.dbName(), schemaName)
                + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * 基本情報セクション
     *
     * @param baseInfo データベース基本情報
     * @return 基本情報セクション文字列
     */
    public static String baseInfo(BaseInfoEntity baseInfo) {
        return """
                ## 基本情報

                | RDBMS | データベース名 | 作成日 |
                |:---|:---|:---|
                """ + baseInfo.baseInfo() + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * ER図索引セクション（スキーマ別ER図へのリンク一覧）
     *
     * @param baseInfo       データベース基本情報
     * @param tablesBySchema スキーマ名をキー、当該スキーマのテーブルのリストを値とするマップ
     * @return ER図索引セクション文字列
     */
    public static String schemaIndex(BaseInfoEntity baseInfo, Map<String, List<TableEntity>> tablesBySchema) {
        StringBuilder sb = new StringBuilder("## スキーマ別ER図").append(LINE_SEPARATOR_DOUBLE)
                .append("| No. | スキーマ名 | テーブル数 | Link |").append(LINE_SEPARATOR)
                .append("|:---|:---|:---|:---|").append(LINE_SEPARATOR);
        final int[] no = { 0 };
        tablesBySchema.forEach((schemaName, tables) -> sb.append(String.format("| %d | %s | %d | [ER図](./%s) |",
                ++no[0], schemaName, tables.size(), erDiagramFileName(baseInfo, schemaName))).append(LINE_SEPARATOR));
        return sb.append(LINE_SEPARATOR).toString();
    }

    /**
     * スキーマ跨ぎ外部キーセクション<br>
     * スキーマ単位にER図を分割すると、スキーマをまたぐ関連は双方の図に現れて全体像が追いにくいため、
     * 索引ページに一覧としてまとめて掲載する
     *
     * @param crossSchemaForeignKeys スキーマを跨ぐ外部キーのリスト
     * @return スキーマ跨ぎ外部キーセクション文字列。該当がない場合は空文字列
     */
    public static String crossSchemaForeignKeys(List<ForeignKeyEntity> crossSchemaForeignKeys) {
        if (crossSchemaForeignKeys.isEmpty()) {
            return "";
        }
        return "## スキーマ跨ぎの外部キー" + LINE_SEPARATOR_DOUBLE + foreignKeyTable(crossSchemaForeignKeys);
    }

    /**
     * ER図セクション（Mermaid記法）<br>
     * 外部キーによる関連を持つテーブルのみをノードとして描画する。
     * 関連を持たないテーブルを含めるとノード数が膨らみ図が読めなくなるため、
     * それらは{@link #tableList}のテーブル一覧側に掲載する。<br>
     * ノード数が上限を超える場合はMermaidの描画を諦め、外部キーの一覧表にフォールバックする
     *
     * @param foreignKeys 当該スキーマに関連する外部キー情報のリスト
     * @param maxNodes    1つの図に描画するノード数の上限。0以下の場合は上限なし
     * @return ER図セクション文字列
     */
    public static String erDiagram(List<ForeignKeyEntity> foreignKeys, int maxNodes) {
        StringBuilder sb = new StringBuilder("## ER図").append(LINE_SEPARATOR_DOUBLE);
        if (foreignKeys.isEmpty()) {
            return sb.append("外部キーによる関連を持つテーブルはありません。").append(LINE_SEPARATOR_DOUBLE).toString();
        }
        final Set<TableKey> nodeKeys = collectNodeKeys(foreignKeys);
        if (maxNodes > 0 && nodeKeys.size() > maxNodes) {
            return sb.append(String.format("関連テーブル数が%d件と上限（%d件）を超えるため、ER図の描画を省略しました。", nodeKeys.size(), maxNodes))
                    .append(LINE_SEPARATOR).append("代わりに外部キーによる関連を一覧で掲載します。").append(LINE_SEPARATOR_DOUBLE)
                    .append(foreignKeyTable(foreignKeys)).toString();
        }
        final Map<TableKey, String> ids = assignNodeIds(nodeKeys);
        sb.append("```mermaid").append(LINE_SEPARATOR).append("erDiagram").append(LINE_SEPARATOR);
        // 参照先（親） ||--o{ 参照元（子） の向きは、テーブル単位のER図の表記と揃える
        foreignKeys.forEach(fk -> sb.append("    ")
                .append(ids.get(TableKey.of(fk.referenceSchemaName(), fk.referenceTableName()))).append(" ||--o{ ")
                .append(ids.get(TableKey.of(fk.schemaName(), fk.tableName()))).append(" : \"")
                .append(fk.foreignkeyName()).append('"').append(LINE_SEPARATOR));
        return sb.append("```").append(LINE_SEPARATOR_DOUBLE).toString();
    }

    /**
     * テーブル一覧セクション<br>
     * 外部キーによる関連の有無を示したうえで、各テーブルの定義書へのリンクを掲載する。
     * Mermaidの{@code click}構文はGitHub上では無効化されるため、図中のノードからの導線はこの一覧で代替する
     *
     * @param tablesInSchema 当該スキーマに属するテーブルのリスト
     * @param foreignKeys    当該スキーマに関連する外部キー情報のリスト
     * @return テーブル一覧セクション文字列
     */
    public static String tableList(List<TableEntity> tablesInSchema, List<ForeignKeyEntity> foreignKeys) {
        final Set<TableKey> nodeKeys = collectNodeKeys(foreignKeys);
        StringBuilder sb = new StringBuilder("## テーブル一覧").append(LINE_SEPARATOR_DOUBLE)
                .append("| No. | 物理テーブル名 | 論理テーブル名 | 区分 | 関連 | Link |").append(LINE_SEPARATOR)
                .append("|:---|:---|:---|:---|:---|:---|").append(LINE_SEPARATOR);
        IntStream.range(0, tablesInSchema.size()).forEach(i -> {
            final TableEntity table = tablesInSchema.get(i);
            sb.append(String.format("| %d | %s | %s | %s | %s | [定義書](./%s) |", i + 1, table.physicalTableName(),
                    StringUtils.defaultString(table.logicalTableName()), table.tableType(),
                    nodeKeys.contains(TableKey.of(table)) ? "○" : "-", tableDefinitionPath(table)))
                    .append(LINE_SEPARATOR);
        });
        return sb.append(LINE_SEPARATOR).toString();
    }

    /**
     * スキーマ別ER図ページのフッター
     *
     * @param baseInfo データベース基本情報
     * @return フッター文字列
     */
    public static String schemaFooter(BaseInfoEntity baseInfo) {
        return HORIZON + LINE_SEPARATOR_DOUBLE
                + String.format("[ER図一覧へ](./erDiagramList_%s.md) [テーブル一覧へ](./tableList_%s.md)",
                        baseInfo.dbName(), baseInfo.dbName())
                + LINE_SEPARATOR;
    }

    /**
     * ER図索引ページのフッター
     *
     * @param baseInfo データベース基本情報
     * @return フッター文字列
     */
    public static String indexFooter(BaseInfoEntity baseInfo) {
        return HORIZON + LINE_SEPARATOR_DOUBLE + String.format("[テーブル一覧へ](./tableList_%s.md)", baseInfo.dbName())
                + LINE_SEPARATOR;
    }

    /**
     * スキーマ別ER図のファイル名を生成するメソッド<br>
     * 索引ページからのリンク生成に利用する（実際の出力パスはOutputPathResolverが解決する）
     *
     * @param baseInfo   データベース基本情報
     * @param schemaName スキーマ名
     * @return スキーマ別ER図のファイル名
     */
    private static String erDiagramFileName(BaseInfoEntity baseInfo, String schemaName) {
        return String.format("erDiagram_%s_%s.md", baseInfo.dbName(), schemaName);
    }

    /**
     * テーブル定義書への相対パスを生成するメソッド<br>
     * ER図は出力ベースディレクトリ直下に配置されるため、{@code ./{DB名}/{スキーマ名}/{区分}/{物理テーブル名}.md}となる
     *
     * @param table テーブル情報
     * @return テーブル定義書への相対パス
     */
    private static String tableDefinitionPath(TableEntity table) {
        return String.format("%s/%s/%s/%s.md", table.dbName(), table.schemaName(), table.tableType(),
                table.physicalTableName());
    }

    /**
     * 外部キーの両端のテーブルをノードとして収集するメソッド
     *
     * @param foreignKeys 外部キー情報のリスト
     * @return ノードとなるテーブルキーの集合（登場順を保持する）
     */
    private static Set<TableKey> collectNodeKeys(List<ForeignKeyEntity> foreignKeys) {
        final Set<TableKey> nodeKeys = new LinkedHashSet<>();
        foreignKeys.forEach(fk -> {
            nodeKeys.add(TableKey.of(fk.schemaName(), fk.tableName()));
            nodeKeys.add(TableKey.of(fk.referenceSchemaName(), fk.referenceTableName()));
        });
        return nodeKeys;
    }

    /**
     * ノードごとに一意なMermaid識別子を採番するメソッド<br>
     * 識別子のサニタイズでは記号がすべてアンダースコアに潰れるため、
     * 多数のテーブルを1つの図に載せると別テーブルが同一識別子となり1ノードに融合する恐れがある。
     * 衝突した場合は連番を付与して一意性を担保する
     *
     * @param nodeKeys ノードとなるテーブルキーの集合
     * @return テーブルキーをキー、Mermaid識別子を値とするマップ
     */
    private static Map<TableKey, String> assignNodeIds(Set<TableKey> nodeKeys) {
        final Map<TableKey, String> ids = new LinkedHashMap<>();
        final Set<String> usedIds = new HashSet<>();
        nodeKeys.forEach(key -> {
            final String baseId = MermaidSupport.mermaidId(key.schema(), key.table());
            String id = baseId;
            for (int suffix = 2; !usedIds.add(id); suffix++) {
                id = baseId + "_" + suffix;
            }
            ids.put(key, id);
        });
        return ids;
    }

    /**
     * 外部キーの一覧表を生成するメソッド
     *
     * @param foreignKeys 外部キー情報のリスト
     * @return 外部キー一覧表の文字列
     */
    private static String foreignKeyTable(List<ForeignKeyEntity> foreignKeys) {
        final String header = """
                | No. | 参照元 | 外部キー名 | 参照先 |
                |:---|:---|:---|:---|
                """;
        return header + IntStream.range(0, foreignKeys.size()).mapToObj(i -> {
            final ForeignKeyEntity fk = foreignKeys.get(i);
            return String.format("| %d | %s | %s | %s |", i + 1, fk.getSchemaTableName(), fk.foreignkeyName(),
                    fk.getReferenceSchemaTableName());
        }).collect(Collectors.joining(LINE_SEPARATOR)) + LINE_SEPARATOR_DOUBLE;
    }
}
