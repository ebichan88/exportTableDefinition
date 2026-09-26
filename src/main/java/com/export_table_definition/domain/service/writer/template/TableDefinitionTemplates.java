package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.row;

import com.export_table_definition.domain.model.database.BaseInfoEntity;
import com.export_table_definition.domain.model.document.ListDocumentType;
import com.export_table_definition.domain.model.relation.ForeignKeyEntity;
import com.export_table_definition.domain.model.sidecar.TableAnnotation;
import com.export_table_definition.domain.model.table.ColumnEntity;
import com.export_table_definition.domain.model.table.ConstraintEntity;
import com.export_table_definition.domain.model.table.IndexEntity;
import com.export_table_definition.domain.model.table.TableEntity;
import com.export_table_definition.domain.model.table.TriggerEntity;
import com.export_table_definition.domain.model.viewpoint.Viewpoint;
import com.export_table_definition.domain.service.path.DocumentLocations;
import java.util.List;
import java.util.function.BiFunction;

/**
 * テーブル定義書き込みに利用するMarkdownのテンプレートを扱うクラス
 *
 * @since 1.0
 * @version 1.0
 * @author takashi.ebina
 */
public class TableDefinitionTemplates {

  /**
   * テーブル定義ヘッダー
   *
   * @param table テーブル情報
   * @return ヘッダー文字列
   */
  public static String fileHeader(TableEntity table) {
    return "# " + table.getHeaderTableName() + LINE_SEPARATOR_DOUBLE;
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
   * テーブル説明セクション<br>
   * サイドカー由来の説明が存在する場合はその本文を、存在しない場合は従来通り空のセクションを出力する。 説明本文は自由記述のブロックとしてそのまま出力するため、改行はエスケープしない
   *
   * @param annotation テーブルの手動付帯情報
   * @return テーブル説明セクション文字列
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
   *
   * @param table テーブル情報
   * @param annotation テーブルの手動付帯情報
   * @return テーブル情報セクション文字列
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
   *
   * @param columns 当該テーブルのカラム情報のリスト
   * @param annotation テーブルの手動付帯情報
   * @return カラム情報セクション文字列
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

  /**
   * ビュー情報セクション
   *
   * @param table テーブル情報
   * @return ビュー情報セクション文字列
   */
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
   *
   * @param indexes 当該テーブルのインデックス情報のリスト
   * @return インデックス情報セクション文字列
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
   *
   * @param constraints 当該テーブルの制約情報のリスト
   * @return 制約情報セクション文字列
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

  /**
   * 外部キー情報セクション
   *
   * @param foreignkeys 当該テーブルの外部キー情報のリスト
   * @return 外部キー情報セクション文字列
   */
  public static String foreignKeys(List<ForeignKeyEntity> foreignkeys) {
    String header =
        """
                ## 外部キー情報

                | No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
                |:---|:---|:---|:---|:---|:---|
                """;
    return tableSection(foreignkeys, header, TableDefinitionTemplates::relationTableLine);
  }

  /**
   * 論理リレーション情報セクション<br>
   * DBに外部キー制約が存在せず、サイドカーYAMLで宣言された関連のみを掲載する。 読み手が「DBに制約がある」と誤読しないよう外部キー情報とは別セクションとし、注意書きを添える。
   * 対象が1件も存在しない場合はセクションごと出力しない（制約を張っているDBでは常に不要なため）
   *
   * @param logicalRelations 当該テーブルの論理リレーションのリスト
   * @return 論理リレーション情報セクション文字列。対象が存在しない場合は空文字
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
                | No. | 関連名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
                |:---|:---|:---|:---|:---|:---|
                """;
    // 物理外部キーの行番号とは独立に、当セクション内で1から採番する
    return tableSection(logicalRelations, header, TableDefinitionTemplates::relationTableLine);
  }

  /**
   * 外部キー・論理リレーションの1行分を生成するメソッド<br>
   * 多重度のラベル表記は{@link com.export_table_definition.domain.model.relation.Cardinality}に集約している
   *
   * @param no 行番号
   * @param fk 外部キーまたは論理リレーション
   * @return 1行分の文字列（改行を含まない）
   */
  private static String relationTableLine(int no, ForeignKeyEntity fk) {
    return row(
        no,
        fk.foreignkeyName(),
        String.join(",", fk.columnNames()),
        fk.getReferenceSchemaTableName(),
        String.join(",", fk.referenceColumnNames()),
        fk.cardinality().getLabel());
  }

  /**
   * トリガー情報セクション
   *
   * @param triggers 当該テーブルのトリガー情報のリスト
   * @return トリガー情報セクション文字列
   */
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
   * 自テーブルはカラム・PK情報付きの箱として、関連テーブル（参照元・参照先）は 属性なしの箱として描画する。関連テーブルの属性情報を必要としないため、
   * チャンク単位の分割取得（他チャンク・他スキーマのテーブル詳細を保持しないこと）の影響を受けない
   *
   * @param table テーブル情報
   * @param columns 自テーブルのカラム情報のリスト
   * @param outgoingFks 自テーブルが参照している外部キー（自テーブル → 参照先）のリスト
   * @param incomingFks 自テーブルを参照している外部キー（参照元 → 自テーブル）のリスト
   * @return ER図セクション文字列
   */
  public static String erDiagram(
      TableEntity table,
      List<ColumnEntity> columns,
      List<ForeignKeyEntity> outgoingFks,
      List<ForeignKeyEntity> incomingFks) {
    StringBuilder sb = new StringBuilder("## ER図").append(LINE_SEPARATOR_DOUBLE);
    if (outgoingFks.isEmpty() && incomingFks.isEmpty()) {
      return sb.append("関連するテーブルはありません。").append(LINE_SEPARATOR_DOUBLE).toString();
    }
    final String selfId = MermaidSupport.mermaidId(table.schemaName(), table.physicalTableName());
    sb.append("```mermaid").append(LINE_SEPARATOR).append("erDiagram").append(LINE_SEPARATOR);
    outgoingFks.forEach(
        fk ->
            sb.append(
                MermaidSupport.relationLine(
                    MermaidSupport.mermaidId(fk.referenceSchemaName(), fk.referenceTableName()),
                    fk,
                    selfId)));
    incomingFks.forEach(
        fk ->
            sb.append(
                MermaidSupport.relationLine(
                    selfId, fk, MermaidSupport.mermaidId(fk.schemaName(), fk.tableName()))));
    sb.append("    ").append(selfId).append(" {").append(LINE_SEPARATOR);
    columns.forEach(
        c ->
            sb.append("        ")
                .append(MermaidSupport.sanitizeType(c.columnType()))
                .append(' ')
                .append(MermaidSupport.sanitizeIdentifier(c.physicalColumnName()))
                .append(c.primaryKey() ? " PK" : "")
                .append(LINE_SEPARATOR));
    sb.append("    }").append(LINE_SEPARATOR).append("```").append(LINE_SEPARATOR_DOUBLE);
    return sb.toString();
  }

  /**
   * 所属する観点セクション<br>
   * テーブル定義書から、当該テーブルが所属する観点のページへ戻る導線とする。所属する観点が無いテーブル
   * （観点を宣言していない場合を含む）では、セクションごと出力しない（観点を導入しても、所属しないテーブルの定義書は変わらない）
   *
   * @param viewpoints 当該テーブルが所属する観点のリスト（宣言順）
   * @param baseInfo データベース基本情報
   * @return 所属する観点セクション文字列。所属する観点が無い場合は空文字
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

  /**
   * フッター
   *
   * @param baseInfo データベース基本情報
   * @return フッター文字列
   */
  public static String footer(BaseInfoEntity baseInfo) {
    return PagedSectionTemplates.backOnlyFooter(
        DocumentLocations.linkFromDefinition(
            DocumentLocations.listFile(ListDocumentType.TABLE, baseInfo.dbName())),
        ListDocumentType.TABLE.getBackLinkLabel());
  }

  /**
   * テーブルごとのセクションを生成する共通メソッド<br>
   * 行番号はリスト内での位置（1始まり）から採番する。当該テーブルへの絞り込みは{@link
   * com.export_table_definition.domain.model.target.TableDefinitionContent#assemble}で済んでいる前提とする
   *
   * @param <T> エンティティの型
   * @param list 当該テーブルのエンティティのリスト
   * @param header セクションのヘッダー文字列
   * @param lineMapper 行番号とエンティティから1行分の文字列を生成する関数
   * @return テーブルごとのセクション文字列
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
