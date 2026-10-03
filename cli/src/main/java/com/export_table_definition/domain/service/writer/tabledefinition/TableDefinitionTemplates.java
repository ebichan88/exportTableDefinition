package com.export_table_definition.domain.service.writer.tabledefinition;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.row;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.relation.DiagramBoxes;
import com.export_table_definition.domain.model.relation.DiagramColumn;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.sidecar.TableAnnotation;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.ConstraintEntity;
import com.export_table_definition.domain.model.table.IndexEntity;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TableKey;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import com.export_table_definition.domain.service.path.DocumentLocations;
import com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport;
import com.export_table_definition.domain.service.writer.template.MermaidSupport;
import com.export_table_definition.domain.service.writer.template.PagedSectionTemplates;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/** テーブル定義書き込みに利用するMarkdownのテンプレートを扱うクラス */
public class TableDefinitionTemplates {

  /** テーブル定義ヘッダー */
  public static String fileHeader(TableEntity table) {
    return "# " + table.getHeaderTableName() + LINE_SEPARATOR_DOUBLE;
  }

  /** 基本情報セクション */
  public static String baseInfo(BaseInfoEntity baseInfo) {
    return MarkdownTemplateSupport.baseInfoSection(baseInfo);
  }

  /**
   * テーブル説明セクション<br>
   * サイドカー由来の説明が存在する場合はその本文を、存在しない場合は従来通り空のセクションを出力する。 説明本文は自由記述のブロックとしてそのまま出力するため、改行はエスケープしない
   */
  public static String tableExplanation(TableAnnotation annotation) {
    final String description = annotation.description();
    if (description.isBlank()) {
      return """
                    ## テーブル説明

                    """;
    }
    return "## テーブル説明"
        + LINE_SEPARATOR_DOUBLE
        + description.stripTrailing()
        + LINE_SEPARATOR_DOUBLE;
  }

  /**
   * テーブル情報セクション<br>
   * 論理テーブル名はDBコメント由来の自由記述文字列（{@code |}・改行を含みうる）のためエスケープする。 末尾の備考セルはSQLでは付与されないため、サイドカー由来のテーブル備考を
   * エスケープした上でここで後付けする（FK多重度と同様の後付け方式）
   */
  public static String tableInfo(TableEntity table, TableAnnotation annotation) {
    return """
                ## テーブル情報

                | スキーマ名 | 論理テーブル名 | 物理テーブル名 | 区分 | 備考 |
                |:---|:---|:---|:---|:---|
                """
        + row(
            table.schemaName(),
            MarkdownTemplateSupport.escapeTableCell(table.logicalTableName()),
            table.physicalTableName(),
            table.tableType().getName(),
            MarkdownTemplateSupport.escapeTableCell(annotation.remarks()))
        + LINE_SEPARATOR_DOUBLE;
  }

  /**
   * カラム情報セクション<br>
   * 論理名（DBコメント）・デフォルト値（DBのデフォルト式。PostgreSQLの{@code ||}連結等で{@code |}を含みうる）は、
   * いずれも自由記述文字列で改行を含む場合もあるためエスケープする
   */
  public static String columns(List<ColumnEntity> columns, TableAnnotation annotation) {
    String header =
        """
                ## カラム情報

                | No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|:---|:---|
                """;
    // 末尾の備考セルはSQL由来ではないため、物理カラム名をキーにサイドカー由来の備考を後付けする
    return tableSection(
        columns,
        header,
        (no, c) ->
            row(
                no,
                MarkdownTemplateSupport.escapeTableCell(c.logicalColumnName()),
                c.physicalColumnName(),
                c.columnType(),
                c.precisionScale(),
                MarkdownTemplateSupport.marker(c.primaryKey()),
                MarkdownTemplateSupport.marker(c.notNull()),
                MarkdownTemplateSupport.escapeTableCell(c.defaultValue()),
                MarkdownTemplateSupport.escapeTableCell(
                    annotation.columnRemark(c.physicalColumnName()))));
  }

  /** ソース（view・materialized viewの定義）セクション。viewでないテーブルでは空文字 */
  public static String view(TableEntity table) {
    if (!table.isView()) {
      return "";
    }
    return """
                ## ソース

                ```sql
                """
        + LINE_SEPARATOR
        + table.definition()
        + LINE_SEPARATOR
        + """

                ```

                """;
  }

