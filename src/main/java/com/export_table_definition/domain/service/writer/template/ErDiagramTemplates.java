package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.HORIZON;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.value.TableKey;

/**
 * スキーマ単位のER図（全体ER図）書き込みに利用するMarkdownのテンプレートを扱うクラス<br>
 * テーブル単位のER図（{@link TableDefinitionTemplates#erDiagram}）とは異なり、
 * すべてのテーブルを属性なしの箱として描画し、外部キーによる関連のみを表現する。
 * 必要な情報はテーブル一覧と外部キー一覧のみのため、テーブル詳細のチャンク分割取得の影響を受けない<br>
 * 表のセクションはヘッダーと1行分を個別に生成できるようにしている。
 * 行数が多い場合に呼び出し側がページ単位で切り出して書き込めるようにするためで、
 * テーブル一覧（{@link TableDefinitionListTemplates}）と同じ方針である
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class ErDiagramTemplates {

    /**
     * ER図ファイルヘッダー
     *
     * @param title    ページのタイトル
     * @param baseInfo データベース基本情報
     * @return ヘッダー文字列
     */
    public static String fileHeader(String title, BaseInfoEntity baseInfo) {
        return MarkdownTemplateSupport.titledFileHeader(title, baseInfo);
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
     * グループ別ER図ページのファイルヘッダー
     *
     * @param schemaName スキーマ名
     * @param groupNo    グループ番号（1始まり）
     * @param baseInfo   データベース基本情報
     * @return ヘッダー文字列
     */
    public static String groupFileHeader(String schemaName, int groupNo, BaseInfoEntity baseInfo) {
        return "# " + String.format("ER図（DB名：%s / スキーマ名：%s / グループ%d）", baseInfo.dbName(), schemaName, groupNo)
                + LINE_SEPARATOR_DOUBLE;
    }

    /**
     * 基本情報セクション
     *
     * @param baseInfo データベース基本情報
     * @return 基本情報セクション文字列
     */
    public static String baseInfo(BaseInfoEntity baseInfo) {
        return MarkdownTemplateSupport.baseInfoSection(baseInfo);
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
        tablesBySchema.forEach((schemaName, tables) -> sb.append(String.format("| %d | %s | %d | [■](./%s) |",
                ++no[0], schemaName, tables.size(), erDiagramFileName(baseInfo, schemaName))).append(LINE_SEPARATOR));
        return sb.append(LINE_SEPARATOR).toString();
    }

    /**
     * ER図セクション（Mermaid記法）<br>
     * 外部キーによる関連を持つテーブルのみをノードとして描画する。
     * 関連を持たないテーブルを含めるとノード数が膨らみ図が読めなくなるため描画対象から除外する
     * （全テーブルはテーブル一覧{@code tableList_{DB名}.md}側に掲載されている）。<br>
     * ノード数が上限を超える場合はMermaidの描画を諦め、その旨のメッセージのみを返す
     * （代替として掲載する外部キー一覧は呼び出し側が組み立てる）
     *
     * @param foreignKeys 当該スキーマに関連する外部キー情報のリスト
     * @param nodes       図のノードとなるテーブルキーのリスト
     * @param maxNodes    1つの図に描画するノード数の上限。0以下の場合は上限なし
     * @return ER図セクション文字列
     */
    public static String erDiagram(List<ForeignKeyEntity> foreignKeys, List<TableKey> nodes, int maxNodes) {
        StringBuilder sb = new StringBuilder("## ER図").append(LINE_SEPARATOR_DOUBLE);
        if (foreignKeys.isEmpty()) {
            return sb.append("外部キーによる関連を持つテーブルはありません。").append(LINE_SEPARATOR_DOUBLE).toString();
        }
        if (isOverflow(nodes.size(), maxNodes)) {
            return sb
                    .append(String.format("ER図に描画するテーブル数が%d件となり、上限（erDiagramMaxNodes = %d件）を超えるため描画を省略しました。",
                            nodes.size(), maxNodes))
                    .append(LINE_SEPARATOR).append("代わりに外部キーによる関連を一覧で掲載します。").append(LINE_SEPARATOR_DOUBLE)
                    .toString();
        }
        final Map<TableKey, String> ids = assignNodeIds(nodes);
        sb.append("```mermaid").append(LINE_SEPARATOR).append("erDiagram").append(LINE_SEPARATOR);
        // 参照先（親） → 参照元（子） の向きは、テーブル単位のER図の表記と揃える
        foreignKeys.forEach(fk -> sb.append("    ")
                .append(ids.get(TableKey.of(fk.referenceSchemaName(), fk.referenceTableName()))).append(' ')
                .append(fk.cardinality().getNotation()).append(' ')
                .append(ids.get(TableKey.of(fk.schemaName(), fk.tableName()))).append(" : \"")
                .append(fk.foreignkeyName()).append('"').append(LINE_SEPARATOR));
        return sb.append("```").append(LINE_SEPARATOR_DOUBLE).toString();
    }

    /**
     * ER図をグループに分割した場合の、スキーマページに掲載する説明セクション
     *
     * @param nodeCount  当該スキーマの関連テーブル数
     * @param maxNodes   1つの図に描画するノード数の上限
     * @param groupCount 分割後のグループ数
     * @return 説明セクション文字列
     */
    public static String groupedMessage(int nodeCount, int maxNodes, int groupCount) {
        return new StringBuilder("## ER図").append(LINE_SEPARATOR_DOUBLE)
                .append(String.format("ER図に描画するテーブル数が%d件となり、上限（erDiagramMaxNodes = %d件）を超えるため、", nodeCount, maxNodes))
                .append(LINE_SEPARATOR)
                .append(String.format("外部キーで繋がったテーブルのまとまりごとに%d個のグループへ分割しました。", groupCount))
                .append(LINE_SEPARATOR_DOUBLE).toString();
    }

    /**
     * グループ一覧セクションの見出し
     *
     * @return 見出し文字列
     */
    public static String groupIndexHeading() {
        return "グループ一覧";
    }

    /**
     * グループ一覧セクションの表ヘッダー
     *
     * @return 表ヘッダー文字列
     */
    public static String groupIndexHeader() {
        return """
                | No. | テーブル数 | 外部キー数 | 主なテーブル | Link |
                |:---|:---|:---|:---|:---|
                """;
    }

    /**
     * グループ一覧セクションの1行分
     *
     * @param no         グループ番号（1始まり）
     * @param tableCount 当該グループのテーブル数
     * @param fkCount    当該グループの外部キー数
     * @param mainTable  当該グループで最も多くの外部キーが接続するテーブル
     * @param href       グループ別ER図ページへの相対パス
     * @return グループ一覧1行分の文字列
     */
    public static String groupIndexLine(int no, int tableCount, int fkCount, TableKey mainTable, String href) {
        return String.format("| %d | %d | %d | %s | [■](%s) |", no, tableCount, fkCount,
                mainTable == null ? "" : mainTable.schema() + "." + mainTable.table(), href) + LINE_SEPARATOR;
    }

    /**
     * グループ別ER図ページのフッター<br>
     * スキーマ全体のER図（グループ一覧）へ戻る導線を加える
     *
     * @param schemaName スキーマ名
     * @param baseInfo   データベース基本情報
     * @return フッター文字列
     */
    public static String groupFooter(String schemaName, BaseInfoEntity baseInfo) {
        return HORIZON + LINE_SEPARATOR_DOUBLE + String.format(
                "[スキーマのER図へ](./erDiagram_%s_%s.md) [ER図一覧へ](./erDiagramList_%s.md) [テーブル一覧へ](./tableList_%s.md)",
                baseInfo.dbName(), schemaName, baseInfo.dbName(), baseInfo.dbName()) + LINE_SEPARATOR;
    }

    /**
     * 図のノードとなるテーブルを取得するメソッド<br>
     * 外部キーの両端のテーブルを収集し、図中の箱を名前から引けるようスキーマ名・テーブル名の順に並べる
     *
     * @param foreignKeys 外部キー情報のリスト
     * @return ノードとなるテーブルキーのリスト
     */
    public static List<TableKey> diagramNodes(List<ForeignKeyEntity> foreignKeys) {
        final Set<TableKey> nodeKeys = new LinkedHashSet<>();
        foreignKeys.forEach(fk -> {
            nodeKeys.add(TableKey.of(fk.schemaName(), fk.tableName()));
            nodeKeys.add(TableKey.of(fk.referenceSchemaName(), fk.referenceTableName()));
        });
        return nodeKeys.stream().sorted(Comparator.comparing(TableKey::schema).thenComparing(TableKey::table))
                .toList();
    }

    /**
     * ノード数が上限を超えているか判定するメソッド
     *
     * @param nodeCount ノード数
     * @param maxNodes  ノード数の上限。0以下の場合は上限なし
     * @return 上限を超えている場合はtrue
     */
    public static boolean isOverflow(int nodeCount, int maxNodes) {
        return maxNodes > 0 && nodeCount > maxNodes;
    }

    /**
     * 掲載テーブルセクションの見出し
     *
     * @return 見出し文字列
     */
    public static String diagramTableHeading() {
        return "ER図に掲載しているテーブル";
    }

    /**
     * 掲載テーブルセクションの表ヘッダー
     *
     * @return 表ヘッダー文字列
     */
    public static String diagramTableHeader() {
        return """
                | No. | スキーマ名 | 物理テーブル名 | 論理テーブル名 | 区分 | Link |
                |:---|:---|:---|:---|:---|:---|
                """;
    }

    /**
     * 掲載テーブルセクションの1行分<br>
     * Mermaidの{@code click}構文はGitHub上では無効化されるため、図中のノードからの導線をこの一覧で代替する
     *
     * @param no    行番号
     * @param key   テーブルキー
     * @param table テーブル情報。出力対象範囲外で定義書が存在しない場合はnull
     * @return 掲載テーブル1行分の文字列
     */
    public static String diagramTableLine(int no, TableKey key, TableEntity table) {
        // 出力対象範囲外のテーブルを参照している場合、定義書が存在しないためリンクを張らない
        if (table == null) {
            return String.format("| %d | %s | %s |  |  | - |", no, key.schema(), key.table()) + LINE_SEPARATOR;
        }
        return String.format("| %d | %s | %s | %s | %s | [■](./%s) |", no, table.schemaName(),
                table.physicalTableName(), StringUtils.defaultString(table.logicalTableName()), table.tableType(),
                tableDefinitionPath(table)) + LINE_SEPARATOR;
    }

    /**
     * 外部キー一覧セクションの見出し（ER図の描画を省略した場合の代替掲載）
     *
     * @return 見出し文字列
     */
    public static String foreignKeyHeading() {
        return "外部キー一覧";
    }

    /**
     * スキーマ跨ぎ外部キーセクションの見出し<br>
     * スキーマ単位にER図を分割すると、スキーマをまたぐ関連は双方の図に現れて全体像が追いにくいため、
     * 索引ページに一覧としてまとめて掲載する
     *
     * @return 見出し文字列
     */
    public static String crossSchemaForeignKeyHeading() {
        return "スキーマ跨ぎの外部キー";
    }

    /**
     * 外部キー一覧セクションの表ヘッダー
     *
     * @return 表ヘッダー文字列
     */
    public static String foreignKeyTableHeader() {
        return """
                | No. | 参照元 | 外部キー名 | 参照先 |
                |:---|:---|:---|:---|
                """;
    }

    /**
     * 外部キー一覧セクションの1行分
     *
     * @param no 行番号
     * @param fk 外部キー情報
     * @return 外部キー一覧1行分の文字列
     */
    public static String foreignKeyTableLine(int no, ForeignKeyEntity fk) {
        return String.format("| %d | %s | %s | %s |", no, fk.getSchemaTableName(), fk.foreignkeyName(),
                fk.getReferenceSchemaTableName()) + LINE_SEPARATOR;
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
        return PagedSectionTemplates.pageFooter(null, null,
                String.format("./tableList_%s.md", baseInfo.dbName()), "テーブル一覧へ");
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
     * ノードごとに一意なMermaid識別子を採番するメソッド<br>
     * 識別子のサニタイズでは記号がすべてアンダースコアに潰れるため、
     * 多数のテーブルを1つの図に載せると別テーブルが同一識別子となり1ノードに融合する恐れがある。
     * 衝突した場合は連番を付与して一意性を担保する
     *
     * @param nodes ノードとなるテーブルキーのリスト
     * @return テーブルキーをキー、Mermaid識別子を値とするマップ
     */
    private static Map<TableKey, String> assignNodeIds(List<TableKey> nodes) {
        final Map<TableKey, String> ids = new LinkedHashMap<>();
        final Set<String> usedIds = new HashSet<>();
        nodes.forEach(key -> {
            final String baseId = MermaidSupport.mermaidId(key.schema(), key.table());
            String id = baseId;
            for (int suffix = 2; !usedIds.add(id); suffix++) {
                id = baseId + "_" + suffix;
            }
            ids.put(key, id);
        });
        return ids;
    }
}
