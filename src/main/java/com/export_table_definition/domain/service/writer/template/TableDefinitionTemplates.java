package com.export_table_definition.domain.service.writer.template;

import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR;
import static com.export_table_definition.domain.service.writer.template.MarkdownTemplateSupport.LINE_SEPARATOR_DOUBLE;

import com.export_table_definition.domain.model.annotation.TableAnnotation;
import com.export_table_definition.domain.model.entity.BaseInfoEntity;
import com.export_table_definition.domain.model.entity.ColumnEntity;
import com.export_table_definition.domain.model.entity.ConstraintEntity;
import com.export_table_definition.domain.model.entity.ForeignKeyEntity;
import com.export_table_definition.domain.model.entity.IndexEntity;
import com.export_table_definition.domain.model.entity.TableEntity;
import com.export_table_definition.domain.model.entity.TriggerEntity;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

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
   * 末尾の備考セルはSQLでは付与されないため、サイドカー由来のテーブル備考を エスケープした上でここで後付けする（FK多重度と同様の後付け方式）
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
        + table.tableInfo()
        + MarkdownTemplateSupport.escapeTableCell(annotation.remarks())
        + "|"
        + LINE_SEPARATOR_DOUBLE;
  }

  /**
   * カラム情報セクション
   *
   * @param columns カラム情報のリスト
   * @param table テーブル情報
   * @param annotation テーブルの手動付帯情報
   * @return カラム情報セクション文字列
   */
  public static String columns(
      List<ColumnEntity> columns, TableEntity table, TableAnnotation annotation) {
    String header =
        """
                ## カラム情報

                | No. | 論理名 | 物理名 | データ型 | 桁数/精度 | PK | Not Null | デフォルト | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|:---|:---|
                """;
    // 末尾の備考セルはSQL由来ではないため、物理カラム名をキーにサイドカー由来の備考を後付けする
    return tableSection(
        columns,
        table,
        header,
        (no, c) ->
            "|"
                + no
                + "|"
                + c.logicalColumnName()
                + "|"
                + c.physicalColumnName()
                + "|"
                + c.columnType()
                + "|"
                + c.precisionScale()
                + "|"
                + c.primaryKey()
                + "|"
                + c.notNull()
                + "|"
                + c.defaultValue()
                + "|"
                + MarkdownTemplateSupport.escapeTableCell(
                    annotation.columnRemark(c.physicalColumnName()))
                + "|",
        ColumnEntity::getSchemaTableName);
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
   * インデックス情報セクション
   *
   * @param indexes インデックス情報のリスト
   * @param table テーブル情報
   * @return インデックス情報セクション文字列
   */
  public static String indexes(List<IndexEntity> indexes, TableEntity table) {
    String header =
        """
                ## インデックス情報

                | No. | インデックス名 | 種別 | UNIQUE | PRIMARY | 定義 | 備考 |
                |:---|:---|:---|:---|:---|:---|:---|
                """;
    return tableSection(
        indexes, table, header, (no, e) -> e.indexInfo(), IndexEntity::getSchemaTableName);
  }

  /**
   * 制約情報セクション
   *
   * @param constraints 制約情報のリスト
   * @param table テーブル情報
   * @return 制約情報セクション文字列
   */
  public static String constraints(List<ConstraintEntity> constraints, TableEntity table) {
    String header =
        """
                ## 制約情報

                | No. | 制約名 | 種類 | 制約定義 | 備考 |
                |:---|:---|:---|:---|:---|
                """;
    return tableSection(
        constraints,
        table,
        header,
        (no, e) -> e.constraintInfo(),
        ConstraintEntity::getSchemaTableName);
  }

  /**
   * 外部キー情報セクション
   *
   * @param foreignkeys 外部キー情報のリスト
   * @param table テーブル情報
   * @return 外部キー情報セクション文字列
   */
  public static String foreignKeys(List<ForeignKeyEntity> foreignkeys, TableEntity table) {
    String header =
        """
                ## 外部キー情報

                | No. | 外部キー名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
                |:---|:---|:---|:---|:---|:---|
                """;
    // 行番号・多重度はここで組み立てる。多重度のラベル表記をCardinalityに集約するため
    return tableSection(
        foreignkeys,
        table,
        header,
        (no, fk) ->
            "|"
                + no
                + "|"
                + fk.foreignkeyName()
                + "|"
                + fk.columnNames()
                + "|"
                + fk.getReferenceSchemaTableName()
                + "|"
                + fk.referenceColumnNames()
                + "|"
                + fk.cardinality().getLabel()
                + "|",
        ForeignKeyEntity::getSchemaTableName);
  }

  /**
   * 論理リレーション情報セクション<br>
   * DBに外部キー制約が存在せず、サイドカーYAMLで宣言された関連のみを掲載する。 読み手が「DBに制約がある」と誤読しないよう外部キー情報とは別セクションとし、注意書きを添える。
   * 対象が1件も存在しない場合はセクションごと出力しない（制約を張っているDBでは常に不要なため）
   *
   * @param logicalRelations 論理リレーションのリスト
   * @param table テーブル情報
   * @return 論理リレーション情報セクション文字列。対象が存在しない場合は空文字
   */
  public static String logicalRelations(
      List<ForeignKeyEntity> logicalRelations, TableEntity table) {
    final List<ForeignKeyEntity> targets =
        logicalRelations.stream()
            .filter(relation -> relation.getSchemaTableName().equals(table.getSchemaTableName()))
            .toList();
    if (targets.isEmpty()) {
      return "";
    }
    final StringBuilder sb =
        new StringBuilder("## 論理リレーション情報")
            .append(LINE_SEPARATOR_DOUBLE)
            .append("※DBに外部キー制約は存在せず、サイドカーYAMLで宣言された関連です。")
            .append(LINE_SEPARATOR_DOUBLE)
            .append(
                """
                        | No. | 関連名 | カラムリスト | 参照先 | 参照先カラムリスト | 多重度 |
                        |:---|:---|:---|:---|:---|:---|
                        """);
    // 物理外部キーの行番号はSQLのrow_number()が振るが、論理リレーションは当セクション内で1から振り直す
    for (int i = 0; i < targets.size(); i++) {
      final ForeignKeyEntity relation = targets.get(i);
      sb.append('|')
          .append(i + 1)
          .append('|')
          .append(relation.foreignkeyName())
          .append('|')
          .append(relation.columnNames())
          .append('|')
          .append(relation.getReferenceSchemaTableName())
          .append('|')
          .append(relation.referenceColumnNames())
          .append('|')
          .append(relation.cardinality().getLabel())
          .append('|')
          .append(LINE_SEPARATOR);
    }
    return sb.append(LINE_SEPARATOR).toString();
  }

  /**
   * トリガー情報セクション
   *
   * @param triggers トリガー情報のリスト
   * @param table テーブル情報
   * @return トリガー情報セクション文字列
   */
  public static String triggers(List<TriggerEntity> triggers, TableEntity table) {
    String header =
        """
                ## トリガー情報

                | No. | トリガー名 | タイミング | イベント | 単位 | 定義 |
                |:---|:---|:---|:---|:---|:---|
                """;
    return tableSection(
        triggers, table, header, (no, e) -> e.triggerInfo(), TriggerEntity::getSchemaTableName);
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
            sb.append("    ")
                .append(MermaidSupport.mermaidId(fk.referenceSchemaName(), fk.referenceTableName()))
                .append(' ')
                .append(fk.cardinality().getNotation(fk.relationType()))
                .append(' ')
                .append(selfId)
                .append(" : \"")
                .append(fk.foreignkeyName())
                .append('"')
                .append(LINE_SEPARATOR));
    incomingFks.forEach(
        fk ->
            sb.append("    ")
                .append(selfId)
                .append(' ')
                .append(fk.cardinality().getNotation(fk.relationType()))
                .append(' ')
                .append(MermaidSupport.mermaidId(fk.schemaName(), fk.tableName()))
                .append(" : \"")
                .append(fk.foreignkeyName())
                .append('"')
                .append(LINE_SEPARATOR));
    sb.append("    ").append(selfId).append(" {").append(LINE_SEPARATOR);
    columns.forEach(
        c ->
            sb.append("        ")
                .append(MermaidSupport.sanitizeType(c.columnType()))
                .append(' ')
                .append(MermaidSupport.sanitizeIdentifier(c.physicalColumnName()))
                .append(c.isPrimaryKey() ? " PK" : "")
                .append(LINE_SEPARATOR));
    sb.append("    }").append(LINE_SEPARATOR).append("```").append(LINE_SEPARATOR_DOUBLE);
    return sb.toString();
  }

  /**
   * フッター
   *
   * @param baseInfo データベース基本情報
   * @return フッター文字列
   */
  public static String footer(BaseInfoEntity baseInfo) {
    return PagedSectionTemplates.pageFooter(
        null, null, String.format("../../../tableList_%s.md", baseInfo.dbName()), "テーブル一覧へ");
  }

  /**
   * テーブルごとのセクションを生成する共通メソッド<br>
   * 行番号は当該テーブルに絞り込んだ後のリスト内での位置（1始まり）から採番する
   *
   * @param <T> エンティティの型
   * @param list エンティティのリスト
   * @param table テーブル情報
   * @param header セクションのヘッダー文字列
   * @param lineMapper 行番号とエンティティから1行分の文字列を生成する関数
   * @param schemaTableNameGetter エンティティからスキーマ名とテーブル名を結合した文字列を取得する関数
   * @return テーブルごとのセクション文字列
   */
  private static <T> String tableSection(
      List<T> list,
      TableEntity table,
      String header,
      BiFunction<Integer, T, String> lineMapper,
      Function<T, String> schemaTableNameGetter) {
    final List<T> filtered =
        list.stream()
            .filter(e -> schemaTableNameGetter.apply(e).equals(table.getSchemaTableName()))
            .toList();
    final StringBuilder sb = new StringBuilder(header);
    for (int i = 0; i < filtered.size(); i++) {
      if (i > 0) {
        sb.append(LINE_SEPARATOR);
      }
      sb.append(lineMapper.apply(i + 1, filtered.get(i)));
    }
    return sb.append(LINE_SEPARATOR_DOUBLE).toString();
  }
}