  /**
   * インデックス情報セクション<br>
   * 備考はDBコメント（{@code COMMENT ON INDEX}）由来の自由記述文字列のためエスケープする
   */
  public static String indexes(List<IndexEntity> indexes) {
    String header =
        """
                ## インデックス情報

                | No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
    return tableSection(
        indexes,
        header,
        (no, idx) ->
            row(
                no,
                idx.indexName(),
                idx.indexMethod(),
                MarkdownTemplateSupport.marker(idx.isUnique()),
                MarkdownTemplateSupport.marker(idx.isPrimary()),
                MarkdownTemplateSupport.escapePipe(idx.indexDefinition()),
                MarkdownTemplateSupport.escapeTableCell(idx.remarks())));
  }

  /**
   * 制約情報セクション<br>
   * 備考はDBコメント（{@code COMMENT ON CONSTRAINT}）由来の自由記述文字列のためエスケープする
   */
  public static String constraints(List<ConstraintEntity> constraints) {
    String header =
        """
                ## 制約情報

                | No. | 制約名 | 種類 | 制約定義 | 備考 |
                |:---|:---|:---|:---|:---|
                """;
    return tableSection(
        constraints,
        header,
        (no, c) ->
            row(
                no,
                c.constraintName(),
                c.constraintType(),
                MarkdownTemplateSupport.escapePipe(c.constraintDefinition()),
                MarkdownTemplateSupport.escapeTableCell(c.remarks())));
  }

  /** 外部キー情報セクション（DBに実在する外部キー制約のみ） */
  public static String foreignKeys(List<ForeignKeyEntity> foreignkeys) {
    String header =
        """
                ## 外部キー情報

                | No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
                |:---|:---|:---|:---|:---|:---|
                """;
    return tableSection(foreignkeys, header, TableDefinitionTemplates::physicalRelationTableLine);
  }

  /**
   * 論理リレーション情報セクション<br>
   * DBに外部キー制約が存在せず、サイドカーYAMLで宣言された関連のみを掲載する。 読み手が「DBに制約がある」と誤読しないよう外部キー情報とは別セクションとし、注意書きを添える。
   * 対象が1件も存在しない場合はセクションごと出力しない（制約を張っているDBでは常に不要なため）。 関連名は現状カラムリストと同じ内容のため列を設けず、カラムリストのみ掲載する
   *
   * @return 対象が存在しない場合は空文字
   */
  public static String logicalRelations(List<ForeignKeyEntity> logicalRelations) {
    if (logicalRelations.isEmpty()) {
      return "";
    }
    final String header =
        "## 論理リレーション情報"
            + LINE_SEPARATOR_DOUBLE
            + "※DBに外部キー制約は存在せず、サイドカーYAMLで宣言された関連です。"
            + LINE_SEPARATOR_DOUBLE
            + """
                | No. | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
                |:---|:---|:---|:---|:---|
                """;
    // 物理外部キーの行番号とは独立に、当セクション内で1から採番する
    return tableSection(
        logicalRelations, header, TableDefinitionTemplates::logicalRelationTableLine);
  }

  /**
   * 多重度のラベル表記は{@link com.export_table_definition.domain.model.relation.Cardinality}に集約している
   *
   * @return 1行分の文字列（改行を含まない）
   */
  private static String physicalRelationTableLine(int no, ForeignKeyEntity fk) {
    return row(
        no,
        fk.foreignKeyName(),
        String.join(",", fk.columnNames()),
        fk.getReferenceSchemaTableName(),
        String.join(",", fk.referenceColumnNames()),
        fk.cardinality().getLabel());
  }

  /**
   * @return 1行分の文字列（改行を含まない）
   */
  private static String logicalRelationTableLine(int no, ForeignKeyEntity fk) {
    return row(
        no,
        String.join(",", fk.columnNames()),
        fk.getReferenceSchemaTableName(),
        String.join(",", fk.referenceColumnNames()),
        fk.cardinality().getLabel());
  }

  /** トリガー情報セクション */
  public static String triggers(List<TriggerEntity> triggers) {
    String header =
        """
                ## トリガー情報

                | No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
                |:---|:---|:---|:---|:---|:---|
                """;
    return tableSection(
        triggers,
        header,
        (no, t) ->
            row(
                no,
                t.triggerName(),
                t.timing(),
                String.join("/", t.events()),
                t.orientation(),
                MarkdownTemplateSupport.escapePipe(t.triggerDefinition())));
  }

  /**
   * ER図セクション（Mermaid記法）<br>
   * 自テーブルは全カラムを、関連テーブル（参照元・参照先）は描画する関連をつなぐカラムだけを箱に表示する。 関連テーブルのカラムはチャンク単位の分割取得の対象外のため、{@code
   * boxes}が別途取得した関連カラムを用いる
   *
   * @param outgoingFks 自テーブルが参照している外部キー（自テーブル → 参照先）のリスト
   * @param incomingFks 自テーブルを参照している外部キー（参照元 → 自テーブル）のリスト
   */
  public static String erDiagram(
      TableEntity table,
      List<ColumnEntity> columns,
      List<ForeignKeyEntity> outgoingFks,
      List<ForeignKeyEntity> incomingFks,
      DiagramBoxes boxes) {
    StringBuilder sb = new StringBuilder("## ER図").append(LINE_SEPARATOR_DOUBLE);
    if (outgoingFks.isEmpty() && incomingFks.isEmpty()) {
      return sb.append("関連するテーブルはありません。").append(LINE_SEPARATOR_DOUBLE).toString();
    }
    final TableKey selfKey = TableKey.of(table);
    final String selfId = MermaidSupport.mermaidId(selfKey);
    final Set<TableKey> nodeKeys = new LinkedHashSet<>();
    nodeKeys.add(selfKey);
    outgoingFks.forEach(fk -> nodeKeys.add(fk.referenceTableKey()));
    incomingFks.forEach(fk -> nodeKeys.add(fk.tableKey()));
    final List<ForeignKeyEntity> drawnRelations =
        Stream.concat(outgoingFks.stream(), incomingFks.stream()).toList();
    final Map<TableKey, String> labels = MermaidSupport.assignLabels(nodeKeys, boxes);
    sb.append("```mermaid").append(LINE_SEPARATOR).append("erDiagram").append(LINE_SEPARATOR);
    nodeKeys.forEach(
        key -> sb.append(MermaidSupport.aliasLine(MermaidSupport.mermaidId(key), labels.get(key))));
    outgoingFks.forEach(
        fk ->
            sb.append(
                MermaidSupport.relationLine(
                    MermaidSupport.mermaidId(fk.referenceTableKey()), fk, selfId)));
    incomingFks.forEach(
        fk ->
            sb.append(
                MermaidSupport.relationLine(selfId, fk, MermaidSupport.mermaidId(fk.tableKey()))));
    sb.append(
        MermaidSupport.attributeBlock(selfId, DiagramColumn.of(selfKey, columns, drawnRelations)));
    final Map<TableKey, List<DiagramColumn>> relationColumns =
        boxes.relationColumnsOf(drawnRelations);
    nodeKeys.stream()
        .filter(key -> !key.equals(selfKey))
        .forEach(
            key ->
                sb.append(
                    MermaidSupport.attributeBlock(
                        MermaidSupport.mermaidId(key),
                        relationColumns.getOrDefault(key, List.of()))));
    return sb.append("```").append(LINE_SEPARATOR_DOUBLE).toString();
  }

  /**
   * 所属する観点セクション<br>
   * テーブル定義書から、当該テーブルが所属する観点のページへ戻る導線とする。所属する観点が無いテーブル
   * （観点を宣言していない場合を含む）では、セクションごと出力しない（観点を導入しても、所属しないテーブルの定義書は変わらない）
   *
   * @param viewpoints 当該テーブルが所属する観点のリスト（宣言順）
   * @return 所属する観点が無い場合は空文字
   */
  public static String viewpoints(List<Viewpoint> viewpoints, BaseInfoEntity baseInfo) {
    if (viewpoints.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder("## 所属する観点").append(LINE_SEPARATOR_DOUBLE);
    viewpoints.forEach(
        viewpoint ->
            sb.append(
                    String.format(
                        "* [%s](%s)  ",
                        viewpoint.name(),
                        DocumentLocations.linkFromDefinition(
                            DocumentLocations.viewpointFile(baseInfo.dbName(), viewpoint))))
                .append(LINE_SEPARATOR));
    return sb.append(LINE_SEPARATOR).toString();
  }

  /** テーブル一覧へ戻るリンクのフッター */
  public static String footer(BaseInfoEntity baseInfo) {
    return PagedSectionTemplates.backOnlyFooter(
        DocumentLocations.linkFromDefinition(
            DocumentLocations.listFile(ListDocumentType.TABLE, baseInfo.dbName())),
        ListDocumentType.TABLE.getBackLinkLabel());
  }

  /**
   * 行番号はリスト内での位置（1始まり）から採番する。当該テーブルへの絞り込みは{@link
   * com.export_table_definition.domain.model.target.TableDefinitionContent#assemble}で済んでいる前提とする
   */
  private static <T> String tableSection(
      List<T> list, String header, BiFunction<Integer, T, String> lineMapper) {
    final StringBuilder sb = new StringBuilder(header);
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) {
        sb.append(LINE_SEPARATOR);
      }
      sb.append(lineMapper.apply(i + 1, list.get(i)));
    }
    return sb.append(LINE_SEPARATOR_DOUBLE).toString();
  }
}
